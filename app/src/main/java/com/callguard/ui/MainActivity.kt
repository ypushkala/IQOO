package com.callguard.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.callguard.CallGuardService
import com.callguard.CallGuardState
import com.callguard.UiState
import com.callguard.alert.Alerter
import com.callguard.alert.AppPrefs
import com.callguard.caller.FeedbackStore
import com.callguard.core.FeedbackEntry
import com.callguard.core.Verdict
import com.callguard.core.FamilyAlert
import com.callguard.core.HealthCheck
import com.callguard.core.HealthText
import com.callguard.core.HistoryEntry
import com.callguard.core.Lang
import com.callguard.core.LanguageResolver
import com.callguard.core.RiskLevel
import com.callguard.core.Tactic
import com.callguard.caller.Blocklist
import com.callguard.core.BlockPolicy
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/**
 * Home. The hierarchy, top to bottom, is deliberate: protection status, then (only while a call is live) the
 * current call, then (only after one finishes) the summary, then quiet quick actions. Nothing here is a giant
 * colour block — risk and protection state are a small dot and a word (see Rows.statusIndicator), never a
 * full-width banner. Developer-only diagnostics are collapsed behind one row and never show in a release build.
 */
class MainActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var lang: Lang // the screen language this instance was built with
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var techVisible = false
    private val durationHandler = Handler(Looper.getMainLooper())

    private lateinit var root: LinearLayout
    private lateinit var healthCard: LinearLayout
    private lateinit var healthStatusSlot: LinearLayout
    private lateinit var healthButton: Button
    private lateinit var unprotectedBanner: LinearLayout
    private lateinit var unprotectedText: TextView

    // Current-call card: built once, visible only while a call is actually being monitored.
    private lateinit var callCard: LinearLayout
    private lateinit var callerLine: TextView
    private lateinit var durationLine: TextView
    private lateinit var riskRow: LinearLayout
    private lateinit var alert: TextView
    private lateinit var coachLine: TextView

    private lateinit var recentActivity: TextView
    private lateinit var summaryCard: LinearLayout
    private lateinit var summaryRisk: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var urgentButton: Button
    private lateinit var feedbackBox: LinearLayout
    private lateinit var feedbackNote: TextView
    private lateinit var blockButton: Button
    private var feedbackGivenFor: Any? = null // the summary object already answered
    private lateinit var toggleLangButton: Button
    private var pendingAction: String? = null

    // Debug-only diagnostics (never present in a release build).
    private var callState = ""; private var captureState = ""; private var gemmaLine = ""; private var indicLine = ""; private var powerLine = ""; private var callerRaw = ""
    private var refreshDiagnostics: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        lang = prefs.screenLanguage
        techVisible = false
        tts = TextToSpeech(this) { ttsReady = it == TextToSpeech.SUCCESS }
        val debugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(theme.dp(20), theme.dp(20), theme.dp(20), theme.dp(20)) }.also { theme.avoidStatusBar(it) }
        val page = ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) }

        // ---- brand row: quiet, not the loudest thing on the screen ----
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            addView(theme.tintedIcon(com.callguard.R.drawable.ic_shield_check, theme.fg, 20).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = theme.dp(8) })
            addView(theme.text(18f, bold = true).apply {
                text = "CallGuard"; isFocusable = true
                // Hidden developer entry: the capture probe exists only in debug builds, so this does nothing in a release.
                setOnLongClickListener { runCatching { startActivity(Intent().setClassName(packageName, "com.callguard.debug.ProbeActivity")) }.isSuccess }
            })
        })

        // ---- protection status: the first question a person has. The status row itself is rebuilt each render
        // (its colour and words depend on state), so this card holds an empty slot for it plus the fix-it button. ----
        healthCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(theme.dp(18), theme.dp(16), theme.dp(18), theme.dp(16)); background = theme.cardDrawable() }
        healthStatusSlot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also(healthCard::addView)
        healthButton = theme.primary(HealthText.setupButton(lang), com.callguard.R.drawable.ic_shield_check) { onHealthAction() }.also(healthCard::addView)
        root.addView(healthCard, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = theme.dp(16) })

        // ---- unprotected-call nudge: quietly counted by the caller-ID service, shown here since it always runs ----
        unprotectedBanner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(theme.dp(16), theme.dp(14), theme.dp(16), theme.dp(14)); background = theme.cardDrawable() }
        unprotectedText = theme.text(13.5f, muted = true).also(unprotectedBanner::addView)
        unprotectedBanner.addView(theme.button(UiStrings.get(Ui.UNPROTECTED_DISMISS, lang)) { prefs.unprotectedCallLog = com.callguard.core.UnprotectedCallLog.clear(); renderUnprotectedNudge() })
        root.addView(unprotectedBanner, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = theme.dp(12) })

        // ---- current call: the visual centrepiece while one is live; otherwise not in the layout at all ----
        callCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(theme.dp(18), theme.dp(16), theme.dp(18), theme.dp(16)); background = theme.cardDrawable() }
        callCard.addView(theme.text(12.5f, bold = true, muted = true).apply { text = UiStrings.get(Ui.HOME_CURRENT_CALL, lang) })
        callerLine = theme.text(15f).also(callCard::addView)
        durationLine = theme.text(13f, muted = true).also(callCard::addView)
        riskRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, theme.dp(10), 0, 0) }.also(callCard::addView)
        alert = theme.text(14f).apply { setPadding(0, theme.dp(6), 0, 0) }.also(callCard::addView)
        coachLine = theme.text(13.5f, muted = true).also(callCard::addView)
        root.addView(callCard, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = theme.dp(16) })

        // ---- calm state: what protection has been doing lately, instead of raw debug text ----
        recentActivity = theme.text(13.5f, muted = true).apply { visibility = View.GONE; setPadding(theme.dp(4), theme.dp(4), theme.dp(4), 0) }
        root.addView(recentActivity)

        // ---- last call (post-call summary): the one urgent action first, everything else under "More" ----
        summaryCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(theme.dp(18), theme.dp(18), theme.dp(18), theme.dp(18)); background = theme.cardDrawable() }
        summaryCard.addView(theme.text(13f, bold = true, muted = true).apply { text = UiStrings.get(Ui.HOME_LAST_CALL, lang) })
        summaryRisk = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, theme.dp(4), 0, theme.dp(8)) }.also(summaryCard::addView)
        summaryText = theme.text(14.5f).also(summaryCard::addView)
        val moreBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        urgentButton = theme.primary(UiStrings.get(Ui.RECOVERY_BUTTON, lang), com.callguard.R.drawable.ic_alert_triangle) { startActivity(Intent(this, FollowUpActivity::class.java)) }.also(summaryCard::addView)
        feedbackBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, theme.dp(8), 0, 0) }
        feedbackBox.addView(theme.text(14.5f, bold = true).apply { text = UiStrings.get(Ui.FEEDBACK_Q, lang) })
        feedbackBox.addView(theme.button(UiStrings.get(Ui.FEEDBACK_YES, lang)) { answer(Verdict.SCAM) })
        feedbackBox.addView(theme.button(UiStrings.get(Ui.FEEDBACK_NO, lang)) { answer(Verdict.FINE) })
        feedbackBox.addView(theme.button(UiStrings.get(Ui.FEEDBACK_UNSURE, lang)) { answer(Verdict.UNSURE) })
        summaryCard.addView(feedbackBox)
        feedbackNote = theme.text(13f, muted = true).also(summaryCard::addView)
        toggleLangButton = theme.button("") { toggleSummaryLanguage() }.also(moreBox::addView)
        moreBox.addView(theme.button(UiStrings.get(Ui.READ_ALOUD, lang)) { readAloud() })
        moreBox.addView(theme.button(UiStrings.get(Ui.FAMILY_ALERT, lang)) { alertFamily() })
        moreBox.addView(theme.button(UiStrings.get(Ui.SHARE, lang)) { shareReport() })
        blockButton = theme.button("", com.callguard.R.drawable.ic_block) { toggleBlock() }.also(moreBox::addView)
        moreBox.addView(theme.button(UiStrings.get(Ui.HELPLINE, lang)) { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))) })
        moreBox.addView(theme.button(UiStrings.get(Ui.DISMISS, lang)) { CallGuardState.update { it.copy(summary = null) } })
        lateinit var moreButton: Button
        moreButton = theme.button(UiStrings.get(Ui.HOME_MORE, lang)) {
            moreBox.visibility = if (moreBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            moreButton.text = UiStrings.get(if (moreBox.visibility == View.VISIBLE) Ui.HOME_LESS else Ui.HOME_MORE, lang)
        }.also(summaryCard::addView)
        summaryCard.addView(moreBox)
        root.addView(summaryCard, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = theme.dp(16) })

        // ---- quiet quick actions: secondary to everything above, never competing with the primary button ----
        root.addView(theme.button(UiStrings.get(Ui.GR_HELP_OTHERS, lang), com.callguard.R.drawable.ic_family) { startActivity(Intent(this, HelperActivity::class.java)) }
            .also { it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).also { lp -> lp.topMargin = theme.dp(20) } })
        root.addView(theme.button(UiStrings.get(Ui.HOME_PRACTICE, lang), com.callguard.R.drawable.ic_play) { startActivity(Intent(this, PracticeActivity::class.java)) })

        // ---- diagnostics: collapsed, debug builds only, never shown to a real user ----
        if (debugBuild) {
            root.addView(theme.button(UiStrings.get(Ui.TEST, lang)) { requestThen(CallGuardService.ACTION_TEST) }
                .also { it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).also { lp -> lp.topMargin = theme.dp(20) } })
            root.addView(theme.button(UiStrings.get(Ui.STOP, lang)) { startService(Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_STOP)) })
            val panelHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun rebuildPanel() {
                panelHolder.removeAllViews()
                // Gemma/Indic/Power already read as full sentences ("Gemma: warming up…", "Power: normal"), so
                // pairing them with a second, separate row label would just repeat the same word — leave those
                // unlabelled. Monitoring/Audio/Caller are plain values, so they keep a label.
                panelHolder.addView(Rows.diagnosticsPanel(this, theme, UiStrings.get(Ui.DIAGNOSTICS, lang), listOf(
                    UiStrings.get(Ui.DIAG_MONITORING, lang) to callState,
                    UiStrings.get(Ui.DIAG_AUDIO, lang) to captureState,
                    "" to gemmaLine,
                    "" to indicLine,
                    "" to powerLine,
                    UiStrings.get(Ui.DIAG_CALLER, lang) to callerRaw.ifEmpty { "—" },
                )))
            }
            refreshDiagnostics = ::rebuildPanel
            rebuildPanel()
            root.addView(panelHolder)
        }

        // A fixed bottom row (Home/History/Settings) sits outside the scrolling content, so it is always reachable.
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        container.addView(NavBar.build(this, theme, lang, NavTab.HOME))
        setContentView(container)
        if (!prefs.setupOffered && !HealthCheck.evaluate(HealthProbe.inputs(this)).protectionOn) {
            prefs.setupOffered = true // "Get ready" opens once by itself; the status card keeps it one tap away
            startActivity(Intent(this, SetupActivity::class.java))
        }
        suggestAccessibilityIfLargeText()
    }

    override fun onStart() {
        super.onStart()
        if (prefs.screenLanguage != lang) { recreate(); return } // changed on the Languages page
        CallGuardState.listener = ::render
        render(CallGuardState.state)
    }

    override fun onResume() {
        super.onResume()
        renderUnprotectedNudge() // written by the always-running caller-ID service, so re-read fresh each time this screen is seen
    }

    /** Suggested once, never forced: if the phone's own text is already large, large text probably helps here too. */
    private fun suggestAccessibilityIfLargeText() {
        if (theme.accessible || prefs.accessibilitySuggested) return
        if (resources.configuration.fontScale < 1.25f) return
        prefs.accessibilitySuggested = true
        android.app.AlertDialog.Builder(this).setMessage(UiStrings.get(Ui.ACC_SUGGEST_FMT, lang))
            .setPositiveButton(UiStrings.get(Ui.ACC_SUGGEST_YES, lang)) { _, _ -> prefs.accessibility = true; recreate() }
            .setNegativeButton(UiStrings.get(Ui.ACC_SUGGEST_NO, lang), null).show()
    }

    override fun onStop() {
        CallGuardState.listener = null
        durationHandler.removeCallbacksAndMessages(null)
        durationTicking = false // so returning mid-call (onStart -> render) restarts the tick loop rather than staying frozen
        tts?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        tts?.shutdown(); tts = null
        super.onDestroy()
    }

    private fun renderUnprotectedNudge() {
        val n = com.callguard.core.UnprotectedCallLog.count(prefs.unprotectedCallLog)
        unprotectedBanner.visibility = if (n > 0 && !CallGuardState.state.monitoring) View.VISIBLE else View.GONE
        if (n > 0) unprotectedText.text = UiStrings.fmt(Ui.UNPROTECTED_NUDGE_FMT, lang, n)
    }

    /** The language the summary is shown in: its own saved setting (default: same as the screen). */
    private fun summaryLangNow(st: UiState) = LanguageResolver.summary(prefs.choices, st.callLang)

    private fun toggleSummaryLanguage() {
        val next = LanguageResolver.next(summaryLangNow(CallGuardState.state))
        prefs.summaryLanguage = LanguageResolver.summaryChoiceFor(next) // remembered for next time
        render(CallGuardState.state)
    }

    private fun answer(v: Verdict) {
        val sum = CallGuardState.state.summary ?: return
        FeedbackStore(this).add(FeedbackEntry.from(sum, v, System.currentTimeMillis()))
        feedbackGivenFor = sum
        render(CallGuardState.state)
    }

    private fun onHealthAction() {
        val r = HealthCheck.evaluate(HealthProbe.inputs(this))
        if (r.blocking == listOf(com.callguard.core.HealthItem.PROTECTION_RUNNING)) requestThen(CallGuardService.ACTION_START) // only the switch is missing: one tap
        else startActivity(Intent(this, SetupActivity::class.java))
    }

    private fun renderHealth() {
        val r = HealthCheck.evaluate(HealthProbe.inputs(this))
        healthStatusSlot.removeAllViews()
        healthStatusSlot.addView(Rows.statusIndicator(this, theme, if (r.protectionOn) theme.safe else theme.caution, HealthText.cardTitle(r, lang), HealthText.cardBody(r, lang)))
        healthCard.contentDescription = "${HealthText.cardTitle(r, lang)}. ${HealthText.cardBody(r, lang)}"
        healthButton.visibility = if (r.protectionOn) View.GONE else View.VISIBLE
        healthButton.text = if (r.blocking == listOf(com.callguard.core.HealthItem.PROTECTION_RUNNING)) HealthText.fix(com.callguard.core.HealthItem.PROTECTION_RUNNING, lang) else HealthText.setupButton(lang)
    }

    private fun isLiveCall(s: UiState) = s.callState == "In call"

    /** One self-scheduling tick loop per call, started the moment a call goes live — not once per `render()`,
     *  which fires far more often (every ASR segment) and would otherwise stack up duplicate ticking loops. */
    private var durationTicking = false

    private fun renderDuration() {
        val started = CallGuardState.state.callStartedAtMs
        if (started == 0L || !isLiveCall(CallGuardState.state)) { durationLine.visibility = View.GONE; durationTicking = false; return }
        val secs = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
        durationLine.visibility = View.VISIBLE
        durationLine.text = UiStrings.fmt(Ui.HOME_DURATION_FMT, lang, "%d:%02d".format(secs / 60, secs % 60))
        durationHandler.postDelayed(::renderDuration, 1000)
    }

    private fun render(s: UiState) {
        renderHealth()
        renderUnprotectedNudge()
        callState = s.callState
        captureState = s.captureState
        gemmaLine = s.gemma
        indicLine = s.indic
        powerLine = s.power
        callerRaw = s.caller
        refreshDiagnostics?.invoke()

        val live = isLiveCall(s)
        callCard.visibility = if (live) View.VISIBLE else View.GONE
        recentActivity.visibility = if (!live && s.summary == null) View.VISIBLE else View.GONE
        if (!live && s.summary == null) {
            val calls = HistoryEntry.parseAll(prefs.callHistory)
            val highRisk = calls.count { it.level == RiskLevel.HIGH }
            recentActivity.text = UiStrings.fmt(Ui.HOME_RECENT_FMT, lang, calls.size, highRisk)
        }
        if (live) {
            callerLine.text = if (s.caller.isEmpty()) UiStrings.get(Ui.HOME_UNKNOWN_CALLER, lang) else s.caller
            if (!durationTicking) { durationTicking = true; renderDuration() }
            val lvl = s.detection.level
            riskRow.removeAllViews()
            riskRow.addView(Rows.statusIndicator(this, theme, theme.statusColor(lvl), UiStrings.risk(lvl, lang)))
            val topTactic = s.alertTactics.firstOrNull()
            val reason = com.callguard.core.AlertReason.lineForTactic(topTactic, lang)
            val hasAlert = lvl >= RiskLevel.MEDIUM || s.lastAlert.isNotEmpty()
            alert.visibility = if (hasAlert && reason != null) View.VISIBLE else View.GONE
            alert.text = reason ?: ""
            alert.setTextColor(theme.statusColor(lvl))
            coachLine.text = com.callguard.core.CoachingLine.forTactics(s.alertTactics, lang)?.let { "${UiStrings.get(Ui.COACHING_LABEL, lang)}: $it" } ?: ""
            coachLine.visibility = if (coachLine.text.isEmpty()) View.GONE else View.VISIBLE
        }

        val sum = s.summary
        summaryCard.visibility = if (sum == null) View.GONE else View.VISIBLE
        if (sum != null) {
            summaryRisk.removeAllViews()
            summaryRisk.addView(Rows.statusIndicator(this, theme, theme.statusColor(sum.level), UiStrings.risk(sum.level, lang)))
            val answered = feedbackGivenFor === sum
            feedbackBox.visibility = if (answered) View.GONE else View.VISIBLE
            feedbackNote.text = if (!answered) "" else UiStrings.get(Ui.FEEDBACK_SAVED, lang) +
                (if (FeedbackStore(this).all().lastOrNull()?.verdict == Verdict.FINE && sum.numberHash != null) "\n" + UiStrings.get(Ui.FEEDBACK_SAFE_NOTE, lang) else "")
            urgentButton.visibility = if (sum.level >= RiskLevel.MEDIUM) View.VISIBLE else View.GONE
            val h = sum.numberHash
            blockButton.visibility = if (BlockPolicy.canOfferBlock(h, null)) View.VISIBLE else View.GONE
            blockButton.text = UiStrings.get(if (h != null && Blocklist(this).contains(h)) Ui.UNBLOCK_NUMBER else Ui.BLOCK_NUMBER, lang)
            val l = summaryLangNow(s)
            summaryText.text = sum.plainText(lang = l)
            val next = LanguageResolver.next(l)
            toggleLangButton.text = when (next) { Lang.EN -> "View in English"; Lang.HI -> "हिन्दी में देखें"; Lang.TE -> "తెలుగులో చూడండి" }
            toggleLangButton.contentDescription = toggleLangButton.text
        }
    }

    private fun toggleBlock() {
        val h = CallGuardState.state.summary?.numberHash ?: return
        val list = Blocklist(this)
        if (list.contains(h)) list.remove(h) else { list.add(h); android.widget.Toast.makeText(this, UiStrings.get(Ui.BLOCK_NOTE, lang), android.widget.Toast.LENGTH_LONG).show() }
        render(CallGuardState.state)
    }

    private fun readAloud() {
        val st = CallGuardState.state
        val l = summaryLangNow(st)
        val text = st.summary?.plainText(lang = l) ?: return
        if (!ttsReady) return
        val locale = Alerter.localeOf(l)
        if (tts?.isLanguageAvailable(locale)?.let { it < TextToSpeech.LANG_AVAILABLE } == true) {
            android.widget.Toast.makeText(this, UiStrings.fmt(Ui.NO_VOICE_FMT, lang, UiStrings.name(l)), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        tts?.language = locale
        tts?.setSpeechRate(if (theme.accessible) 0.85f else 0.95f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "summary")
    }

    /** Hands the report to Android's share sheet: the user picks where it goes; CallGuard sends nothing itself. Reports stay English for authorities. */
    private fun shareReport() {
        val report = CallGuardState.state.summary?.reportText() ?: return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), UiStrings.get(Ui.SHARE, lang)))
    }

    /**
     * Opens the messaging app with a ready-to-send alert for the chosen family member (risk, tactics, caller number:
     * never audio or transcript), in the "message to family" language. The user taps Send, so CallGuard needs no SMS
     * permission and never sends on its own.
     */
    private fun alertFamily() {
        val contacts = prefs.familyContacts
        if (contacts.isEmpty()) { startActivity(Intent(this, FamilyAlertActivity::class.java)); return }
        if (contacts.size > 1) { // one composer per person: ask who first
            android.app.AlertDialog.Builder(this).setTitle(UiStrings.get(Ui.FAMILY_ALERT, lang)).setItems(contacts.map { it.label }.toTypedArray()) { _, i -> composeFamily(contacts[i].number) }.show()
            return
        }
        composeFamily(contacts[0].number)
    }

    private fun composeFamily(number: String) {
        val st = CallGuardState.state
        val level = st.summary?.level ?: st.detection.level
        val tactics: List<Tactic> = st.summary?.findings?.map { it.tactic }
            ?: st.detection.signals.filter { it.level >= RiskLevel.MEDIUM }.map { it.tactic }.distinct()
        val callerLine = st.summary?.callerSummary ?: st.caller.takeIf { it.isNotEmpty() }
        val body = FamilyAlert.message(level, tactics, callerLine, prefs.familyMessageLanguage)
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", body))
    }

    private fun requestThen(action: String) {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
        if (android.os.Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) launch(action) else {
            pendingAction = action
            requestPermissions(missing.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val a = pendingAction ?: return
        pendingAction = null
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launch(a)
    }

    private fun launch(action: String) {
        startForegroundService(Intent(this, CallGuardService::class.java).setAction(action))
    }
}
