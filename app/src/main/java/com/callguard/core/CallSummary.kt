package com.callguard.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One scam tactic seen during the call. Holds short matched phrases and reasons, never the transcript. */
data class TacticFinding(
    val tactic: Tactic,
    val level: RiskLevel,
    /** Milliseconds after the call started when this tactic first reached MEDIUM or HIGH. */
    val firstAtMs: Long,
    val examples: List<String>,
    val reasons: List<String>,
)

/** What the user sees after a call: plain language, no audio and no transcript. */
data class CallSummary(
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val level: RiskLevel,
    val findings: List<TacticFinding>,
    /** Weaker things heard (urgency, prize talk) that did not reach MEDIUM on their own. */
    val minorNotes: List<Tactic>,
    /** A safety warning was heard ("never share your OTP"). It is good news, not a finding. */
    val heardSafetyWarning: Boolean,
    val alertCount: Int,
    val callerSummary: String?,
    val advice: List<String>,
    /** Salted hash of the caller's number (never the number itself), used only to remember the user's feedback about it. */
    val numberHash: String? = null,
) {
    val headline: String get() = Strings.headline(level, Lang.EN)

    /** Shown in the app and read aloud, in English or Hindi. */
    fun plainText(zone: ZoneId = ZoneId.systemDefault(), lang: Lang = Lang.EN): String = buildString {
        appendLine(Strings.headline(level, lang))
        appendLine(Strings.callAt(formatTime(startedAtEpochMs, zone, lang), formatDuration(durationMs), lang))
        callerSummary?.let { appendLine(Strings.caller(it, lang)) }
        if (findings.isNotEmpty()) {
            appendLine()
            appendLine(Strings.tacticsHeader(lang))
            for (f in findings) {
                append(Strings.findingLine(Strings.tactic(f.tactic, lang), Strings.levelWord(f.level, lang), formatDuration(f.firstAtMs), lang))
                if (f.examples.isNotEmpty()) append(Strings.heard(f.examples.joinToString(", ") { "“$it”" }, lang))
                // The model's reason is written in English, so it is only shown in the English summary.
                if (lang == Lang.EN && f.reasons.isNotEmpty()) append(". " + f.reasons.joinToString("; ").trimEnd('.') + ".")
                appendLine()
            }
        }
        if (minorNotes.isNotEmpty()) appendLine(Strings.alsoHeard(minorNotes.joinToString(", ") { Strings.tactic(it, lang) }, lang))
        if (heardSafetyWarning) appendLine(Strings.safetyWarning(lang))
        if (alertCount > 0) appendLine(Strings.warnedYou(alertCount, lang))
        val adv = Strings.adviceFor(findings.map { it.tactic }.toSet(), level, lang)
        if (adv.isNotEmpty()) {
            appendLine()
            appendLine(Strings.whatToDo(lang))
            adv.forEach { appendLine("• $it") }
        }
    }.trim()

    /** For the share sheet. Contains the caller number (the user is sharing their own report) but never audio or transcript. */
    fun reportText(zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("Suspected scam call report (made on my phone by CallGuard)")
        appendLine("When: ${formatTime(startedAtEpochMs, zone)}, duration ${formatDuration(durationMs)}")
        appendLine("Caller: ${callerSummary ?: "number not available"}")
        appendLine("Risk assessed: ${level.name}")
        if (findings.isNotEmpty()) {
            appendLine("Tactics observed:")
            findings.forEach { appendLine("- ${it.tactic.label} (at ${formatDuration(it.firstAtMs)})") }
        }
        appendLine("No audio or transcript is included.")
        append("Report to the National Cyber Crime Helpline 1930 or cybercrime.gov.in")
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }
        fun formatTime(epochMs: Long, zone: ZoneId, lang: Lang = Lang.EN): String {
            val t = Instant.ofEpochMilli(epochMs).atZone(zone)
            return if (lang == Lang.EN) DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).format(t)
            else "${t.dayOfMonth} ${Strings.months(lang)[t.monthValue - 1]} ${t.year}, %02d:%02d".format(t.hour, t.minute)
        }
    }
}

/**
 * Collects what [RiskEngine] saw during one call and turns it into a [CallSummary]. It keeps only
 * tactic names, levels, times and short matched phrases; the transcript itself is never retained.
 */
class CallTimeline(private val epochClock: () -> Long = System::currentTimeMillis) {
    private class Acc(val firstAtMs: Long) {
        var level = RiskLevel.MEDIUM
        val examples = LinkedHashSet<String>()
        val reasons = LinkedHashSet<String>()
    }

    var active = false
        private set
    private var startedAtEpoch = 0L
    private var startedAtMs = 0L
    private var records = 0
    private var alerts = 0
    private var maxLevel = RiskLevel.LOW
    private var sawAdvisory = false
    private val byTactic = LinkedHashMap<Tactic, Acc>()
    private val minor = LinkedHashSet<Tactic>()

    fun start(nowMs: Long) {
        reset(); active = true; startedAtMs = nowMs; startedAtEpoch = epochClock()
    }

    fun reset() {
        active = false; records = 0; alerts = 0; maxLevel = RiskLevel.LOW; sawAdvisory = false; byTactic.clear(); minor.clear()
    }

    fun hasData() = records > 0

    fun record(nowMs: Long, result: DetectionResult, alertsFired: Int) {
        if (!active) return
        records++
        alerts += alertsFired
        if (result.level > maxLevel) maxLevel = result.level
        for (s in result.signals) {
            when {
                s.advisory -> sawAdvisory = true
                s.level >= RiskLevel.MEDIUM -> {
                    val acc = byTactic.getOrPut(s.tactic) { Acc(nowMs - startedAtMs) }
                    if (s.level > acc.level) acc.level = s.level
                    // "heard" phrases come from the rules; the model's label is not something anyone said (its reason is kept below)
                    if (s.source != "gemma" && s.matched.isNotBlank() && acc.examples.size < 3) acc.examples += s.matched.take(40)
                    if (s.reason.isNotBlank() && acc.reasons.size < 2) acc.reasons += s.reason.take(120)
                }
                s.tactic in MINOR -> minor += s.tactic
            }
        }
    }

    /** Ends the session. Null if nothing was ever analysed (no speech), so there is nothing to summarise. */
    fun finish(nowMs: Long, caller: NumberAssessment?): CallSummary? {
        if (!active) return null
        active = false
        if (records == 0) return null
        val findings = byTactic.map { (t, a) -> TacticFinding(t, a.level, a.firstAtMs, a.examples.toList(), a.reasons.toList()) }
            .sortedBy { it.firstAtMs }
        val callerLine = caller?.takeIf { it.displayNumber.isNotBlank() }?.summary
        return CallSummary(
            startedAtEpochMs = startedAtEpoch,
            durationMs = nowMs - startedAtMs,
            level = maxLevel,
            findings = findings,
            minorNotes = minor.filter { m -> findings.none { it.tactic == m } },
            heardSafetyWarning = sawAdvisory,
            alertCount = alerts,
            callerSummary = callerLine,
            advice = adviceFor(findings.map { it.tactic }.toSet(), maxLevel),
            numberHash = caller?.numberHash,
        )
    }

    private companion object { val MINOR = setOf(Tactic.URGENCY, Tactic.PAYMENT_LURE, Tactic.SECRECY) }
}

/** Plain-language next steps, keyed to what was actually seen. */
fun adviceFor(tactics: Set<Tactic>, level: RiskLevel, lang: Lang = Lang.EN): List<String> = Strings.adviceFor(tactics, level, lang)
