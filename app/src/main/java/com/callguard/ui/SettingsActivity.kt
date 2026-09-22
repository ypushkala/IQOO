package com.callguard.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.callguard.CallGuardService
import com.callguard.R
import com.callguard.alert.AppPrefs
import com.callguard.core.SetupRun
import com.callguard.caller.Blocklist
import com.callguard.caller.PackStore
import com.callguard.caller.PackUpdater
import com.callguard.caller.UpdateResult
import com.callguard.core.AnalyseScope
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/**
 * Protection settings, as a plain grouped list (Protection / Accessibility / Help & testing / Privacy / Danger
 * zone) rather than a stack of buttons: a switch for each on/off preference, a row with a chevron for anything
 * that opens elsewhere, plain text for facts, and a button only for the one thing here that is a real action.
 */
class SettingsActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private val lang get() = prefs.screenLanguage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(theme.dp(20), theme.dp(24), theme.dp(20), theme.dp(24)) }.also { theme.avoidStatusBar(it) }
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
        root.addView(theme.text(24f, bold = true).apply { text = UiStrings.get(Ui.SETTINGS, lang); setTextColor(theme.fg); setPadding(theme.dp(4), 0, theme.dp(4), theme.dp(4)) })
        root.addView(theme.text(14f, muted = true).apply { text = UiStrings.get(Ui.SETTINGS_INTRO, lang); setPadding(theme.dp(4), 0, theme.dp(4), 0) })

        protectionSection()
        accessibilitySection()
        helpSection()
        privacySection()
        dangerSection()

        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }

    // ---- Protection: what gets analysed, and how the other party is told ----
    private fun protectionSection() {
        val rows = ArrayList<android.view.View>()
        rows += Rows.switchRow(this, theme, UiStrings.scopeOption(AnalyseScope.UNKNOWN_ONLY, lang).substringBefore(" ("),
            UiStrings.get(Ui.SCOPE_HINT, lang), checked = prefs.analyseScope == AnalyseScope.UNKNOWN_ONLY) { on ->
            prefs.analyseScope = if (on) AnalyseScope.UNKNOWN_ONLY else AnalyseScope.ALL; build()
        }
        rows += Rows.switchRow(this, theme, UiStrings.get(Ui.BEEP_TITLE, lang), UiStrings.get(Ui.BEEP_HINT, lang), checked = prefs.disclosureBeep) { on ->
            prefs.disclosureBeep = on; build()
        }
        val blocks = Blocklist(this)
        rows += Rows.navRow(this, theme, UiStrings.get(Ui.BLOCKED_NUMBERS_TITLE, lang), trailing = blocks.size.toString()) {
            if (blocks.size == 0) return@navRow
            AlertDialog.Builder(this).setMessage(UiStrings.get(Ui.BLOCK_CONFIRM, lang))
                .setPositiveButton(UiStrings.get(Ui.BLOCK_CLEAR, lang)) { _, _ -> blocks.clear(); build() }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        if (PackUpdater.available) {
            val store = PackStore(this)
            rows += Rows.navRow(this, theme, UiStrings.get(Ui.PACK_UPDATE, lang),
                trailing = if (store.version == 0L) "bundled" else store.version.toString()) {
                Thread {
                    val r = PackUpdater.update(this)
                    runOnUiThread {
                        val k = when (r) { UpdateResult.UPDATED -> Ui.PACK_UPDATED; UpdateResult.UP_TO_DATE -> Ui.PACK_UP_TO_DATE; UpdateResult.OFFLINE -> Ui.PACK_OFFLINE; else -> Ui.PACK_REJECTED }
                        Toast.makeText(this, UiStrings.get(k, lang), Toast.LENGTH_LONG).show(); build()
                    }
                }.start()
            }
        }
        Rows.section(this, theme, root, UiStrings.get(Ui.SEC_PROTECTION, lang), rows)
    }

    // ---- Accessibility: language, large text, who gets told, and the language models ----
    private fun accessibilitySection() {
        val rows = listOf(
            Rows.navRow(this, theme, UiStrings.get(Ui.MY_LANGUAGE, lang), trailing = UiStrings.name(prefs.screenLanguage)) {
                startActivity(Intent(this, LanguagesActivity::class.java))
            },
            Rows.switchRow(this, theme, UiStrings.get(Ui.ACCESSIBILITY_MODE_TITLE, lang), checked = theme.accessible) { on -> prefs.accessibility = on; recreate() },
            Rows.navRow(this, theme, UiStrings.get(Ui.FA_OPEN, lang), icon = R.drawable.ic_family) { startActivity(Intent(this, FamilyAlertActivity::class.java)) },
            Rows.navRow(this, theme, UiStrings.get(Ui.MI_TITLE, lang)) { startActivity(Intent(this, ModelImportActivity::class.java)) },
        )
        Rows.section(this, theme, root, UiStrings.get(Ui.SEC_ACCESSIBILITY, lang), rows)
    }

    // ---- Help & testing: see what happened, rehearse it, tell us what we missed ----
    private fun helpSection() {
        val rows = listOf(
            Rows.navRow(this, theme, UiStrings.get(Ui.ST_CALL_HISTORY, lang), icon = R.drawable.ic_history) { startActivity(Intent(this, HistoryActivity::class.java)) },
            Rows.navRow(this, theme, UiStrings.get(Ui.ST_PRACTICE, lang), icon = R.drawable.ic_play) { startActivity(Intent(this, PracticeActivity::class.java)) },
            Rows.navRow(this, theme, UiStrings.get(Ui.ST_REPORT_MISSED, lang), icon = R.drawable.ic_alert_triangle) { startActivity(Intent(this, MissedScamActivity::class.java)) },
            Rows.navRow(this, theme, UiStrings.get(Ui.GR_HELP_OTHERS, lang), icon = R.drawable.ic_family) { startActivity(Intent(this, HelperActivity::class.java)) },
        )
        Rows.section(this, theme, root, UiStrings.get(Ui.SEC_HELP, lang), rows)
        val last = SetupRun.parseAll(prefs.setupRuns).lastOrNull()
        root.addView(theme.text(12.5f, muted = true).apply {
            text = if (last == null) UiStrings.get(Ui.ST_METRICS_NONE, lang) else UiStrings.fmt(Ui.ST_METRICS_FMT, lang, "%d:%02d".format(last.durationMs / 60000, last.durationMs / 1000 % 60), last.taps) + if (last.meetsTarget) " ✓" else ""
            setPadding(theme.dp(4), 0, theme.dp(4), 0)
        })
    }

    // ---- Privacy: three short facts, then the one place to act on them ----
    private fun privacySection() {
        val rows = ArrayList<android.view.View>()
        rows += Rows.infoRow(this, theme, UiStrings.get(Ui.PRIV_ROW1_TITLE, lang), UiStrings.get(Ui.PRIV_ROW1_BODY, lang))
        rows += Rows.statusRow(this, theme, UiStrings.get(Ui.INTERNET_ACCESS, lang),
            UiStrings.get(if (TrustCheck.noInternetVerified(this)) Ui.TR_CHECK_YES else Ui.TR_CHECK_NO, lang),
            if (TrustCheck.noInternetVerified(this)) theme.safe else theme.caution)
        rows += Rows.infoRow(this, theme, UiStrings.get(Ui.PRIV_ROW2_TITLE, lang), UiStrings.get(Ui.PRIV_ROW2_BODY, lang))
        rows += Rows.infoRow(this, theme, UiStrings.get(Ui.PRIV_ROW3_TITLE, lang), UiStrings.get(Ui.PRIV_ROW3_BODY, lang))
        rows += Rows.navRow(this, theme, UiStrings.get(Ui.MANAGE_DATA, lang)) { manageData() }
        Rows.section(this, theme, root, UiStrings.get(Ui.SEC_PRIVACY, lang), rows)
    }

    private fun manageData() {
        AlertDialog.Builder(this).setTitle(UiStrings.get(Ui.MANAGE_DATA, lang)).setMessage(UiStrings.get(Ui.ERASE_CONFIRM, lang))
            .setPositiveButton(UiStrings.get(Ui.ERASE_ALL, lang)) { _, _ ->
                for (n in listOf("callguard_prefs", "callguard_feedback", "callguard_seen", "callguard_blocklist", "callguard_pack", "callguard_dl")) getSharedPreferences(n, MODE_PRIVATE).edit().clear().commit()
                PackStore(this).clear()
                Toast.makeText(this, UiStrings.get(Ui.ERASE_DONE, lang), Toast.LENGTH_LONG).show()
                build()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    // ---- Danger zone: kept visually apart, styled as a warning, not another setting ----
    private fun dangerSection() {
        root.addView(Rows.sectionHeader(this, theme, UiStrings.get(Ui.SEC_DANGER, lang)))
        root.addView(theme.dangerButton(UiStrings.get(Ui.HOME_TURN_OFF, lang)) {
            AlertDialog.Builder(this).setMessage(UiStrings.get(Ui.TURN_OFF_CONFIRM, lang))
                .setPositiveButton(UiStrings.get(Ui.HOME_TURN_OFF, lang)) { _, _ -> startService(Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_STOP)) }
                .setNegativeButton(android.R.string.cancel, null).show()
        })
    }
}
