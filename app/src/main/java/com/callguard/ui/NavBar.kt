package com.callguard.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.callguard.R
import com.callguard.core.Lang
import com.callguard.core.Ui
import com.callguard.core.UiStrings

enum class NavTab { HOME, HISTORY, SETTINGS }

/**
 * A small, persistent three-item row (Home / History / Settings) so every top-level screen can reach every other one,
 * instead of dead-ending back at Home. Kept outside the scrolling content so it never scrolls away. Icon-over-label,
 * the usual shape for a bottom tab bar, so it reads at a glance rather than as three more text buttons.
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
        fun item(label: String, icon: Int, tab: NavTab, target: Class<out Activity>?) = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val color = if (tab == current) theme.accent else theme.fgMuted
            addView(ImageView(activity).apply {
                setImageDrawable(ContextCompat.getDrawable(activity, icon)?.mutate()?.apply { setTint(color) })
                val s = (22 * theme.scale).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
            })
            addView(TextView(activity).apply {
                text = label; setTextColor(color); gravity = android.view.Gravity.CENTER
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f * theme.scale)
                setPadding(0, (4 * theme.scale).toInt(), 0, 0)
            })
            isEnabled = tab != current
            contentDescription = label
            isClickable = target != null
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f) // wrap content: never clips the label under the icon
            setPadding(0, (10 * theme.scale).toInt(), 0, (10 * theme.scale).toInt())
            if (target != null) setOnClickListener { activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        }
        row.addView(item(homeLabel(lang), R.drawable.ic_home, NavTab.HOME, MainActivity::class.java))
        row.addView(item(UiStrings.get(Ui.HOME_HISTORY, lang), R.drawable.ic_history, NavTab.HISTORY, HistoryActivity::class.java))
        row.addView(item(UiStrings.get(Ui.HOME_SETTINGS, lang), R.drawable.ic_settings, NavTab.SETTINGS, SettingsActivity::class.java))
        return row
    }

    private fun homeLabel(lang: Lang) = when (lang) { Lang.EN -> "Home"; Lang.HI -> "होम"; Lang.TE -> "హోమ్" }
}
