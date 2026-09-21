package com.callguard.caller

import com.callguard.core.NumberAssessment

/** The assessment of the call in progress. In memory only, cleared when the call ends. */
object CallerRegistry {
    private const val TTL_MS = 3 * 60 * 60 * 1000L
    @Volatile private var current: NumberAssessment? = null
    @Volatile private var at = 0L

    fun set(a: NumberAssessment) { current = a; at = System.currentTimeMillis() }
    fun get(): NumberAssessment? = current?.takeIf { System.currentTimeMillis() - at < TTL_MS }
    fun clear() { current = null }
}
