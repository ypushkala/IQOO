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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.HP_TITLE, lang); setTextColor(theme.accent) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.HP_INTRO, lang) })
        root.addView(theme.text(16f, bold = true).apply { text = UiStrings.get(Ui.HP_STEP1, lang) })
        root.addView(theme.primary(UiStrings.get(Ui.MI_SHARE_APP, lang)) { ModelShare.share(this, true, lang) { status.text = it } })
        root.addView(theme.text(16f, bold = true).apply { text = UiStrings.get(Ui.HP_STEP2, lang); setPadding(0, 24, 0, 0) })
        root.addView(theme.primary(UiStrings.get(Ui.MI_SHARE_MODELS, lang)) { ModelShare.share(this, false, lang) { status.text = it } })
        status = theme.text(14f).also(root::addView)
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.HP_STEP3, lang); setPadding(0, 24, 0, 0) })
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }
}
