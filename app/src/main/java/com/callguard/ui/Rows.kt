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
    /** A section label ("Protection", "Privacy"): sentence case, quiet, real separation from the section before it. */
    fun sectionHeader(ctx: Context, theme: UiTheme, title: String) = theme.text(13.5f, bold = true).apply {
        text = title; setTextColor(theme.accent)
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
}
