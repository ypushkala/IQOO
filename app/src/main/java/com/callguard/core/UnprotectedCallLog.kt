package com.callguard.core

/**
 * A quiet count of unknown-number calls the caller-ID service saw while protection was off, so Home can nudge "turn on now"
 * instead of staying silent. Holds only timestamps, capped, never the numbers.
 */
object UnprotectedCallLog {
    const val MAX = 50

    fun record(stored: String, nowMs: Long): String = (parseAll(stored) + nowMs).takeLast(MAX).joinToString(",")
    fun parseAll(stored: String): List<Long> = stored.split(',').mapNotNull { it.trim().toLongOrNull() }
    fun count(stored: String): Int = parseAll(stored).size
    fun clear(): String = ""
}
