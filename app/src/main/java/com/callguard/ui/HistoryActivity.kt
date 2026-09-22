package com.callguard.ui

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.alert.AppPrefs
import com.callguard.caller.Blocklist
import com.callguard.core.HistoryEntry
import com.callguard.core.MissedScamReport
import com.callguard.core.Strings
import com.callguard.core.Ui
import com.callguard.core.UiStrings
import java.text.DateFormat
import java.util.Date

/**
 * One timeline instead of scattered screens: past calls (from CallGuardService), calls reported as missed
 * (Settings → Help), and how many numbers are blocked. Everything here is read from local storage only.
 */
class HistoryActivity : Activity() {
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
        container.addView(NavBar.build(this, theme, lang, NavTab.HISTORY))
        setContentView(container)
        build()
    }

    override fun onResume() { super.onResume(); if (::root.isInitialized) build() } // a report or a block may have just been added

    private fun build() {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.HIST_TITLE, lang); setTextColor(theme.accent) })

        val calls = HistoryEntry.parseAll(prefs.callHistory).asReversed()
        if (calls.isEmpty()) {
            root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.HIST_EMPTY, lang) })
        } else {
            for (e in calls) {
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12); setBackgroundColor(theme.cardBg) }
                box.addView(theme.text(16f, bold = true).apply {
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.atEpochMs)) + "  ·  " + UiStrings.risk(e.level, lang)
                    setTextColor(theme.statusColor(e.level))
                })
                val tactics = e.tactics.joinToString(", ") { Strings.tactic(it, lang) }
                box.addView(theme.text(14f).apply { text = listOfNotNull(e.callerLabel, tactics.ifEmpty { null }).joinToString(" · ").ifEmpty { "—" } })
                root.addView(box, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = 12 })
            }
        }

        val reports = MissedScamReport.parseAll(prefs.missedScamReports).asReversed()
        if (reports.isNotEmpty()) {
            root.addView(theme.text(18f, bold = true).apply { text = UiStrings.get(Ui.HIST_REPORTS_TITLE, lang); setPadding(0, 24, 0, 0) })
            for (r in reports) {
                val tags = r.tags.joinToString(", ") { missedTagLabel(it, lang) }
                root.addView(theme.text(14f).apply {
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.atEpochMs)) + "  " + tags.ifEmpty { r.note.ifEmpty { "—" } }
                })
            }
        }

        val blocked = Blocklist(this).size
        root.addView(theme.text(15f).apply { text = UiStrings.fmt(Ui.HIST_BLOCKED_FMT, lang, blocked); setPadding(0, 24, 0, 0) })
        root.addView(theme.button(UiStrings.get(Ui.MISSED_BUTTON, lang)) { startActivity(android.content.Intent(this, MissedScamActivity::class.java)) })
    }

    private fun missedTagLabel(t: com.callguard.core.ReportTag, lang: com.callguard.core.Lang): String = when (t) {
        com.callguard.core.ReportTag.OTP_ASKED -> UiStrings.get(Ui.MISSED_TAG_OTP, lang)
        com.callguard.core.ReportTag.THREATENED -> UiStrings.get(Ui.MISSED_TAG_THREAT, lang)
        com.callguard.core.ReportTag.INSTALL_APP -> UiStrings.get(Ui.MISSED_TAG_APP, lang)
        com.callguard.core.ReportTag.MONEY_ASKED -> UiStrings.get(Ui.MISSED_TAG_MONEY, lang)
        com.callguard.core.ReportTag.OTHER -> UiStrings.get(Ui.MISSED_TAG_OTHER, lang)
    }
}
