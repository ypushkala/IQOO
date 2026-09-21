package com.callguard.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisclosurePolicyTest {
    private fun due(enabled: Boolean = true, call: Boolean = true, speaking: Boolean = false, last: Long = 0, now: Long = 100_000) =
        DisclosurePolicy.due(enabled, call, speaking, last, now)

    @Test fun firstBeepPlaysImmediately() = assertTrue(due(last = 0, now = 1))
    @Test fun thenEveryFifteenSeconds() {
        assertFalse(due(last = 100_000, now = 110_000))
        assertTrue(due(last = 100_000, now = 115_000))
    }
    @Test fun neverWhenTurnedOffOutsideACallOrOverASpokenWarning() {
        assertFalse(due(enabled = false)); assertFalse(due(call = false)); assertFalse(due(speaking = true))
    }
    @Test fun beepIsShort() = assertTrue(DisclosurePolicy.BEEP_MS <= 300)
}
