package com.callguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.callguard.alert.Alerter
import com.callguard.alert.AppPrefs
import com.callguard.asr.AsrPipeline
import com.callguard.asr.AsrSegment
import com.callguard.audio.AudioCapture
import com.callguard.audio.PcmRingBuffer
import com.callguard.caller.CallerRegistry
import com.callguard.core.AlertDebouncer
import com.callguard.core.CallSummary
import com.callguard.core.CallLanguageTracker
import com.callguard.core.CallTimeline
import com.callguard.core.ListenPolicy
import com.callguard.core.Lang
import com.callguard.core.LanguageResolver
import com.callguard.core.PowerSnapshot
import com.callguard.core.Strings
import com.callguard.core.Throttle
import com.callguard.core.NumberSignalSource
import com.callguard.core.GemmaPrompt
import com.callguard.core.GemmaSignalSource
import com.callguard.gemma.GemmaClassifier
import com.callguard.core.RiskEngine
import com.callguard.core.RiskLevel
import com.callguard.core.SelfSpeechFilter
import com.callguard.core.RollingTranscript
import com.callguard.debug.DevReplay
import com.callguard.power.PowerMonitor

/**
 * Owns the whole on-device pipeline. Capture starts when a call goes OFFHOOK (speakerphone is
 * requested), or immediately in test mode (ACTION_TEST) to exercise the mic path without a call.
 */
