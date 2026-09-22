package com.callguard.ui

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.CallGuardService
import com.callguard.CallGuardState
import com.callguard.alert.AppPrefs
import com.callguard.core.BrandAutostart
import com.callguard.core.HealthItem
import com.callguard.core.HealthText
import com.callguard.core.Lang
import com.callguard.core.SetupRun
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/**
 * "Get ready": a straight line. One step per screen, in a fixed order, with one big action and, once it is done, one big Next.
 * There is no list of other steps and no skip button. The only ways past a step are the ones that cannot leave anyone stuck:
 * a permission or role the person declined, and "English only for now" for the language files.
 * Time and in-app taps are kept on the phone so a tester can be measured (see SetupRun).
 */
class SetupActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private val lang get() = prefs.screenLanguage
    private var shown = -1
    private companion object { const val REQ_PERMS = 1; const val REQ_CONTACTS = 2; const val REQ_ROLE = 3 }

    private class Step(val key: String, val title: String, val why: String, val done: Boolean, val action: String?, val extra: Pair<String, () -> Unit>? = null, val run: () -> Unit)
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }.also { theme.avoidStatusBar(it) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        if (prefs.setupStartedAt == 0L) { prefs.setupStartedAt = System.currentTimeMillis(); prefs.setupTaps = 0 }
    }

    override fun onResume() { super.onResume(); build() }

    override fun onDestroy() { tts?.shutdown(); tts = null; super.onDestroy() }

    private fun readTrustBodyAloud() {
        if (tts == null) tts = android.speech.tts.TextToSpeech(this) { st -> ttsReady = st == android.speech.tts.TextToSpeech.SUCCESS; if (ttsReady) speakTrustBody() }
        else if (ttsReady) speakTrustBody()
    }

    private fun speakTrustBody() {
        tts?.language = com.callguard.alert.Alerter.localeOf(lang)
        tts?.speak(UiStrings.get(Ui.TR_BODY, lang), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "trust")
    }

    /** A thin, quiet fraction-of-the-way bar — two weighted views, no extra library, no numbers competing with "Step X of Y". */
    private fun progressBar(shown: Int, total: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(-1, theme.dp(4)).also { it.topMargin = theme.dp(10); it.marginStart = theme.dp(4); it.marginEnd = theme.dp(4) }
        val done = (shown + 1).coerceIn(1, total)
        addView(android.view.View(this@SetupActivity).apply { setBackgroundColor(theme.accent) }, LinearLayout.LayoutParams(0, -1, done.toFloat()))
        if (done < total) addView(android.view.View(this@SetupActivity).apply { setBackgroundColor(theme.cardBorder) }, LinearLayout.LayoutParams(0, -1, (total - done).toFloat()))
    }

    private fun tap() { prefs.setupTaps = prefs.setupTaps + 1 }
    private fun action(label: String, big: Boolean = true, run: () -> Unit) = (if (big) theme.primary(label) { tap(); run() } else theme.button(label) { tap(); run() })

    private fun steps(): List<Step> {
        val h = HealthProbe.inputs(this)
        val permsOk = h.mic && h.phone && h.notifications
        val rm = getSystemService(RoleManager::class.java)
        val roleAvailable = runCatching { rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) }.getOrDefault(false)
        val list = ArrayList<Step>()
        list += Step("LANG", UiStrings.get(Ui.GR_LANG_TITLE, lang), UiStrings.get(Ui.GR_LANG_WHY, lang), prefs.languagesChosen, null, null, {})
        list += Step("TRUST", UiStrings.get(Ui.TR_TITLE, lang), UiStrings.get(Ui.TR_BODY, lang), prefs.trustStepSeen, null, null, {})
        list += Step("PERMS", UiStrings.get(Ui.GR_PERMS_TITLE, lang), UiStrings.get(Ui.GR_PERMS_WHY, lang), permsOk,
            if (prefs.permsAsked >= 2) UiStrings.get(Ui.GR_PERMS_SETTINGS, lang) else HealthText.fix(HealthItem.MIC_PERMISSION, lang)) { askPermissions() }
        list += Step("ROLE", HealthText.name(HealthItem.CALLER_ID_ROLE, lang), HealthText.why(HealthItem.CALLER_ID_ROLE, lang),
            h.callerIdRole || prefs.roleDeclined || !roleAvailable, HealthText.fix(HealthItem.CALLER_ID_ROLE, lang)) { askRole() }
        list += Step("BATTERY", HealthText.name(HealthItem.BATTERY_EXEMPTION, lang), HealthText.why(HealthItem.BATTERY_EXEMPTION, lang),
            h.batteryExempt || prefs.batteryAsked, HealthText.fix(HealthItem.BATTERY_EXEMPTION, lang)) {
            prefs.batteryAsked = true
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        if (BrandAutostart.candidates(Build.MANUFACTURER).isNotEmpty())
            list += Step("AUTOSTART", HealthText.autostartName(lang), HealthText.autostartWhy(lang), prefs.autostartAcknowledged, HealthText.openSettings(lang),
                extra = UiStrings.get(Ui.SETUP_MARK_DONE, lang) to { prefs.autostartAcknowledged = true }, run = { openAutostart() })
        list += Step("MODELS", HealthText.name(HealthItem.MODELS, lang), HealthText.why(HealthItem.MODELS, lang),
            HealthItem.MODELS !in com.callguard.core.HealthCheck.evaluate(h).missing || prefs.modelsDeferred, HealthText.fix(HealthItem.MODELS, lang),
            extra = UiStrings.get(Ui.GR_MODELS_LATER, lang) to { prefs.modelsDeferred = true }, run = { startActivity(Intent(this, ModelImportActivity::class.java)) })
        list += Step("RUN", HealthText.name(HealthItem.PROTECTION_RUNNING, lang), HealthText.why(HealthItem.PROTECTION_RUNNING, lang),
            CallGuardState.state.monitoring, HealthText.fix(HealthItem.PROTECTION_RUNNING, lang)) {
            if (permsOk) { startForegroundService(Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_START)); root.postDelayed({ build() }, 800) } else askPermissions()
        }
        list += Step("PRACTICE", UiStrings.get(Ui.GR_PRACTICE_TITLE, lang), UiStrings.get(Ui.GR_PRACTICE_WHY, lang), prefs.practiceDone, UiStrings.get(Ui.GR_PRACTICE_START, lang)) {
            startActivity(Intent(this, PracticeActivity::class.java))
        }
        return list
    }

    private fun build() {
        val steps = steps()
        if (shown < 0 || shown > steps.size) shown = steps.indexOfFirst { !it.done }.let { if (it < 0) steps.size else it }
        root.removeAllViews()
        // Small eyebrow, not the loudest thing on screen — the current step's own heading carries the weight instead.
        root.addView(theme.text(13f, bold = true).apply { text = HealthText.setupTitle(lang); setTextColor(theme.accent); setPadding(theme.dp(4), 0, theme.dp(4), 0) })
        if (shown >= steps.size) { finishScreen(steps.size); return }
        val s = steps[shown]
        root.addView(theme.text(13.5f, muted = true).apply { text = UiStrings.fmt(Ui.SETUP_STEP_FMT, lang, shown + 1, steps.size); setPadding(theme.dp(4), theme.dp(2), theme.dp(4), 0) })
        root.addView(progressBar(shown, steps.size))
        root.addView(theme.text(27f, bold = true).apply { text = s.title; contentDescription = s.title; setPadding(theme.dp(4), theme.dp(16), theme.dp(4), theme.dp(6)) })
        root.addView(theme.text(16f, muted = true).apply { text = s.why; setPadding(theme.dp(4), 0, theme.dp(4), 0) })
        if (s.key == "LANG") {
            for (l in Lang.values()) root.addView(action((if (l == prefs.screenLanguage && prefs.languagesChosen) "✓  " else "") + UiStrings.name(l)) { prefs.setMyLanguage(l); build() })
        }
        if (s.key == "TRUST") {
            val verified = TrustCheck.noInternetVerified(this)
            root.addView(theme.text(15f, bold = true).apply {
                text = UiStrings.fmt(Ui.TR_CHECK_FMT, lang, UiStrings.get(if (verified) Ui.TR_CHECK_YES else Ui.TR_CHECK_NO, lang))
                setTextColor(if (verified) theme.safe else theme.caution)
            })
            root.addView(theme.button(UiStrings.get(Ui.TR_READ_ALOUD, lang), com.callguard.R.drawable.ic_volume) { readTrustBodyAloud() })
            if (!prefs.trustStepSeen) root.addView(action(UiStrings.get(Ui.GR_NEXT, lang)) { prefs.trustStepSeen = true; build() })
        }
        if (s.done) {
            root.addView(theme.text(18f, bold = true).apply { text = UiStrings.get(Ui.GR_DONE_TICK, lang); setTextColor(theme.safe) })
        } else if (s.action != null) {
            root.addView(action(s.action, run = s.run))
            s.extra?.let { (label, run) -> root.addView(action(label, big = false) { run(); build() }) }
        }
        if (s.done) root.addView(action(UiStrings.get(Ui.GR_NEXT, lang)) { advance() })
        if (shown > 0) root.addView(theme.button(UiStrings.get(Ui.GR_BACK, lang)) { shown--; build() })
    }

    private fun advance() {
        val steps = steps()
        var i = shown + 1
        while (i < steps.size && steps[i].done) i++
        shown = i
        build()
    }

    private fun finishScreen(total: Int) {
        root.addView(theme.text(26f, bold = true).apply { text = UiStrings.get(Ui.GR_FINISH_TITLE, lang); setPadding(0, 24, 0, 8) })
        root.addView(theme.text(17f).apply { text = UiStrings.get(Ui.GR_FINISH_BODY, lang) })
        root.addView(action(UiStrings.get(Ui.GR_FINISH, lang)) { recordRun(total); finish() })
        root.addView(theme.button(UiStrings.get(Ui.GR_HELP_OTHERS, lang)) { recordRun(total); startActivity(Intent(this, HelperActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(Ui.GR_BACK, lang)) { shown = total - 1; build() })
    }

    /** Stores the time and tap count of a completed run (on this phone only) and starts a fresh count. */
    private fun recordRun(total: Int) {
        val started = prefs.setupStartedAt.takeIf { it > 0 } ?: return
        prefs.setupRuns = SetupRun.append(prefs.setupRuns, SetupRun(started, System.currentTimeMillis() - started, prefs.setupTaps, total))
        prefs.setupStartedAt = 0; prefs.setupTaps = 0
    }

    private fun askPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return
        if (prefs.permsAsked >= 2) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        else requestPermissions(missing.toTypedArray(), REQ_PERMS)
    }

    private fun askRole() {
        // Contacts are optional and only used to skip calls from people you know; the role is what matters.
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
        else requestRole()
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ROLE) {
            val held = runCatching { getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }.getOrDefault(false)
            if (!held) prefs.roleDeclined = true // declined: move on rather than ask forever
        }
    }

    /** Tries each known vendor page in turn, then falls back to CallGuard's own app-settings page. */
    private fun openAutostart() {
        for (t in BrandAutostart.candidates(Build.MANUFACTURER)) {
            val i = Intent().setComponent(ComponentName(t.pkg, t.cls))
            if (i.resolveActivity(packageManager) != null && runCatching { startActivity(i) }.isSuccess) return
        }
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ_CONTACTS) { requestRole(); return }
        if (code == REQ_PERMS && results.any { it != PackageManager.PERMISSION_GRANTED }) prefs.permsAsked = prefs.permsAsked + 1
        build()
    }
}
