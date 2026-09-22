package com.callguard.core

/** One finished call, kept locally so the app remembers calls across sessions instead of only showing the last one. */
data class HistoryEntry(val atEpochMs: Long, val durationMs: Long, val level: RiskLevel, val tactics: List<Tactic>, val callerLabel: String?, val numberHash: String?) {
    fun encode(): String {
        val caller = (callerLabel ?: "").replace("|", " ").replace("\n", " ").take(120)
        return "$atEpochMs|$durationMs|${level.name}|${tactics.joinToString(",") { it.name }}|$caller|${numberHash ?: ""}"
    }

    companion object {
        const val MAX = 100
        fun decode(line: String): HistoryEntry? {
            val p = line.split('|', limit = 6)
            if (p.size < 6) return null
            val at = p[0].toLongOrNull() ?: return null
            val dur = p[1].toLongOrNull() ?: return null
            val level = runCatching { RiskLevel.valueOf(p[2]) }.getOrNull() ?: return null
            val tactics = p[3].split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { Tactic.valueOf(it) }.getOrNull() }
            return HistoryEntry(at, dur, level, tactics, p[4].ifBlank { null }, p[5].ifBlank { null })
        }
        fun parseAll(stored: String): List<HistoryEntry> = stored.split('\n').mapNotNull { if (it.isBlank()) null else decode(it) }
        fun append(stored: String, e: HistoryEntry): String = (parseAll(stored) + e).takeLast(MAX).joinToString("\n") { it.encode() }
    }
}
