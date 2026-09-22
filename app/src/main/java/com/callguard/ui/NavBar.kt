package com.callguard.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import com.callguard.core.Lang
import com.callguard.core.Ui
import com.callguard.core.UiStrings

enum class NavTab { HOME, HISTORY, SETTINGS }

/**
 * A small, persistent three-item row (Home / History / Settings) so every top-level screen can reach every other one,
 * instead of dead-ending back at Home. Kept outside the scrolling content so it never scrolls away.
 */
object NavBar {
    fun build(activity: Activity, theme: UiTheme, lang: Lang, current: NavTab): LinearLayout {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(theme.cardBg) }
        // Android 15 (targetSdk 35) draws edge-to-edge by default: without this, the system's own gesture/button bar sits
        // on top of the last few pixels of the screen, so a bottom-pinned row like this is there but not reliably tappable.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(row) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom)
            insets
        }
        fun item(label: String, tab: NavTab, target: Class<out Activity>?) = Button(activity).apply {
            text = (if (tab == current) "● " else "") + label
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f * theme.scale)
            setTextColor(if (tab == current) theme.accent else theme.fg)
            setBackgroundColor(Color.TRANSPARENT)
            isEnabled = tab != current
            contentDescription = label
            layoutParams = LinearLayout.LayoutParams(0, (56 * theme.scale).toInt(), 1f)
            if (target != null) setOnClickListener { activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        }
        row.addView(item(homeLabel(lang), NavTab.HOME, MainActivity::class.java))
        row.addView(item(UiStrings.get(Ui.HOME_HISTORY, lang), NavTab.HISTORY, HistoryActivity::class.java))
        row.addView(item(UiStrings.get(Ui.HOME_SETTINGS, lang), NavTab.SETTINGS, SettingsActivity::class.java))
        return row
    }

    private fun homeLabel(lang: Lang) = when (lang) { Lang.EN -> "Home"; Lang.HI -> "होम"; Lang.TE -> "హోమ్" }
}
