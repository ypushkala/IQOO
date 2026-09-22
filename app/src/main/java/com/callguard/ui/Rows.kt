package com.callguard.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.callguard.R

/**
 * A small settings-list component system, so different kinds of controls look different: a page that opens
 * elsewhere is a row with a chevron, an on/off preference is a switch, a fact is plain text, an explicit action
 * is a button. None of it is a big rectangular button standing in for all four. Modelled on the plain list rows
 * of stock Android Settings — no card wrapping, a thin divider between rows, generous section spacing.
 */
object Rows {
    /** A section label ("Protection", "Privacy"): sentence case, quiet, dark rather than coloured — a section
     *  header is structure, not a place to spend the app's one accent colour. */
    fun sectionHeader(ctx: Context, theme: UiTheme, title: String) = theme.text(13.5f, bold = true, muted = true).apply {
        text = title
        setPadding(theme.dp(4), theme.dp(32), theme.dp(4), theme.dp(8))
    }

    /** A thin, inset divider between two rows in the same section — not between a header and its first row. */
    private fun divider(ctx: Context, theme: UiTheme) = View(ctx).apply {
        setBackgroundColor(theme.cardBorder)
        layoutParams = LinearLayout.LayoutParams(-1, theme.dp(1)).also { it.marginStart = theme.dp(4) }
    }

    /** A row that opens another screen or a chooser: title, optional one-line description, a chevron. */
    fun navRow(ctx: Context, theme: UiTheme, title: String, description: String? = null, trailing: String? = null, icon: Int? = null, onClick: () -> Unit) =
        row(ctx, theme, title, description, icon) {
            trailing?.let { addView(theme.text(13.5f, muted = true).apply { text = it; setPadding(0, 0, theme.dp(8), 0) }) }
            addView(theme.tintedIcon(R.drawable.ic_chevron_right, theme.fgMuted, 18))
            setOnClickListener { onClick() }
            foreground = theme.rowRipple()
            isClickable = true; isFocusable = true
        }

    /** An on/off preference that takes effect immediately — no separate "save" step, matching every other switch on the phone. */
    fun switchRow(ctx: Context, theme: UiTheme, title: String, description: String? = null, checked: Boolean, onToggle: (Boolean) -> Unit) =
        row(ctx, theme, title, description, icon = null) {
            addView(Switch(ctx).apply {
                isChecked = checked
                thumbTintList = android.content.res.ColorStateList.valueOf(if (checked) theme.accent else Color.parseColor("#B9BFC6"))
                setOnCheckedChangeListener { _, on -> onToggle(on) }
            })
        }

    /** A fact about the system's current state — never tappable, never styled like an action. */
    fun statusRow(ctx: Context, theme: UiTheme, title: String, status: String, statusColor: Int = theme.fgMuted) =
        row(ctx, theme, title, description = null, icon = null) {
            addView(theme.text(14f, bold = true).apply { text = status; setTextColor(statusColor); setPadding(0, 0, 0, 0) })
        }

    /** Plain title + description, for information that is not a control at all (the privacy facts). */
    fun infoRow(ctx: Context, theme: UiTheme, title: String, description: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(theme.dp(4), theme.dp(10), theme.dp(4), theme.dp(10))
        addView(theme.text(15f, bold = true).apply { text = title; setPadding(0, 0, 0, theme.dp(2)) })
        addView(theme.text(14f, muted = true).apply { text = description; setPadding(0, 0, 0, 0) })
    }