class CallGuardService : Service() {
    private lateinit var audioManager: AudioManager
    private lateinit var telephony: TelephonyManager
    private var capture: AudioCapture? = null
    private var asr: AsrPipeline? = null
    private var alerter: Alerter? = null
    private val selfSpeech = SelfSpeechFilter()
    private val ring = PcmRingBuffer.ofSeconds(30)
    private val transcript = RollingTranscript()
    private val gemmaSource = GemmaSignalSource()
    private val engine = RiskEngine(models = listOf(gemmaSource), number = NumberSignalSource { CallerRegistry.get() })
    private var gemma: GemmaClassifier? = null
    private val debouncer = AlertDebouncer()
    private val timeline = CallTimeline()
    private val prefs by lazy { AppPrefs(this) }
    private val langTracker = CallLanguageTracker()
    private var power: PowerMonitor? = null
    private var throttle: Throttle? = null
    private var callback: Any? = null
    private var legacyListener: PhoneStateListener? = null
    private var speakerWasOn = false
    /** True when the current capture was started by a real call (only then may IDLE stop it). */
    private var captureFromCall = false
    private var beeper: com.callguard.alert.DisclosureBeeper? = null
    private var lastBeepMs = 0L
    private val beepTick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (com.callguard.core.DisclosurePolicy.due(prefs.disclosureBeep, captureFromCall && capture != null, alerter?.speaking == true, lastBeepMs, now)) {
                (beeper ?: com.callguard.alert.DisclosureBeeper().also { beeper = it }).beep(); lastBeepMs = now
            }
            if (captureFromCall) mainHandler.postDelayed(this, 2_000)
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    /** Model loading runs on a worker thread; these are only touched on the main thread. */
    private var loading = false
    private var loadGeneration = 0
    private var pendingTest = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_TEST -> {
                ensureStarted()
                if (asr != null) startCapture("test mode (no call)") else pendingTest = loading
            }
            else -> ensureStarted()
        }
        return START_STICKY
    }

    private fun ensureStarted() {
        if (asr != null || loading) return
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        startForegroundCompat()
        alerter = Alerter(this, selfSpeech)
        power = PowerMonitor(this) { snap, t -> applyPower(snap, t) }.also { it.start() }
        loading = true
        val generation = ++loadGeneration
        CallGuardState.update {
            it.copy(monitoring = true, callState = "Loading models…", captureState = "Stopped", transcript = "",
                detection = com.callguard.core.DetectionResult.NONE, lastAlert = "")
        }
        // Whisper + VAD take several seconds to load; keep the main thread free.
        Thread({
            val t0 = System.nanoTime()
            var pipeline: AsrPipeline? = null
            var error: Throwable? = null
            try {
                pipeline = AsrPipeline(this, ::onAsrSegment, selfSpeech = selfSpeech,
                    onIndicStatus = { st -> CallGuardState.update { it.copy(indic = st) } })
            } catch (t: Throwable) {
                error = t
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            mainHandler.post { onModelsLoaded(generation, pipeline, error, ms) }
        }, "callguard-load").start()
    }

    private fun onModelsLoaded(generation: Int, pipeline: AsrPipeline?, error: Throwable?, loadMs: Long) {
        loading = false
        if (generation != loadGeneration) { // stopped while loading
            pipeline?.release()
            return
        }
        if (pipeline == null) {
            Log.e(TAG, "ASR init failed", error)
            shutdown()
            CallGuardState.update { it.copy(captureState = "ASR init failed: ${error?.message}") }
            return
        }
        Log.i(TAG, "models loaded in ${loadMs}ms (off main thread)")
        pipeline.start()
        asr = pipeline
        // Gemma loads after Whisper (one heavy load at a time) on its own thread; rules work meanwhile.
        gemma = GemmaClassifier(
            this, gemmaSource,
            onStatus = { st -> CallGuardState.update { it.copy(gemma = st) } },
            onVerdict = { refresh() },
        ).also { g ->
            g.start()
            throttle?.let { g.setThrottle(it.gemmaIntervalMs, it.gemmaPaused) }
        }
        throttle?.let { pipeline.skipTranslation = it.skipTranslation }
        CallGuardState.update { it.copy(callState = "Monitoring — waiting for call") }
        registerCallState()
        if (pendingTest) {
            pendingTest = false
            if (DevReplay.available(this)) startReplay() else startCapture("test mode (no call)")
        }
    }

    /**
     * The always-on foreground notification, kept honest about what is actually happening right now: "watching" while
     * idle between calls, "protecting" while a call is being listened to. Safe wording on the lock screen too, so a
     * glance answers "am I protected" without opening the app — the same ambient signal a Wi-Fi icon gives.
     */
    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "CallGuard", NotificationManager.IMPORTANCE_LOW))
        val n = ongoingNotification(listening = false)
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun ongoingNotification(listening: Boolean): Notification {
        val l = prefs.screenLanguage
        val (titleKey, bodyKey) = if (listening) com.callguard.core.Ui.NOTIF_LISTENING_TITLE to com.callguard.core.Ui.NOTIF_LISTENING_BODY
            else com.callguard.core.Ui.NOTIF_WATCHING_TITLE to com.callguard.core.Ui.NOTIF_WATCHING_BODY
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(com.callguard.core.UiStrings.get(titleKey, l)).setContentText(com.callguard.core.UiStrings.get(bodyKey, l))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setVisibility(Notification.VISIBILITY_PUBLIC) // wording itself is safe to show; only the call summary stays private
            .build()
    }

    private fun updateOngoingNotification(listening: Boolean) {
        getSystemService(NotificationManager::class.java).notify(1, ongoingNotification(listening))
    }

    private fun registerCallState() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            CallGuardState.update { it.copy(callState = "READ_PHONE_STATE not granted") }
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleCallState(state)
            }
            callback = cb
            telephony.registerTelephonyCallback(mainExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleCallState(state)
            }
            legacyListener = l
            @Suppress("DEPRECATION") telephony.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallState(state: Int) {
        val name = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "Ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "In call"
            else -> "Monitoring — waiting for call"
        }
        Log.i(TAG, "call state -> $name")
        CallGuardState.update { it.copy(callState = name) }
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val d = ListenPolicy.decide(prefs.analyseScope, CallerRegistry.get())
                Log.i(TAG, "listen decision: listen=${d.listen} (${d.reason})")
                if (d.listen) { startCapture("call"); captureFromCall = capture != null; lastBeepMs = 0L; if (captureFromCall) { mainHandler.post(beepTick); updateOngoingNotification(listening = true); CallGuardState.update { it.copy(callStartedAtMs = System.currentTimeMillis()) } }; refresh() }
                else CallGuardState.update { it.copy(captureState = "Not listening: ${d.reason} (setting: unknown numbers only)") }
            }
            // Android also reports IDLE right on registration; don't kill a manual test capture.
            TelephonyManager.CALL_STATE_IDLE -> {
                if (captureFromCall) { captureFromCall = false; mainHandler.removeCallbacks(beepTick); stopCapture(); updateOngoingNotification(listening = false) } // builds the summary while the caller is still known
                CallerRegistry.clear(); CallGuardState.update { it.copy(caller = "", callStartedAtMs = 0L) }
            }
        }
    }

    private fun startCapture(reason: String) {
        if (capture != null) return
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            CallGuardState.update { it.copy(captureState = "RECORD_AUDIO not granted") }
            return
        }
        speakerWasOn = @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn
        @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = true
        Log.i(TAG, "speakerphone requested; isSpeakerphoneOn=${@Suppress("DEPRECATION") audioManager.isSpeakerphoneOn} audioMode=${audioManager.mode}")
        familyAlertSent = false; transcript.clear(); debouncer.reset(); gemmaSource.clear(); langTracker.reset(); timeline.start(System.currentTimeMillis())
        getSystemService(NotificationManager::class.java).cancel(LIVE_ALERT_NOTIFICATION_ID)
        CallGuardState.update { it.copy(transcript = "", detection = com.callguard.core.DetectionResult.NONE, lastAlert = "", alertTactics = emptyList(), summary = null, callLang = Lang.EN) }
        val cap = AudioCapture { pcm, n -> ring.write(pcm, n); asr?.feed(pcm, n) }
        val err = cap.start()
        if (err != null) {
            Log.e(TAG, "capture start failed: $err")
            CallGuardState.update { it.copy(captureState = "Failed: $err") }
            return
        }
        capture = cap
        CallGuardState.update { it.copy(captureState = "Capturing ($reason): MIC 16 kHz mono PCM16, speakerphone") }
    }

    /** Applies the thermal/battery policy: slow or pause Gemma and skip translation when the phone is warm or hot. Whisper and the rules never stop. */
    private fun applyPower(snap: PowerSnapshot, t: Throttle) {
        if (t != throttle) {
            throttle = t
            gemma?.setThrottle(t.gemmaIntervalMs, t.gemmaPaused)
            asr?.skipTranslation = t.skipTranslation
        }
        val bits = buildList {
            snap.batteryTempC?.let { add("%.1f °C".format(it)) }
            snap.batteryPercent?.let { add("$it%" + if (snap.charging == true) " (charging)" else "") }
        }
        CallGuardState.update { it.copy(power = (listOf(t.label) + bits).joinToString(" · ")) }
    }

    /** Builds the post-call summary (in memory only) and, for MEDIUM/HIGH calls, posts a private notification. */
    private fun finishSession(): CallSummary? {
        if (!timeline.active) return null
        val summary = timeline.finish(System.currentTimeMillis(), CallerRegistry.get()) ?: return null
        val lang = LanguageResolver.summary(prefs.choices, langTracker.callLanguage())
        CallGuardState.update { it.copy(summary = summary, summaryLang = lang, callLang = langTracker.callLanguage()) }
        Log.i(TAG, "call summary ready: level=${summary.level} findings=${summary.findings.size} duration_ms=${summary.durationMs}")
        if (summary.level >= RiskLevel.MEDIUM) notifySummary(summary, lang)
        prefs.callHistory = com.callguard.core.HistoryEntry.append(prefs.callHistory,
            com.callguard.core.HistoryEntry(summary.startedAtEpochMs, summary.durationMs, summary.level, summary.findings.map { it.tactic }, summary.callerSummary, summary.numberHash))
        return summary
    }

    private fun notifySummary(summary: CallSummary, lang: com.callguard.core.Lang) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SUMMARY_CHANNEL, "Call summaries", NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, com.callguard.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val tactics = summary.findings.joinToString(", ") { Strings.tactic(it.tactic, lang) }
            .ifEmpty { if (lang == com.callguard.core.Lang.HI) "सारांश देखें" else "see the summary" }
        val public = Notification.Builder(this, SUMMARY_CHANNEL)
            .setContentTitle(if (lang == com.callguard.core.Lang.HI) "CallGuard: कॉल का सारांश तैयार है" else "CallGuard: call summary ready").setSmallIcon(android.R.drawable.ic_dialog_alert).build()
        val n = Notification.Builder(this, SUMMARY_CHANNEL)
            .setContentTitle(Strings.headline(summary.level, lang)).setContentText(tactics)
            .setStyle(Notification.BigTextStyle().bigText(if (lang == com.callguard.core.Lang.HI) "$tactics। क्या करना है, देखने के लिए टैप करें।" else "$tactics. Tap to see what to do."))
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentIntent(open).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public) // tactics stay off the lock screen
            .build()
        nm.notify(SUMMARY_NOTIFICATION_ID, n)
    }

    /** Debug builds only, see [DevReplay]. */
    @Volatile private var replayAlerts = 0
    private fun startReplay() {
        CallGuardState.update { it.copy(captureState = "Replaying test WAVs (debug, no microphone)") }
        DevReplay.start(
            this,
            resetSession = {
                transcript.clear(); debouncer.reset(); gemmaSource.clear(); langTracker.reset(); replayAlerts = 0; timeline.start(System.currentTimeMillis())
                CallGuardState.update { it.copy(transcript = "", detection = com.callguard.core.DetectionResult.NONE, lastAlert = "") }
            },
            endSession = { finishSession()?.plainText()?.replace("\n", " / ") ?: "(no summary: nothing analysed)" },
            feed = { asr?.feed(it, it.size) },
            waitReady = { while (CallGuardState.state.gemma.let { g -> g.contains("loading") || g.contains("warming") }) Thread.sleep(500) },
            summary = {
                val st = CallGuardState.state
                "risk=${st.detection.level}|alerts=$replayAlerts|${st.gemma}|signals=${st.detection.signals.joinToString(",") { it.source + ":" + it.tactic + ":" + it.level }}|tx=${st.transcript}"
            },
        )
    }

    private fun stopCapture() {
        finishSession()
        capture?.stop()
        capture = null
        if (::audioManager.isInitialized) @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = speakerWasOn
        CallGuardState.update { it.copy(captureState = "Stopped") }
    }

    private fun onAsrSegment(seg: AsrSegment) {
        langTracker.onSegment(seg.native, seg.lang)
        transcript.commit(seg.native, seg.english)
        gemma?.onTranscript(transcript.modelText().takeLast(GemmaPrompt.MAX_WINDOW_CHARS))
        refresh()
    }

    /** Re-fuses rules + Gemma over the current transcript. Called from the ASR and Gemma threads. */
    @Synchronized
    private fun refresh() {
        val result = engine.evaluate(transcript.analysisText())
        val now = System.currentTimeMillis()
        val alerts = debouncer.onDetection(result, now)
        timeline.record(now, result, if (alerts.isNotEmpty()) 1 else 0)
        var lastAlert: String? = null
        var tactics: List<com.callguard.core.Tactic> = emptyList()
        if (alerts.isNotEmpty()) {
            replayAlerts++
            val level = if (alerts.any { it.level == RiskLevel.HIGH }) RiskLevel.HIGH else RiskLevel.MEDIUM
            alerter?.warn(level, LanguageResolver.spoken(prefs.choices, langTracker.callLanguage()))
            lastAlert = alerts.joinToString { "${it.tactic.label} (“${it.matched}”)" }
            tactics = com.callguard.core.AlertReason.topTactic(alerts)?.let { listOf(it) } ?: emptyList()
            if (alerts.any { it.level == RiskLevel.HIGH }) maybeAutoAlertFamily(result)?.let { lastAlert += "\n$it" }
            notifyLiveAlert(level, tactics)
        }
        CallGuardState.update { it.copy(transcript = transcript.text(), detection = result, lastAlert = lastAlert ?: it.lastAlert,
            alertTactics = if (tactics.isNotEmpty()) tactics else it.alertTactics, callLang = langTracker.callLanguage()) }
    }

    /**
     * A HIGH/MEDIUM alert also becomes a proper notification, not just the in-app banner, so it is visible even if the
     * screen is off or locked during a speakerphone call. The lock-screen version never names the tactic; the private
     * one (shown once unlocked, or if the phone allows private notifications on the lock screen) does.
     */
    private fun notifyLiveAlert(level: RiskLevel, tactics: List<com.callguard.core.Tactic>) {
        val l = prefs.screenLanguage
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(LIVE_CHANNEL, "Live call warnings", NotificationManager.IMPORTANCE_HIGH))
        val public = Notification.Builder(this, LIVE_CHANNEL)
            .setContentTitle(com.callguard.core.UiStrings.get(com.callguard.core.Ui.LIVE_ALERT_PUBLIC_TITLE, l)).setSmallIcon(android.R.drawable.ic_dialog_alert).build()
        val open = android.app.PendingIntent.getActivity(
            this, 1, Intent(this, com.callguard.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, LIVE_CHANNEL)
            .setContentTitle(Strings.headline(level, l)).setContentText(com.callguard.core.AlertReason.line(tactics.map { com.callguard.core.Signal("", "", it, level, "") }, l) ?: "")
            .setCategory(Notification.CATEGORY_CALL).setPriority(Notification.PRIORITY_HIGH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentIntent(open).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
            .build()
        nm.notify(LIVE_ALERT_NOTIFICATION_ID, n)
    }

    private var familyAlertSent = false

    /** Opt-in automatic family SMS (see [AutoFamilyAlert]). Returns a line for the screen when something was sent. */
    private fun maybeAutoAlertFamily(result: com.callguard.core.DetectionResult): String? {
        val contacts = prefs.familyContacts
        val decision = com.callguard.core.AutoFamilyAlert.decide(
            enabled = prefs.autoFamilyAlert, consented = prefs.autoFamilyAlert, hasContact = contacts.isNotEmpty(),
            hasPermission = checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            level = result.level, alreadySent = familyAlertSent,
        )
        if (decision != com.callguard.core.AutoAlertDecision.SEND) return null
        familyAlertSent = true
        val tactics = result.signals.filter { it.level >= RiskLevel.MEDIUM && !it.advisory }.map { it.tactic }.distinct()
        val caller = if (prefs.includeCallerInAlert) CallerRegistry.get()?.summary?.takeIf { it.isNotEmpty() } else null
        val body = com.callguard.core.FamilyAlert.message(RiskLevel.HIGH, tactics, caller, prefs.familyMessageLanguage)
        val sent = ArrayList<String>()
        for (c in contacts) {
            val ok = runCatching {
                val sms = getSystemService(android.telephony.SmsManager::class.java)
                sms.sendMultipartTextMessage(c.number, null, sms.divideMessage(body), null, null)
            }.isSuccess
            prefs.familyAlertLog = com.callguard.core.AlertLogEntry.append(prefs.familyAlertLog,
                com.callguard.core.AlertLogEntry(System.currentTimeMillis(), if (ok) com.callguard.core.AlertLogEntry.Outcome.SENT else com.callguard.core.AlertLogEntry.Outcome.FAILED, c.label))
            if (ok) sent += c.label
        }
        Log.i(TAG, "automatic family alert: sent=${sent.size} of ${contacts.size}") // never numbers or text
        return if (sent.isEmpty()) null else com.callguard.core.UiStrings.fmt(com.callguard.core.Ui.FA_SENT_LINE, prefs.screenLanguage, sent.joinToString(", "))
    }

    private fun shutdown() {
        mainHandler.removeCallbacks(beepTick); beeper?.release(); beeper = null
        power?.stop(); power = null; throttle = null
        loadGeneration++ // invalidates any in-flight model load
        loading = false
        pendingTest = false
        stopCapture()
        gemma?.stop(); gemma = null; gemmaSource.clear()
        if (Build.VERSION.SDK_INT >= 31) (callback as? TelephonyCallback)?.let { telephony.unregisterTelephonyCallback(it) }
        else @Suppress("DEPRECATION") legacyListener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
        callback = null; legacyListener = null
        asr?.release(); asr = null
        alerter?.release(); alerter = null
        CallGuardState.update { it.copy(monitoring = false, callState = "Idle (not monitoring)", captureState = "Stopped", gemma = "Gemma: off", indic = "", power = "") }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (asr != null || loading) shutdown()
        super.onDestroy()
    }

    companion object {
        const val TAG = "CallGuard.Service"
        const val ACTION_START = "com.callguard.START"
        const val ACTION_STOP = "com.callguard.STOP"
        const val ACTION_TEST = "com.callguard.TEST"
        private const val CHANNEL = "callguard"
        private const val SUMMARY_CHANNEL = "callguard_summary"
        private const val LIVE_CHANNEL = "callguard_live_alert"
        private const val LIVE_ALERT_NOTIFICATION_ID = 5 // 1=ongoing, 2=summary, 3=incoming heads-up, 4=boot reminder (CallAlerts)
        private const val SUMMARY_NOTIFICATION_ID = 2
    }
}
