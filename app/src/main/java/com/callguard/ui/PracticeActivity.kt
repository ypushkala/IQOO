package com.callguard.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.alert.Alerter
import com.callguard.alert.AppPrefs
import com.callguard.core.PracticeScenario
import com.callguard.core.PracticeScenarios
import com.callguard.core.Strings
import com.callguard.core.RiskEngine
import com.callguard.core.RiskLevel
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/**
 * A small scam-literacy library, not one canned line: pick a scenario (OTP, digital arrest, lottery, KYC), each one plays
 * the real warning (screen, vibration, spoken voice) so people know what to expect. No microphone, nothing recorded.
 * The demo level is taken from the real detector's rating of the scenario's own line, so the demo is honest about what
 * that phrase would actually trigger.
 */
class PracticeActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private var alerter: Alerter? = null
    private val handler = Handler(Looper.getMainLooper())
    private val lang get() = prefs.screenLanguage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        alerter = Alerter(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }.also { theme.avoidStatusBar(it) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        showPicker()
    }

    private fun showPicker() {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.PR_PICK_TITLE, lang); setTextColor(theme.fg) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.PR_PICK_INTRO, lang) })
        for (s in PracticeScenarios.all) root.addView(theme.button(Strings.tactic(s.tactic, lang), com.callguard.R.drawable.ic_alert_triangle) { showScenario(s) })
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }

    private fun showScenario(scenario: PracticeScenario) {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.PR_TITLE, lang); setTextColor(theme.fg) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.PR_SAYS, lang) })
        root.addView(theme.text(20f).apply { text = "“" + scenario.line(lang) + "”"; background = theme.cardDrawable(); setPadding(24, 24, 24, 24) })
        val level = RiskEngine().evaluate(scenario.line(lang)).level.let { if (it < RiskLevel.MEDIUM) RiskLevel.MEDIUM else it }
        val banner = theme.text(20f, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(16, 32, 16, 32); visibility = View.GONE; setTextColor(android.graphics.Color.WHITE); setBackgroundColor(theme.danger)
        }
        val note = theme.text(15f)
        val start = theme.primary(UiStrings.get(Ui.PR_START, lang), com.callguard.R.drawable.ic_play) {}
        start.setOnClickListener {
            start.isEnabled = false
            note.text = UiStrings.get(Ui.PR_RINGING, lang)
            handler.postDelayed({
                banner.text = UiStrings.get(Ui.PR_WARNING, lang); banner.visibility = View.VISIBLE
                alerter?.warn(level, lang)
                note.text = UiStrings.get(Ui.PR_DONE, lang)
                prefs.practiceDone = true
            }, 2500)
        }
        root.addView(start); root.addView(banner); root.addView(note)
        root.addView(theme.button(UiStrings.get(Ui.PR_PICK_TITLE, lang)) { showPicker() })
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); alerter?.release(); alerter = null; super.onDestroy() }
}