    private fun row(ctx: Context, theme: UiTheme, title: String, description: String?, icon: Int?, trailing: LinearLayout.() -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = theme.dp(56)
        setPadding(theme.dp(4), theme.dp(10), theme.dp(4), theme.dp(10))
        icon?.let { addView(theme.tintedIcon(it, theme.fg, 22).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = theme.dp(16) } ) }
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(theme.text(16f).apply { text = title; setPadding(0, 0, 0, if (description != null) theme.dp(2) else 0) })
            description?.let { addView(theme.text(13.5f, muted = true).apply { text = it; setPadding(0, 0, theme.dp(12), 0) }) }
        })
        trailing()
    }

    /** Lays out one section: header, then its rows with a thin divider between consecutive rows (not before the first). */
    fun section(ctx: Context, theme: UiTheme, container: LinearLayout, title: String, rows: List<View>) {
        container.addView(sectionHeader(ctx, theme, title))
        rows.forEachIndexed { i, r -> if (i > 0) container.addView(divider(ctx, theme)); container.addView(r) }
    }

    /**
     * A small dot and a word — the quiet way to show a protection/risk state, never a full-width colour block.
     * The colour is the only signal; everything else (size, weight) stays the same across states.
     */
    fun statusIndicator(ctx: Context, theme: UiTheme, color: Int, label: String, sublabel: String? = null) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color) }
            layoutParams = LinearLayout.LayoutParams(theme.dp(9), theme.dp(9)).also { it.marginEnd = theme.dp(9) }
        })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(theme.text(15f, bold = true).apply { text = label; setTextColor(color); setPadding(0, 0, 0, 0) })
            sublabel?.let { addView(theme.text(13f, muted = true).apply { text = it; setPadding(0, 0, 0, 0) }) }
        })
    }

    /**
     * One past call in the history list: a small risk-coloured dot (not a coloured card), when it happened and
     * who/why on a second line, and the risk word set small and quiet at the end — a fact to scan, not a banner.
     */
    fun historyRow(ctx: Context, theme: UiTheme, color: Int, dateTime: String, riskWord: String, detail: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(theme.dp(4), theme.dp(12), theme.dp(4), theme.dp(12))
        addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color) }
            layoutParams = LinearLayout.LayoutParams(theme.dp(8), theme.dp(8)).also { it.marginEnd = theme.dp(12); it.topMargin = theme.dp(6) }
        })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(theme.text(15f, bold = true).apply { text = dateTime; setPadding(0, 0, theme.dp(8), theme.dp(2)) })
            addView(theme.text(13.5f, muted = true).apply { text = detail; setPadding(0, 0, theme.dp(8), 0) })
        })
        addView(theme.text(13f, bold = true).apply { text = riskWord; setTextColor(color) })
    }

    /**
     * Technical state, kept but demoted: a single tappable row that expands into compact label/value lines —
     * not a stack of loose full-size text views competing with the rest of the screen.
     */
    fun diagnosticsPanel(ctx: Context, theme: UiTheme, title: String, lines: List<Pair<String, String>>): View {
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(theme.dp(4), theme.dp(4), theme.dp(4), theme.dp(4))
            // Stacked, not a two-column grid: several of these values are already full sentences ("Gemma: warming
            // up…"), and a fixed-width label column either truncates the label or gets squeezed to nothing by a
            // long value. A label line (when there is one) above a full-width value line never has that problem.
            for ((k, v) in lines) addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, theme.dp(4), 0, theme.dp(4))
                if (k.isNotEmpty()) addView(theme.text(11.5f, muted = true).apply { text = k; setPadding(0, 0, 0, 0) })
                addView(theme.text(12.5f).apply { text = v; setPadding(0, 0, 0, 0) })
            })
        }
        lateinit var chevron: android.widget.ImageView
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = theme.dp(44)
            setPadding(theme.dp(4), theme.dp(6), theme.dp(4), theme.dp(6))
            addView(theme.text(13f, muted = true).apply { text = title; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            chevron = theme.tintedIcon(R.drawable.ic_chevron_right, theme.fgMuted, 16)
            addView(chevron)
            foreground = theme.rowRipple(); isClickable = true; isFocusable = true
            setOnClickListener {
                body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                chevron.rotation = if (body.visibility == View.VISIBLE) 90f else 0f
            }
        }
        return LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(header); addView(body) }
    }
}
