package com.callguard.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.callguard.CallGuardService
import com.callguard.alert.AppPrefs
import com.callguard.core.SetupRun
import com.callguard.caller.Blocklist
import com.callguard.caller.PackStore
import com.callguard.caller.PackUpdater
import com.callguard.caller.UpdateResult
import com.callguard.core.AnalyseScope
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/** Protection settings. Saved immediately in private app storage. More options (disclosure beep, family auto-alert) land here. */
class SettingsActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private val lang get() = prefs.screenLanguage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }
        val page = ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        container.addView(NavBar.build(this, theme, lang, NavTab.SETTINGS))
        setContentView(container)
        build()
    }

    override fun onResume() { super.onResume(); if (::root.isInitialized) build() }

    private fun build() {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.SETTINGS, lang); setTextColor(theme.accent) })
        root.addView(theme.button(UiStrings.get(Ui.MY_LANGUAGE, lang) + ": " + UiStrings.name(prefs.screenLanguage)) { startActivity(Intent(this, LanguagesActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(if (theme.accessible) Ui.ACCESS_ON else Ui.ACCESS_OFF, lang)) { prefs.accessibility = !theme.accessible; recreate() })
        root.addView(theme.button(UiStrings.get(Ui.FA_OPEN, lang), com.callguard.R.drawable.ic_family) { startActivity(Intent(this, FamilyAlertActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(Ui.MI_TITLE, lang)) { startActivity(Intent(this, ModelImportActivity::class.java)) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.SETTINGS_INTRO, lang); setPadding(0, 16, 0, 0) })
        root.addView(theme.button("${UiStrings.get(Ui.SCOPE_LABEL, lang)}: ${UiStrings.scopeOption(prefs.analyseScope, lang)}") {
            val options = AnalyseScope.values()
            AlertDialog.Builder(this).setTitle(UiStrings.get(Ui.SCOPE_LABEL, lang))
                .setSingleChoiceItems(options.map { UiStrings.scopeOption(it, lang) }.toTypedArray(), prefs.analyseScope.ordinal) { d, which -> d.dismiss(); prefs.analyseScope = options[which]; build() }
                .show()
        })
        root.addView(theme.text(13f).apply { text = UiStrings.get(Ui.SCOPE_HINT, lang) })
        root.addView(theme.button(UiStrings.get(if (prefs.disclosureBeep) Ui.BEEP_ON else Ui.BEEP_OFF, lang)) { prefs.disclosureBeep = !prefs.disclosureBeep; build() })
        root.addView(theme.text(13f).apply { text = UiStrings.get(Ui.BEEP_HINT, lang) })
        if (PackUpdater.available) {
            val store = PackStore(this)
            root.addView(theme.text(13f).apply { text = UiStrings.fmt(Ui.PACK_VERSION_FMT, lang, if (store.version == 0L) "bundled" else store.version.toString()); setPadding(0, 24, 0, 0) })
            root.addView(theme.button(UiStrings.get(Ui.PACK_UPDATE, lang)) {
                Thread {
                    val r = PackUpdater.update(this)
                    runOnUiThread {
                        val k = when (r) { UpdateResult.UPDATED -> Ui.PACK_UPDATED; UpdateResult.UP_TO_DATE -> Ui.PACK_UP_TO_DATE; UpdateResult.OFFLINE -> Ui.PACK_OFFLINE; else -> Ui.PACK_REJECTED }
                        Toast.makeText(this, UiStrings.get(k, lang), Toast.LENGTH_LONG).show(); build()
                    }
                }.start()
            })
        }
        val blocks = Blocklist(this)
        root.addView(theme.text(15f).apply { text = UiStrings.fmt(Ui.BLOCK_LIST_FMT, lang, blocks.size); setPadding(0, 24, 0, 0) })
        if (blocks.size > 0) root.addView(theme.button(UiStrings.get(Ui.BLOCK_CLEAR, lang)) { blocks.clear(); build() })
        root.addView(theme.text(18f, bold = true).apply { text = UiStrings.get(Ui.ST_SECTION_HELP, lang); setPadding(0, 32, 0, 0) })
        root.addView(theme.button(UiStrings.get(Ui.HOME_HISTORY, lang), com.callguard.R.drawable.ic_history) { startActivity(Intent(this, HistoryActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(Ui.MISSED_BUTTON, lang), com.callguard.R.drawable.ic_alert_triangle) { startActivity(Intent(this, MissedScamActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(Ui.GR_HELP_OTHERS, lang), com.callguard.R.drawable.ic_family) { startActivity(Intent(this, HelperActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(Ui.HOME_PRACTICE, lang), com.callguard.R.drawable.ic_play) { startActivity(Intent(this, PracticeActivity::class.java)) })
        root.addView(theme.button(UiStrings.get(Ui.HOME_TURN_OFF, lang), com.callguard.R.drawable.ic_block) { startService(Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_STOP)) })
        val last = SetupRun.parseAll(prefs.setupRuns).lastOrNull()
        root.addView(theme.text(13f).apply {
            text = if (last == null) UiStrings.get(Ui.ST_METRICS_NONE, lang) else UiStrings.fmt(Ui.ST_METRICS_FMT, lang, "%d:%02d".format(last.durationMs / 60000, last.durationMs / 1000 % 60), last.taps) + if (last.meetsTarget) " ✓" else ""
        })
        root.addView(theme.text(18f, bold = true).apply { text = UiStrings.get(Ui.PRIVACY, lang); setPadding(0, 32, 0, 0) })
        root.addView(theme.text(14f).apply { text = UiStrings.get(Ui.PRIVACY_BODY, lang) })
        root.addView(theme.button(UiStrings.get(Ui.ERASE_ALL, lang)) {
            AlertDialog.Builder(this).setMessage(UiStrings.get(Ui.ERASE_CONFIRM, lang))
                .setPositiveButton(UiStrings.get(Ui.ERASE_ALL, lang)) { _, _ ->
                    for (n in listOf("callguard_prefs", "callguard_feedback", "callguard_seen", "callguard_blocklist", "callguard_pack", "callguard_dl")) getSharedPreferences(n, MODE_PRIVATE).edit().clear().commit()
                    PackStore(this).clear()
                    Toast.makeText(this, UiStrings.get(Ui.ERASE_DONE, lang), Toast.LENGTH_LONG).show()
                    build()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        })
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }
}
