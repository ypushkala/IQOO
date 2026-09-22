package com.callguard.core

enum class ReportTag { OTP_ASKED, THREATENED, INSTALL_APP, MONEY_ASKED, OTHER }

/** A user-initiated "this one got through" report. No audio, no transcript, no number required. Local only. */
data class MissedScamReport(val atEpochMs: Long, val tags: Set<ReportTag>, val note: String = "") {
    fun encode() = "$atEpochMs|${tags.joinToString(",") { it.name }}|${note.replace("|", " ").replace("\n", " ").take(200)}"

    companion object {
        const val MAX = 30
        fun decode(line: String): MissedScamReport? {
            val p = line.split('|', limit = 3)
            if (p.size < 2) return null
            val at = p[0].toLongOrNull() ?: return null
            val tags = p[1].split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { ReportTag.valueOf(it) }.getOrNull() }.toSet()
            return MissedScamReport(at, tags, p.getOrElse(2) { "" })
        }
        fun parseAll(stored: String): List<MissedScamReport> = stored.split('\n').mapNotNull { if (it.isBlank()) null else decode(it) }
        fun append(stored: String, r: MissedScamReport): String = (parseAll(stored) + r).takeLast(MAX).joinToString("\n") { it.encode() }
    }
}
