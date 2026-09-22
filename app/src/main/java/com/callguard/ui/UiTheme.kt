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
import android.widget.CheckBox
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
    val bg get() = if (accessible) Color.BLACK else Color.parseColor("#F7F6F3")
    val fg get() = if (accessible) Color.WHITE else Color.parseColor("#1B1C1E")
    val fgMuted get() = if (accessible) Color.parseColor("#CCCCCC") else Color.parseColor("#6B7076")
    // A restrained, slightly muted mid-blue — used sparingly (one primary button per screen, the active nav tab,
    // the progress bar), never as the default colour for titles, headings or section labels.
    val accent get() = if (accessible) Color.YELLOW else Color.parseColor("#33587E")
    val buttonBg get() = if (accessible) Color.parseColor("#FFEB3B") else Color.parseColor("#EEEDE9")
    val buttonBorder get() = if (accessible) Color.parseColor("#FFEB3B") else Color.parseColor("#DAD8D2")
    val cardBg get() = if (accessible) Color.parseColor("#222222") else Color.WHITE
    val cardBorder get() = if (accessible) Color.parseColor("#3A3A3A") else Color.parseColor("#E2E0DA")

    // One definition of "safe / caution / danger" used everywhere, instead of the same hex codes repeated per
    // screen. Muted on purpose — these are a signal (a small dot, a status word), never a full-width colour block.
    // Accessibility mode keeps them readable on black.
    val safe get() = if (accessible) Color.parseColor("#7CB784") else Color.parseColor("#4B7A55")
    val caution get() = if (accessible) Color.parseColor("#E4B65C") else Color.parseColor("#9A6A2A")
    val danger get() = if (accessible) Color.parseColor("#FF8A80") else Color.parseColor("#A23B34")
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

    /** A card tinted a soft, translucent version of one colour, with a matching border — a quieter alternative
     *  to a solid full-bleed fill for the handful of places a status needs to be more than a small dot (the
     *  selected item in a list of options, the simulated warning in Practice). */
    fun tintedCardDrawable(color: Int, radius: Float = 16f * scale): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)))
        setStroke((1.5f * scale).toInt().coerceAtLeast(1), color)
    }

    /** A quiet "this one is chosen" surface for a list of options (a language, a scope): a soft tint of the
     *  accent colour and an accent border. Never the loud filled `primary()` treatment — several of these can
     *  sit on screen at once, and only one is ever the real selection, so the signal has to be a tint and a
     *  small check mark, not a competing full-colour button. */
    fun selectedCardDrawable(radius: Float = 16f * scale): Drawable = tintedCardDrawable(accent, radius)

    // A translucent version of the accent colour for a Switch's ON/OFF track, so a switch reads as part of
    // this palette instead of the OS default accent (usually teal or purple, depending on the phone).
    val switchTrackOn get() = Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent))
    val switchTrackOff get() = Color.argb(90, Color.red(fgMuted), Color.green(fgMuted), Color.blue(fgMuted))

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

    /** A checkbox tinted to this palette instead of the OS default, for the small number of genuine
     *  multi-select lists (recovery steps, missed-call tags) — same text size and colour as the rest of the page. */
    fun checkBox(label: String, checked: Boolean = false, onToggle: (Boolean) -> Unit) = CheckBox(ctx).apply {
        text = label; isChecked = checked
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale); setTextColor(fg)
        buttonTintList = ColorStateList.valueOf(accent)
        setPadding(dp(4), dp(6), dp(4), dp(6))
        setOnCheckedChangeListener { _, on -> onToggle(on) }
    }

    internal fun tintedIcon(icon: Int, color: Int = fgMuted, size: Int = 22) = android.widget.ImageView(ctx).apply {
        setImageDrawable(ContextCompat.getDrawable(ctx, icon)?.mutate()?.apply { setTint(color) })
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }
}
