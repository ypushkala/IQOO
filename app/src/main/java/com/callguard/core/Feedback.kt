package com.callguard.core

enum class Verdict { SCAM, FINE, UNSURE }

/** One answer to "was this a scam?". Contains no audio, no transcript, no phone number: only names, levels and a salted hash. */
data class FeedbackEntry(
    val atEpochMs: Long,
    val level: RiskLevel,
    val tactics: List<String>,
    val numberHash: String?,
    val verdict: Verdict,
) {
    companion object {
        fun from(s: CallSummary, verdict: Verdict, nowMs: Long) =
            FeedbackEntry(nowMs, s.level, s.findings.map { it.tactic.name }, s.numberHash, verdict)
    }
}

/** A tiny, forgiving text format (one entry per line) so nothing depends on a JSON library. */
object FeedbackCodec {
    private const val SEP = '|'

    fun encode(e: FeedbackEntry) = listOf(e.atEpochMs, e.level.name, e.tactics.joinToString(","), e.numberHash.orEmpty(), e.verdict.name).joinToString(SEP.toString())

    /** Returns null for any malformed line instead of throwing. */
    fun decode(line: String): FeedbackEntry? {
        val p = line.split(SEP)
        if (p.size != 5) return null
        return runCatching {
            FeedbackEntry(p[0].toLong(), RiskLevel.valueOf(p[1]), p[2].split(',').filter { it.isNotEmpty() }, p[3].ifEmpty { null }, Verdict.valueOf(p[4]))
        }.getOrNull()
    }

    fun decodeAll(text: String) = text.lineSequence().mapNotNull { decode(it) }.toList()
    fun encodeAll(entries: List<FeedbackEntry>) = entries.joinToString("\n") { encode(it) }
}

object FeedbackLog {
    const val MAX_ENTRIES = 200

    fun append(entries: List<FeedbackEntry>, e: FeedbackEntry): List<FeedbackEntry> = (entries + e).takeLast(MAX_ENTRIES)

    /**
     * Numbers whose most recent answer was "fine". Trust is per number and only quietens NUMBER-based warnings (the
     * heads-up and the identity check). It never switches off a content rule: a trusted number can be spoofed or taken over.
     */
    fun trustedHashes(entries: List<FeedbackEntry>): Set<String> =
        entries.filter { it.numberHash != null }.groupBy { it.numberHash!! }
            .filterValues { it.maxByOrNull { e -> e.atEpochMs }?.verdict == Verdict.FINE }.keys

    fun counts(entries: List<FeedbackEntry>): Map<Verdict, Int> = Verdict.values().associateWith { v -> entries.count { it.verdict == v } }
}
