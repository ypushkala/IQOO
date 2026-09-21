package com.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFamilyAlertTest {
    private fun d(enabled: Boolean = true, consent: Boolean = true, contact: Boolean = true, perm: Boolean = true, level: RiskLevel = RiskLevel.HIGH, sent: Boolean = false) =
        AutoFamilyAlert.decide(enabled, consent, contact, perm, level, sent)

    @Test fun sendsOnlyWhenEverythingIsInPlace() = assertEquals(AutoAlertDecision.SEND, d())
    @Test fun offByDefaultNeverSends() = assertEquals(AutoAlertDecision.OFF, d(enabled = false))
    @Test fun needsConsentContactAndPermission() {
        assertEquals(AutoAlertDecision.NO_CONSENT, d(consent = false))
        assertEquals(AutoAlertDecision.NO_CONTACT, d(contact = false))
        assertEquals(AutoAlertDecision.NO_PERMISSION, d(perm = false))
    }
    @Test fun onlyHighAndOncePerCall() {
        assertEquals(AutoAlertDecision.NOT_HIGH, d(level = RiskLevel.MEDIUM))
        assertEquals(AutoAlertDecision.NOT_HIGH, d(level = RiskLevel.LOW))
        assertEquals(AutoAlertDecision.ALREADY_SENT, d(sent = true))
    }
    @Test fun sampleIsExactlyTheRealMessageAndCallerIsOptional() {
        for (l in Lang.values()) {
            assertTrue(AutoFamilyAlert.sampleMessage(l, true).contains("98xxxxxx10"))
            assertFalse(AutoFamilyAlert.sampleMessage(l, false).contains("98xxxxxx10"))
        }
    }
    @Test fun logKeepsTheLatestTwentyAndRoundTrips() {
        var s = ""
        for (i in 1..25) s = AlertLogEntry.append(s, AlertLogEntry(i.toLong(), AlertLogEntry.Outcome.SENT, "Amma"))
        val all = AlertLogEntry.parseAll(s)
        assertEquals(20, all.size); assertEquals(6L, all.first().atEpochMs); assertEquals(25L, all.last().atEpochMs)
        assertEquals(AlertLogEntry(1, AlertLogEntry.Outcome.FAILED, "A B"), AlertLogEntry.decode(AlertLogEntry(1, AlertLogEntry.Outcome.FAILED, "A|B").encode()))
        assertNull(AlertLogEntry.decode("garbage"))
    }
}
