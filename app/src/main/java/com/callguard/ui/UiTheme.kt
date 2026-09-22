package com.callguard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Shared look for the app's screens: normal, or large text + high contrast (accessibility mode). The palette matches
 * the "How CallGuard works" explainer diagram (warm off-white page, white bordered cards, the same risk colours),
 * so the live app and the thing people saw explained a minute earlier look like one product.
 */
class UiTheme(private val ctx: Context, val accessible: Boolean) {
    val scale = if (accessible) 1.5f else 1f
    val bg get() = if (accessible) Color.BLACK else Color.parseColor("#F6F5F2")
    val fg get() = if (accessible) Color.WHITE else Color.parseColor("#1C2024")
    val fgMuted get() = if (accessible) Color.parseColor("#CCCCCC") else Color.parseColor("#5B6472")
    val accent get() = if (accessible) Color.YELLOW else Color.parseColor("#1565C0")
    val buttonBg get() = if (accessible) Color.parseColor("#FFEB3B") else Color.parseColor("#ECEEF1")
    val buttonBorder get() = if (accessible) Color.parseColor("#FFEB3B") else Color.parseColor("#D7DBE0")
    val cardBg get() = if (accessible) Color.parseColor("#222222") else Color.WHITE
    val cardBorder get() = if (accessible) Color.parseColor("#3A3A3A") else Color.parseColor("#E3E6EA")

    // One definition of "safe / caution / danger" used everywhere (status card, risk banner, ticks, warning banners),
    // instead of the same hex codes repeated per screen. Accessibility mode keeps the danger colour readable on black.
    // Also the exact colours the explainer diagram's risk meter uses, on purpose.
    val safe get() = Color.parseColor("#2E7D32")
    val caution get() = Color.parseColor("#EF6C00")
    val danger get() = if (accessible) Color.parseColor("#FF8A80") else Color.parseColor("#C62828")
    fun statusColor(level: com.callguard.core.RiskLevel) = when (level) {
        com.callguard.core.RiskLevel.HIGH -> danger
        com.callguard.core.RiskLevel.MEDIUM -> caution
        com.callguard.core.RiskLevel.LOW -> safe
    }

    fun text(sp: Float, bold: Boolean = false, muted: Boolean = false) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale)
        setTextColor(if (muted) fgMuted else fg); setPadding(0, (8 * scale).toInt(), 0, (8 * scale).toInt())
        // A heavier system weight for anything marked bold, so headings read as headings rather than just "text but darker".
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.DEFAULT
    }

    /** A rounded, bordered surface (the same treatment the explainer diagram's panels use) for a card-like container. */
    fun cardDrawable(radius: Float = 20f * scale): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(cardBg)
        setStroke((1.5f * scale).toInt().coerceAtLeast(1), cardBorder)
    }

    private fun rippleBackground(fill: Int, radius: Float, borderColor: Int? = null): Drawable {
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            borderColor?.let { setStroke((1.5f * scale).toInt().coerceAtLeast(1), it) }
        }
        val mask = GradientDrawable().apply { this.shape = GradientDrawable.RECTANGLE; cornerRadius = radius; setColor(Color.WHITE) }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(46, 0, 0, 0)), shape, mask)
    }

    private fun tint(icon: Int, color: Int) = ContextCompat.getDrawable(ctx, icon)?.mutate()?.apply {
        setTint(color)
        val s = (22 * scale).toInt()
        setBounds(0, 0, s, s)
    }

    /** The 8/16/24/32 spacing system, scaled for accessibility mode like everything else. */
    fun dp(n: Int) = (n * scale).toInt()

    /** Adds the status bar's own height as extra top padding, so a page's title never sits under the clock/icons
     *  (Android 15's edge-to-edge default draws content behind the status bar unless a view accounts for it itself). */
    fun avoidStatusBar(view: android.view.View, extra: Int = 0) {
        val basePadding = view.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, basePadding + bars.top + extra, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    fun button(label: String, icon: Int? = null, onClick: () -> Unit) = Button(ctx).apply {
        text = label; setOnClickListener { onClick() }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale); setTextColor(fg)
        isAllCaps = false // sentence case: the platform Button style shouts in caps by default
        background = rippleBackground(buttonBg, 16f * scale, buttonBorder)
        minimumHeight = (48 * scale).toInt()
        icon?.let { setCompoundDrawables(tint(it, fg), null, null, null); compoundDrawablePadding = (12 * scale).toInt() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = (8 * scale).toInt() }
        contentDescription = label
    }

    /** The one main action on a screen: larger, filled with the accent colour. Use sparingly — one per screen. */
    fun primary(label: String, icon: Int? = null, onClick: () -> Unit) = Button(ctx).apply {
        text = label; setOnClickListener { onClick() }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * scale); setTextColor(if (accessible) Color.BLACK else Color.WHITE)
        isAllCaps = false
        background = rippleBackground(accent, 16f * scale)
        minimumHeight = (64 * scale).toInt()
        icon?.let { setCompoundDrawables(tint(it, if (accessible) Color.BLACK else Color.WHITE), null, null, null); compoundDrawablePadding = (14 * scale).toInt() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = (16 * scale).toInt() }
        contentDescription = label
    }

    /** A destructive action, named but not shouted: outlined in the danger colour, never a solid filled block. */
    fun dangerButton(label: String, onClick: () -> Unit) = Button(ctx).apply {
        text = label; setOnClickListener { onClick() }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale); setTextColor(danger)
        isAllCaps = false
        background = rippleBackground(if (accessible) Color.BLACK else Color.WHITE, 16f * scale, danger)
        minimumHeight = (48 * scale).toInt()
        layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = (8 * scale).toInt() }
        contentDescription = label
    }

    /** A plain tap ripple with no fill or border, for list rows that sit directly on the page background. */
    fun rowRipple(): Drawable = RippleDrawable(ColorStateList.valueOf(Color.argb(28, 0, 0, 0)), null, null)

    internal fun tintedIcon(icon: Int, color: Int = fgMuted, size: Int = 22) = android.widget.ImageView(ctx).apply {
        setImageDrawable(ContextCompat.getDrawable(ctx, icon)?.mutate()?.apply { setTint(color) })
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }
}
