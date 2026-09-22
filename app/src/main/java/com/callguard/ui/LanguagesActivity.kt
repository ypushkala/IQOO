package com.callguard.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.alert.AppPrefs
import com.callguard.core.Lang
import com.callguard.core.LanguageSetting
import com.callguard.core.SummaryLanguage
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/**
 * One page for the four language choices (screen, spoken warning, summary, family message). Every change is saved
 * immediately in private app storage. Shown once on first run, and from the "Languages" button any time.
 */
class LanguagesActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private val lang get() = prefs.screenLanguage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(theme.dp(32), theme.dp(48), theme.dp(32), theme.dp(32)) }.also { theme.avoidStatusBar(it) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        build()
    }

    private var advanced = false

    private fun build() {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.MY_LANGUAGE, lang); setTextColor(theme.fg) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.MY_LANGUAGE_HINT, lang) })
        for (l in Lang.values()) root.addView(Rows.selectableRow(this, theme, UiStrings.name(l), selected = l == prefs.screenLanguage) {
            prefs.setMyLanguage(l); recreate()
        })
        root.addView(theme.button(UiStrings.get(if (advanced) Ui.ADVANCED_HIDE else Ui.ADVANCED_SHOW, lang)) { advanced = !advanced; build() })
        if (advanced) {
            root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.LANG_INTRO, lang) })
            root.addView(theme.text(14f).apply { text = UiStrings.get(Ui.LANG_EXAMPLE, lang) })
            row(Ui.LANG_SCREEN, UiStrings.name(prefs.screenLanguage)) { pickLang(Ui.LANG_SCREEN, prefs.screenLanguage) { prefs.screenLanguage = it; recreate() } }
            row(Ui.LANG_SPOKEN, UiStrings.spokenOption(prefs.language, lang)) {
                choose(Ui.LANG_SPOKEN, LanguageSetting.values().map { UiStrings.spokenOption(it, lang) }, prefs.language.ordinal) { prefs.language = LanguageSetting.values()[it]; build() }
            }
            row(Ui.LANG_SUMMARY, UiStrings.summaryOption(prefs.summaryLanguage, lang)) {
                choose(Ui.LANG_SUMMARY, SummaryLanguage.values().map { UiStrings.summaryOption(it, lang) }, prefs.summaryLanguage.ordinal) { prefs.summaryLanguage = SummaryLanguage.values()[it]; build() }
            }
            row(Ui.LANG_FAMILY, UiStrings.name(prefs.familyMessageLanguage)) { pickLang(Ui.LANG_FAMILY, prefs.familyMessageLanguage) { prefs.familyMessageLanguage = it; build() } }
        }
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { prefs.languagesChosen = true; finish() })
    }

    private fun row(label: Ui, value: String, onClick: () -> Unit) {
        root.addView(theme.button("${UiStrings.get(label, lang)}: $value", onClick = onClick))
    }

    private fun pickLang(title: Ui, current: Lang, set: (Lang) -> Unit) =
        choose(title, Lang.values().map { UiStrings.name(it) }, current.ordinal) { set(Lang.values()[it]) }

    private fun choose(title: Ui, options: List<String>, checked: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle(UiStrings.get(title, lang))
            .setSingleChoiceItems(options.toTypedArray(), checked) { d, which -> d.dismiss(); onPick(which) }
            .show()
    }

    /** Leaving the page (including Back) counts as having chosen: the first-run prompt is not shown again. */
    override fun onPause() { prefs.languagesChosen = true; super.onPause() }
}
