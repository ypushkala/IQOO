package com.callguard.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.alert.Alerter
import com.callguard.alert.AppPrefs
import com.callguard.core.PracticeScript
import com.callguard.core.RiskLevel
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/**
 * A safe rehearsal: shows a pretend scammer line, then triggers the real warning (screen, vibration and spoken voice in the
 * user's language), so people know what to expect. No microphone, nothing recorded.
 */
class PracticeActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private var alerter: Alerter? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        val theme = UiTheme(this, prefs.accessibility)
        val lang = prefs.screenLanguage
        alerter = Alerter(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.PR_TITLE, lang); setTextColor(theme.accent) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.PR_SAYS, lang) })
        root.addView(theme.text(20f).apply { text = "“" + PracticeScript.line(lang) + "”"; setBackgroundColor(theme.cardBg); setPadding(24, 24, 24, 24) })
        val banner = theme.text(20f, bold = true).apply { gravity = Gravity.CENTER; setPadding(16, 32, 16, 32); visibility = android.view.View.GONE; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#C62828")) }
        val note = theme.text(15f)
        val start = theme.primary(UiStrings.get(Ui.PR_START, lang)) {}
        start.setOnClickListener {
            start.isEnabled = false
            note.text = UiStrings.get(Ui.PR_RINGING, lang)
            handler.postDelayed({
                banner.text = UiStrings.get(Ui.PR_WARNING, lang); banner.visibility = android.view.View.VISIBLE
                alerter?.warn(RiskLevel.HIGH, lang)
                note.text = UiStrings.get(Ui.PR_DONE, lang)
                prefs.practiceDone = true
            }, 2500)
        }
        root.addView(start); root.addView(banner); root.addView(note)
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); alerter?.release(); alerter = null; super.onDestroy() }
}
