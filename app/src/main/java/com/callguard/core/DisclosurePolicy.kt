package com.callguard.core

/**
 * The short beep that tells the other party the call is being analysed on this phone (like the recording tone on some phones).
 * On by default; the user can turn it off. It never plays outside a real call, and never over a spoken warning.
 */
object DisclosurePolicy {
    const val INTERVAL_MS = 15_000L
    const val BEEP_MS = 250

    /** [lastBeepMs] is 0 before the first beep, so the first one plays as soon as analysis starts. */
    fun due(enabled: Boolean, analysingACall: Boolean, warningSpeaking: Boolean, lastBeepMs: Long, nowMs: Long): Boolean =
        enabled && analysingACall && !warningSpeaking && (lastBeepMs == 0L || nowMs - lastBeepMs >= INTERVAL_MS)
}
