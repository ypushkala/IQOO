package com.callguard.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.CallGuardState
import com.callguard.alert.AppPrefs
import com.callguard.caller.Blocklist
import com.callguard.caller.FeedbackStore
import com.callguard.core.BlockPolicy
import com.callguard.core.FamilyAlert
import com.callguard.core.FeedbackEntry
import com.callguard.core.RecoveryPlan
import com.callguard.core.RiskLevel
import com.callguard.core.Ui
import com.callguard.core.UiStrings
import com.callguard.core.Verdict

/**
 * "What next", one step at a time, the same pattern as Get Ready — instead of the flat list of buttons a MEDIUM/HIGH
 * summary used to show. Every step here can be skipped; nothing here is required. Reuses the existing screens
 * (Recovery, family composer) for anything that needs its own fuller screen; the rest is answered inline.
 */
class FollowUpActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private val lang get() = prefs.screenLanguage
    private var shown = 0
    private var feedbackAnswered = false
    private val summary get() = CallGuardState.state.summary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        build()
    }

    override fun onResume() { super.onResume(); build() }

    private val steps = listOf("FEEDBACK", "RECOVERY", "BLOCK", "FAMILY")

    private fun build() {
        val sum = summary
        if (sum == null) { finish(); return } // nothing to follow up on any more
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.FU_TITLE, lang); setTextColor(theme.accent) })
        if (shown >= steps.size) { finishScreen(); return }
        when (steps[shown]) {
            "FEEDBACK" -> feedbackStep(sum)
            "RECOVERY" -> recoveryStep(sum)
            "BLOCK" -> blockStep(sum)
            "FAMILY" -> familyStep(sum)
        }
    }

    private fun advance() { shown++; build() }

    private fun feedbackStep(sum: com.callguard.core.CallSummary) {
        if (feedbackAnswered) { advance(); return }
        root.addView(theme.text(20f, bold = true).apply { text = UiStrings.get(Ui.FU_FEEDBACK_STEP, lang); setPadding(0, 24, 0, 8) })
        fun answer(v: Verdict) { FeedbackStore(this).add(FeedbackEntry.from(sum, v, System.currentTimeMillis())); feedbackAnswered = true; advance() }
        root.addView(theme.primary(UiStrings.get(Ui.FEEDBACK_YES, lang)) { answer(Verdict.SCAM) })
        root.addView(theme.button(UiStrings.get(Ui.FEEDBACK_NO, lang)) { answer(Verdict.FINE) })
        root.addView(theme.button(UiStrings.get(Ui.FEEDBACK_UNSURE, lang)) { answer(Verdict.UNSURE) })
        root.addView(theme.button(UiStrings.get(Ui.FU_SKIP, lang)) { advance() })
    }

    private fun recoveryStep(sum: com.callguard.core.CallSummary) {
        root.addView(theme.text(20f, bold = true).apply { text = UiStrings.get(Ui.FU_RECOVERY_STEP, lang); setPadding(0, 24, 0, 8) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.FU_RECOVERY_WHY, lang) })
        val tactics = sum.findings.map { it.tactic }.toSet()
        root.addView(theme.primary(UiStrings.get(Ui.RECOVERY_BUTTON, lang)) {
            startActivity(Intent(this, RecoveryActivity::class.java).putExtra(RecoveryActivity.EXTRA_SITUATIONS, RecoveryPlan.likelySituations(tactics).map { it.name }.toTypedArray()))
            advance()
        })
        root.addView(theme.button(UiStrings.get(Ui.FU_SKIP, lang)) { advance() })
    }

    private fun blockStep(sum: com.callguard.core.CallSummary) {
        val h = sum.numberHash
        if (!BlockPolicy.canOfferBlock(h, null)) { advance(); return }
        root.addView(theme.text(20f, bold = true).apply { text = UiStrings.get(Ui.FU_BLOCK_STEP, lang); setPadding(0, 24, 0, 8) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.FU_BLOCK_WHY, lang) })
        val list = Blocklist(this)
        root.addView(theme.primary(UiStrings.get(if (h != null && list.contains(h)) Ui.UNBLOCK_NUMBER else Ui.BLOCK_NUMBER, lang)) {
            if (h != null) { if (list.contains(h)) list.remove(h) else list.add(h) }
            advance()
        })
        root.addView(theme.button(UiStrings.get(Ui.FU_SKIP, lang)) { advance() })
    }

    private fun familyStep(sum: com.callguard.core.CallSummary) {
        if (sum.level < RiskLevel.HIGH) { advance(); return } // not worth a family step below HIGH
        root.addView(theme.text(20f, bold = true).apply { text = UiStrings.get(Ui.FU_FAMILY_STEP, lang); setPadding(0, 24, 0, 8) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.FU_FAMILY_WHY, lang) })
        root.addView(theme.primary(UiStrings.get(Ui.FAMILY_ALERT, lang)) {
            val contacts = prefs.familyContacts
            if (contacts.isEmpty()) startActivity(Intent(this, FamilyAlertActivity::class.java))
            else {
                val tactics = sum.findings.map { it.tactic }
                val body = FamilyAlert.message(sum.level, tactics, sum.callerSummary, prefs.familyMessageLanguage)
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(contacts[0].number))).putExtra("sms_body", body))
            }
            advance()
        })
        root.addView(theme.button(UiStrings.get(Ui.FU_SKIP, lang)) { advance() })
    }

    private fun finishScreen() {
        root.addView(theme.text(20f, bold = true).apply { text = UiStrings.get(Ui.FU_DONE, lang); setPadding(0, 24, 0, 8) })
        root.addView(theme.primary(UiStrings.get(Ui.DONE, lang)) { finish() })
    }
}
