package com.callguard.ui

import android.app.Activity
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.callguard.alert.AppPrefs
import com.callguard.core.MissedScamReport
import com.callguard.core.ReportTag
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/** "A call that got through": a quick, tag-based, local-only report — no audio, no transcript, no number required. */
class MissedScamActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPrefs(this)
        val theme = UiTheme(this, prefs.accessibility)
        val lang = prefs.screenLanguage
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }.also { theme.avoidStatusBar(it) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })

        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.MISSED_TITLE, lang); setTextColor(theme.fg) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.MISSED_INTRO, lang) })

        val tagLabels = mapOf(
            ReportTag.OTP_ASKED to Ui.MISSED_TAG_OTP, ReportTag.THREATENED to Ui.MISSED_TAG_THREAT,
            ReportTag.INSTALL_APP to Ui.MISSED_TAG_APP, ReportTag.MONEY_ASKED to Ui.MISSED_TAG_MONEY, ReportTag.OTHER to Ui.MISSED_TAG_OTHER,
        )
        val picked = HashSet<ReportTag>()
        for ((tag, key) in tagLabels) root.addView(CheckBox(this).apply {
            text = UiStrings.get(key, lang); textSize = 16f; setTextColor(theme.fg)
            setOnCheckedChangeListener { _, on -> if (on) picked += tag else picked -= tag }
        })
        root.addView(theme.primary(UiStrings.get(Ui.MISSED_SAVE, lang)) {
            prefs.missedScamReports = MissedScamReport.append(prefs.missedScamReports, MissedScamReport(System.currentTimeMillis(), picked.toSet()))
            Toast.makeText(this, UiStrings.get(Ui.MISSED_SAVED, lang), Toast.LENGTH_LONG).show()
            finish()
        })
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }
}
