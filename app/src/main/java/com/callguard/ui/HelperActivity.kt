package com.callguard.ui

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.alert.AppPrefs
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/** For the family member who is good with phones: send the app and the language pack to a parent's phone, with the three steps to follow there. */
class HelperActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var status: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        val theme = UiTheme(this, prefs.accessibility)
        val lang = prefs.screenLanguage
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(theme.dp(20), theme.dp(24), theme.dp(20), theme.dp(24)) }.also { theme.avoidStatusBar(it) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        root.addView(theme.text(24f, bold = true).apply { text = UiStrings.get(Ui.HP_TITLE, lang); setPadding(theme.dp(4), 0, theme.dp(4), theme.dp(4)) })
        root.addView(theme.text(14f, muted = true).apply { text = UiStrings.get(Ui.HP_INTRO, lang); setPadding(theme.dp(4), 0, theme.dp(4), theme.dp(8)) })

        root.addView(numberedStep(theme, "1", UiStrings.get(Ui.HP_STEP1, lang)))
        root.addView(theme.button(UiStrings.get(Ui.HELPER_STEP1_ACTION, lang)) { ModelShare.share(this, true, lang) { status.text = it } })

        root.addView(numberedStep(theme, "2", UiStrings.get(Ui.HP_STEP2, lang)))
        root.addView(theme.button(UiStrings.get(Ui.HELPER_STEP2_ACTION, lang)) { ModelShare.share(this, false, lang) { status.text = it } })

        status = theme.text(13.5f, muted = true).also(root::addView)

        root.addView(numberedStep(theme, "3", UiStrings.get(Ui.HP_STEP3, lang)))
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }

    /** A step in the "what to do" list: a small numbered marker, not another bold all-caps heading. */
    private fun numberedStep(theme: UiTheme, number: String, text: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(theme.dp(4), theme.dp(20), theme.dp(4), theme.dp(4))
        addView(theme.text(15f, bold = true).apply {
            this.text = number; setTextColor(theme.accent)
            layoutParams = LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = theme.dp(10) }
        })
        addView(theme.text(15f).apply { this.text = text; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
    }
}
