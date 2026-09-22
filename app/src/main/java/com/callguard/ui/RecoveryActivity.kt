package com.callguard.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.alert.AppPrefs
import com.callguard.core.RecoveryPlan
import com.callguard.core.Situation
import com.callguard.core.StepAction
import com.callguard.core.Ui
import com.callguard.core.UiStrings

/** "I already shared something" checklist. Ticks live only on screen. The bank number is the user's own entry. */
class RecoveryActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private val lang get() = prefs.screenLanguage
    private val situations = linkedSetOf<Situation>()
    private val done = HashSet<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        intent.getStringArrayExtra(EXTRA_SITUATIONS)?.forEach { n -> runCatching { situations += Situation.valueOf(n) } }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }.also { theme.avoidStatusBar(it) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        build()
    }

    private fun build() {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.RECOVERY_TITLE, lang); setTextColor(theme.fg) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.RECOVERY_INTRO, lang) })
        // The single most urgent action, visible immediately — a HIGH-risk / money-sent moment should not require
        // ticking checkboxes first. It reflects what the call itself already suggested happened.
        val bank0 = prefs.bankHelpline
        RecoveryPlan.steps(situations).firstOrNull { it.action != StepAction.NONE }?.let { top ->
            root.addView(theme.text(15f, bold = true).apply { text = UiStrings.get(Ui.URGENT_FIRST, lang) })
            root.addView(theme.primary(RecoveryPlan.stepText(top.id, lang, bank0.isNotEmpty())) {
                when (top.action) {
                    StepAction.CALL_BANK -> if (bank0.isNotEmpty()) dial(bank0) else askBank()
                    StepAction.CALL_1930 -> dial(RecoveryPlan.HELPLINE)
                    StepAction.OPEN_PORTAL -> open(Intent(Intent.ACTION_VIEW, Uri.parse(RecoveryPlan.PORTAL_URL)))
                    StepAction.OPEN_APP_SETTINGS -> open(Intent(Settings.ACTION_APPLICATION_SETTINGS))
                    StepAction.NONE -> {}
                }
            })
        }
        root.addView(theme.text(17f, bold = true).apply { text = UiStrings.get(Ui.RECOVERY_WHAT, lang); setPadding(0, 24, 0, 8) })
        for (s in Situation.values()) root.addView(CheckBox(this).apply {
            text = RecoveryPlan.situationLabel(s, lang); textSize = 16f; setTextColor(theme.fg); isChecked = s in situations
            setOnCheckedChangeListener { _, on -> if (on) situations += s else situations -= s; done.clear(); build() }
        })
        val bank = prefs.bankHelpline
        val steps = RecoveryPlan.steps(situations)
        steps.forEachIndexed { i, step ->
            val label = "${i + 1}. " + RecoveryPlan.stepText(step.id, lang, bank.isNotEmpty())
            root.addView(CheckBox(this).apply {
                text = label; textSize = 16f; setTextColor(theme.fg); isChecked = i in done
                setOnCheckedChangeListener { _, on -> if (on) done += i else done -= i }
            })
            when (step.action) {
                StepAction.CALL_BANK -> if (bank.isNotEmpty()) root.addView(theme.button("${UiStrings.get(Ui.BANK_NUMBER_SET, lang)}: $bank") { dial(bank) })
                    else root.addView(theme.button(UiStrings.get(Ui.BANK_NUMBER, lang)) { askBank() })
                StepAction.CALL_1930 -> root.addView(theme.button(UiStrings.get(Ui.HELPLINE, lang), com.callguard.R.drawable.ic_phone) { dial(RecoveryPlan.HELPLINE) })
                StepAction.OPEN_PORTAL -> root.addView(theme.button("cybercrime.gov.in") { open(Intent(Intent.ACTION_VIEW, Uri.parse(RecoveryPlan.PORTAL_URL))) })
                StepAction.OPEN_APP_SETTINGS -> root.addView(theme.button("Apps") { open(Intent(Settings.ACTION_APPLICATION_SETTINGS)) })
                StepAction.NONE -> {}
            }
        }
        if (bank.isNotEmpty()) root.addView(theme.button(UiStrings.get(Ui.BANK_NUMBER, lang)) { askBank() })
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }

    // Dialer opens with the number filled in; the user presses call.
    private fun dial(n: String) = open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")))
    private fun open(i: Intent) { try { startActivity(i) } catch (_: ActivityNotFoundException) { } }

    private fun askBank() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_PHONE; setText(prefs.bankHelpline) }
        AlertDialog.Builder(this).setTitle(UiStrings.get(Ui.BANK_NUMBER, lang)).setView(input)
            .setPositiveButton(UiStrings.get(Ui.DONE, lang)) { _, _ -> prefs.bankHelpline = input.text.toString(); build() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    companion object { const val EXTRA_SITUATIONS = "situations" }
}
