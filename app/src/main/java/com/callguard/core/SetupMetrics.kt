package com.callguard.core

/**
 * How long "Get ready" took and how many in-app taps it needed, stored on the phone only, so a tester can be timed without any
 * analytics. Target: under three minutes and at most eight taps (in-app taps; system dialogs are not counted).
 */
data class SetupRun(val startedAtMs: Long, val durationMs: Long, val taps: Int, val steps: Int) {
    val meetsTarget get() = durationMs <= TARGET_MS && taps <= TARGET_TAPS
    fun encode() = "$startedAtMs|$durationMs|$taps|$steps"

    companion object {
        const val TARGET_MS = 180_000L
        const val TARGET_TAPS = 8
        const val KEEP = 10
        fun decode(s: String): SetupRun? {
            val p = s.split('|').mapNotNull { it.toLongOrNull() }
            return if (p.size == 4) SetupRun(p[0], p[1], p[2].toInt(), p[3].toInt()) else null
        }
        fun parseAll(stored: String) = stored.split('\n').mapNotNull { decode(it) }
        fun append(stored: String, r: SetupRun) = (parseAll(stored) + r).takeLast(KEEP).joinToString("\n") { it.encode() }
    }
}
