package com.callguard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared look for the app's screens: normal, or large text + high contrast (accessibility mode). */
class UiTheme(private val ctx: Context, val accessible: Boolean) {
    val scale = if (accessible) 1.5f else 1f
    val bg get() = if (accessible) Color.BLACK else Color.parseColor("#FAFAFA")
    val fg get() = if (accessible) Color.WHITE else Color.parseColor("#212121")
    val accent get() = if (accessible) Color.YELLOW else Color.parseColor("#1565C0")
    val buttonBg get() = if (accessible) Color.parseColor("#FFEB3B") else Color.parseColor("#D8D8D8")
    val cardBg get() = if (accessible) Color.parseColor("#222222") else Color.parseColor("#ECEFF1")

    fun text(sp: Float, bold: Boolean = false) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale)
        setTextColor(fg); setPadding(0, (8 * scale).toInt(), 0, (8 * scale).toInt())
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun button(label: String, onClick: () -> Unit) = Button(ctx).apply {
        text = label; setOnClickListener { onClick() }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale); setTextColor(Color.BLACK); setBackgroundColor(buttonBg)
        minimumHeight = (48 * scale).toInt()
        layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = (8 * scale).toInt() }
        contentDescription = label
    }

    /** The one main action on a screen: larger, filled with the accent colour. */
    fun primary(label: String, onClick: () -> Unit) = Button(ctx).apply {
        text = label; setOnClickListener { onClick() }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * scale); setTextColor(if (accessible) Color.BLACK else Color.WHITE); setBackgroundColor(accent)
        minimumHeight = (64 * scale).toInt()
        layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = (16 * scale).toInt() }
        contentDescription = label
    }
}
