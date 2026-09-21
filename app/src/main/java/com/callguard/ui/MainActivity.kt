package com.callguard.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
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
import com.callguard.core.Lang
import com.callguard.core.LanguageResolver
import com.callguard.core.RiskLevel
import com.callguard.core.Tactic
import com.callguard.caller.Blocklist
import com.callguard.core.BlockPolicy
import com.callguard.core.RecoveryPlan
import com.callguard.core.Ui
import com.callguard.core.UiStrings

class MainActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var lang: Lang // the screen language this instance was built with
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var techVisible = false

    private lateinit var callState: TextView
    private lateinit var captureState: TextView
    private lateinit var gemma: TextView
    private lateinit var indic: TextView
    private lateinit var power: TextView
    private lateinit var caller: TextView
    private lateinit var risk: TextView
    private lateinit var alert: TextView
    private lateinit var transcript: TextView
    private lateinit var techBox: LinearLayout
    private lateinit var techButton: Button
    private lateinit var urgentButton: Button
    private lateinit var moreButton: Button
    private lateinit var moreBox: LinearLayout
    private lateinit var healthCard: LinearLayout
    private lateinit var healthTitle: TextView
    private lateinit var healthBody: TextView
    private lateinit var healthButton: Button
    private lateinit var summaryCard: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var feedbackBox: LinearLayout
    private lateinit var feedbackNote: TextView
    private lateinit var blockButton: android.widget.Button
    private var feedbackGivenFor: Any? = null // the summary object already answered
    private lateinit var toggleLangButton: Button
    private var pendingAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        lang = prefs.screenLanguage
        // Developers keep the diagnostics open; everyone else sees them collapsed.
        techVisible = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        tts = TextToSpeech(this) { ttsReady = it == TextToSpeech.SUCCESS }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }
        val page = ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) }

        val debugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        root.addView(theme.text(22f, bold = true).apply {
            text = UiStrings.get(Ui.TITLE, lang); setTextColor(theme.accent); isFocusable = true
            // Hidden developer entry: the capture probe exists only in debug builds, so this does nothing in a release.
            setOnLongClickListener {
                runCatching { startActivity(Intent().setClassName(packageName, "com.callguard.debug.ProbeActivity")) }.isSuccess
            }
        })
        // ---- status: is protection on, and what is the one thing to do? ----
        healthCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20); setBackgroundColor(theme.cardBg) }
        healthTitle = theme.text(24f, bold = true).also(healthCard::addView)
        healthBody = theme.text(16f).also(healthCard::addView)
        healthButton = theme.primary(HealthText.setupButton(lang)) { onHealthAction() }.also(healthCard::addView)
        root.addView(healthCard, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = 16 })
        caller = theme.text(14f)
        // Live warning during a call: hidden while everything is calm.
        risk = theme.text(26f, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(16, (24 * theme.scale).toInt(), 16, (24 * theme.scale).toInt())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE // screen readers announce every change
        }.also(root::addView)
        alert = theme.text(16f, bold = true).also(root::addView)

        // ---- last call (post-call summary): the one urgent action first, everything else under "More" ----
        summaryCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(16, 16, 16, 16); setBackgroundColor(theme.cardBg) }
        summaryCard.addView(theme.text(18f, bold = true).apply { text = UiStrings.get(Ui.HOME_LAST_CALL, lang); setTextColor(theme.accent) })
        summaryText = theme.text(15f).also(summaryCard::addView)
        urgentButton = theme.primary(UiStrings.get(Ui.RECOVERY_BUTTON, lang)) {
            val t = CallGuardState.state.summary?.findings?.map { it.tactic }?.toSet().orEmpty()
            startActivity(Intent(this, RecoveryActivity::class.java).putExtra(RecoveryActivity.EXTRA_SITUATIONS, RecoveryPlan.likelySituations(t).map { it.name }.toTypedArray()))
        }.also(summaryCard::addView)
        // "was this a scam?" (kept on this phone only)
        feedbackBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        feedbackBox.addView(theme.text(16f, bold = true).apply { text = UiStrings.get(Ui.FEEDBACK_Q, lang) })
        feedbackBox.addView(theme.button(UiStrings.get(Ui.FEEDBACK_YES, lang)) { answer(Verdict.SCAM) })
        feedbackBox.addView(theme.button(UiStrings.get(Ui.FEEDBACK_NO, lang)) { answer(Verdict.FINE) })
        feedbackBox.addView(theme.button(UiStrings.get(Ui.FEEDBACK_UNSURE, lang)) { answer(Verdict.UNSURE) })
        summaryCard.addView(feedbackBox)
        feedbackNote = theme.text(14f).also(summaryCard::addView)
        moreBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        toggleLangButton = theme.button("") { toggleSummaryLanguage() }.also(moreBox::addView)
        moreBox.addView(theme.button(UiStrings.get(Ui.READ_ALOUD, lang)) { readAloud() })
        moreBox.addView(theme.button(UiStrings.get(Ui.FAMILY_ALERT, lang)) { alertFamily() })
        moreBox.addView(theme.button(UiStrings.get(Ui.SHARE, lang)) { shareReport() })
        blockButton = theme.button("") { toggleBlock() }.also(moreBox::addView)
        moreBox.addView(theme.button(UiStrings.get(Ui.HELPLINE, lang)) { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))) })
        moreBox.addView(theme.button(UiStrings.get(Ui.DISMISS, lang)) { CallGuardState.update { it.copy(summary = null) } })
        moreButton = theme.button(UiStrings.get(Ui.HOME_MORE, lang)) {
            moreBox.visibility = if (moreBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            moreButton.text = UiStrings.get(if (moreBox.visibility == View.VISIBLE) Ui.HOME_LESS else Ui.HOME_MORE, lang)
        }.also(summaryCard::addView)
        summaryCard.addView(moreBox)
        root.addView(summaryCard, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = 16 })

        // ---- three places to go ----
        root.addView(theme.button(UiStrings.get(Ui.HOME_SETTINGS, lang)) { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(Ui.GR_HELP_OTHERS, lang)) { startActivity(Intent(this, HelperActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(Ui.HOME_PRACTICE, lang)) { startActivity(Intent(this, PracticeActivity::class.java)) })

        // ---- developer tools: debug builds only (test capture, stop, raw transcript, diagnostics) ----
        transcript = theme.text(18f)
        callState = theme.text(16f); captureState = theme.text(16f); gemma = theme.text(14f); indic = theme.text(13f); power = theme.text(13f)
        techButton = theme.button("") { techVisible = !techVisible; applyTech() }
        techBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (v in listOf(callState, captureState, gemma, indic, power)) techBox.addView(v)
        if (debugBuild) {
            root.addView(caller)
            root.addView(theme.button(UiStrings.get(Ui.TEST, lang)) { requestThen(CallGuardService.ACTION_TEST) })
            root.addView(theme.button(UiStrings.get(Ui.STOP, lang)) { startService(Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_STOP)) })
            root.addView(transcript); root.addView(techButton); root.addView(techBox)
        }

        setContentView(page)
        applyTech()
        if (!prefs.setupOffered && !HealthCheck.evaluate(HealthProbe.inputs(this)).protectionOn) {
            prefs.setupOffered = true // "Get ready" opens once by itself; the status card keeps it one tap away
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        if (prefs.screenLanguage != lang) { recreate(); return } // changed on the Languages page
        CallGuardState.listener = ::render
        render(CallGuardState.state)
    }

    override fun onStop() {
        CallGuardState.listener = null
        tts?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        tts?.shutdown(); tts = null
        super.onDestroy()
    }

    private fun applyTech() {
        techBox.visibility = if (techVisible) View.VISIBLE else View.GONE
        techButton.text = UiStrings.get(if (techVisible) Ui.TECH_HIDE else Ui.TECH_SHOW, lang)
        techButton.contentDescription = techButton.text
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
        healthTitle.text = HealthText.cardTitle(r, lang)
        healthTitle.setTextColor(if (r.protectionOn) Color.parseColor("#2E7D32") else Color.parseColor("#EF6C00"))
        healthBody.text = HealthText.cardBody(r, lang)
        healthButton.visibility = if (r.protectionOn) View.GONE else View.VISIBLE
        healthButton.text = if (r.blocking == listOf(com.callguard.core.HealthItem.PROTECTION_RUNNING)) HealthText.fix(com.callguard.core.HealthItem.PROTECTION_RUNNING, lang) else HealthText.setupButton(lang)
        healthCard.contentDescription = "${healthTitle.text}. ${healthBody.text}"
    }

    private fun render(s: UiState) {
        renderHealth()
        callState.text = "Call: ${s.callState}"
        captureState.text = "Capture: ${s.captureState}"
        gemma.text = s.gemma
        indic.text = s.indic
        power.text = s.power
        caller.text = if (s.caller.isEmpty()) "" else "Caller: ${s.caller}"
        val lvl = s.detection.level
        risk.text = UiStrings.risk(lvl, lang)
        risk.contentDescription = risk.text
        risk.setBackgroundColor(when (lvl) { RiskLevel.HIGH -> Color.parseColor("#C62828"); RiskLevel.MEDIUM -> Color.parseColor("#EF6C00"); else -> Color.parseColor("#2E7D32") })
        risk.setTextColor(if (lvl == RiskLevel.MEDIUM) Color.BLACK else Color.WHITE)
        val calm = lvl == RiskLevel.LOW && s.lastAlert.isEmpty() && (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0
        risk.visibility = if (calm) View.GONE else View.VISIBLE
        alert.visibility = if (calm) View.GONE else View.VISIBLE
        val line = UiStrings.alertLine(lvl, lang)
        alert.text = if (line.isEmpty()) "" else "$line ${s.lastAlert}"
        alert.setTextColor(if (theme.accessible) Color.parseColor("#FF8A80") else Color.RED)
        transcript.text = s.transcript.ifEmpty { UiStrings.get(Ui.TRANSCRIPT_EMPTY, lang) }
        val sum = s.summary
        summaryCard.visibility = if (sum == null) View.GONE else View.VISIBLE
        if (sum != null) {
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
        else callState.text = "Microphone permission denied"
    }

    private fun launch(action: String) {
        startForegroundService(Intent(this, CallGuardService::class.java).setAction(action))
    }
}
