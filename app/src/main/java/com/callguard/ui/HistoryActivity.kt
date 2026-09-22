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
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }.also { theme.avoidStatusBar(it) }
        val page = ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        container.addView(NavBar.build(this, theme, lang, NavTab.HISTORY))
        setContentView(container)
        build()
    }

    override fun onResume() { super.onResume(); if (::root.isInitialized) build() } // a report or a block may have just been added

    /** A thin, inset divider between two list rows — same treatment as the Settings rows use. */
    private fun divider() = android.view.View(this).apply {
        setBackgroundColor(theme.cardBorder)
        layoutParams = LinearLayout.LayoutParams(-1, theme.dp(1)).also { it.marginStart = theme.dp(4) }
    }

    private fun build() {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.HIST_TITLE, lang); setTextColor(theme.fg) })

        val calls = HistoryEntry.parseAll(prefs.callHistory).asReversed()
        if (calls.isEmpty()) {
            root.addView(theme.text(15f, muted = true).apply { text = UiStrings.get(Ui.HIST_EMPTY_HINT, lang); setPadding(0, theme.dp(24), 0, theme.dp(2)) })
            root.addView(theme.text(15f, bold = true).apply { text = UiStrings.get(Ui.HIST_EMPTY, lang) })
            root.addView(theme.button(UiStrings.get(Ui.ST_PRACTICE, lang), com.callguard.R.drawable.ic_play) { startActivity(android.content.Intent(this, PracticeActivity::class.java)) })
        } else {
            for ((i, e) in calls.withIndex()) {
                if (i > 0) root.addView(divider())
                val dateTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.atEpochMs))
                val tactics = e.tactics.joinToString(", ") { Strings.tactic(it, lang) }
                val detail = listOfNotNull(e.callerLabel, tactics.ifEmpty { null }).joinToString(" · ").ifEmpty { "—" }
                root.addView(Rows.historyRow(this, theme, theme.statusColor(e.level), dateTime, UiStrings.risk(e.level, lang), detail))
            }
        }

        val reports = MissedScamReport.parseAll(prefs.missedScamReports).asReversed()
        if (reports.isNotEmpty()) {
            root.addView(Rows.sectionHeader(this, theme, UiStrings.get(Ui.HIST_REPORTS_TITLE, lang)))
            for ((i, r) in reports.withIndex()) {
                if (i > 0) root.addView(divider())
                val tags = r.tags.joinToString(", ") { missedTagLabel(it, lang) }
                val dateTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.atEpochMs))
                root.addView(Rows.historyRow(this, theme, theme.fgMuted, dateTime, "", tags.ifEmpty { r.note.ifEmpty { "—" } }))
            }
        }

        root.addView(theme.text(13.5f, muted = true).apply {
            text = UiStrings.fmt(Ui.HIST_BLOCKED_FMT, lang, Blocklist(this@HistoryActivity).size)
            setPadding(theme.dp(4), theme.dp(24), theme.dp(4), 0)
        })
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
