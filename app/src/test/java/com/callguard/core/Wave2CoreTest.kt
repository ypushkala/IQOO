package com.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoInternetCheckTest {
    @Test fun trueOnlyWhenNoNetworkPermissionIsDeclared() {
        assertTrue(NoInternetCheck.verified(listOf("android.permission.RECORD_AUDIO", "android.permission.READ_PHONE_STATE")))
        assertFalse(NoInternetCheck.verified(listOf("android.permission.INTERNET")))
        assertFalse(NoInternetCheck.verified(listOf("android.permission.ACCESS_NETWORK_STATE")))
        assertTrue(NoInternetCheck.verified(emptyList()))
    }
}

class AlertReasonTest {
    private fun sig(t: Tactic, lvl: RiskLevel, advisory: Boolean = false) = Signal("rules", "r", t, lvl, "x", advisory = advisory)

    @Test fun namesTheMostSevereNonAdvisoryTactic() {
        val signals = listOf(sig(Tactic.URGENCY, RiskLevel.LOW), sig(Tactic.CREDENTIAL_REQUEST, RiskLevel.HIGH), sig(Tactic.SECRECY, RiskLevel.MEDIUM, advisory = true))
        assertEquals(Tactic.CREDENTIAL_REQUEST, AlertReason.topTactic(signals))
        for (l in Lang.values()) assertTrue(AlertReason.line(signals, l)!!.isNotBlank())
    }
    @Test fun nullWhenOnlyAdvisorySignalsFired() = assertNull(AlertReason.topTactic(listOf(sig(Tactic.CREDENTIAL_REQUEST, RiskLevel.HIGH, advisory = true))))
    @Test fun nullWhenNothingFired() = assertNull(AlertReason.line(emptyList(), Lang.EN))
    @Test fun lineForTacticDirectlyMatchesTheSignalsPath() {
        for (l in Lang.values()) assertEquals(AlertReason.lineForTactic(Tactic.CREDENTIAL_REQUEST, l), AlertReason.line(listOf(sig(Tactic.CREDENTIAL_REQUEST, RiskLevel.HIGH)), l))
        assertNull(AlertReason.lineForTactic(null, Lang.EN))
    }
}

class CoachingLineTest {
    @Test fun picksTheMostUrgentTacticAndAllLanguagesHaveText() {
        for (l in Lang.values()) {
            assertTrue(CoachingLine.forTactics(setOf(Tactic.URGENCY, Tactic.REMOTE_ACCESS, Tactic.CREDENTIAL_REQUEST), l)!!.isNotBlank()) // remote access wins the priority order
            assertTrue(CoachingLine.forTactics(setOf(Tactic.PAYMENT_LURE), l)!!.isNotBlank())
        }
    }
    @Test fun nullForTacticsWithNoScriptedLine() = assertEquals(null, CoachingLine.forTactics(setOf(Tactic.OTHER, Tactic.URGENCY, Tactic.NUMBER_RISK), Lang.EN))
    @Test fun nullForAnEmptySet() = assertEquals(null, CoachingLine.forTactics(emptySet(), Lang.EN))
}

class UnprotectedCallLogTest {
    @Test fun recordsAndCounts() {
        var s = UnprotectedCallLog.clear()
        assertEquals(0, UnprotectedCallLog.count(s))
        s = UnprotectedCallLog.record(s, 100); s = UnprotectedCallLog.record(s, 200)
        assertEquals(2, UnprotectedCallLog.count(s))
        assertEquals(listOf(100L, 200L), UnprotectedCallLog.parseAll(s))
    }
    @Test fun capped() {
        var s = ""
        for (i in 1..(UnprotectedCallLog.MAX + 10)) s = UnprotectedCallLog.record(s, i.toLong())
        assertEquals(UnprotectedCallLog.MAX, UnprotectedCallLog.count(s))
    }
}

class MissedScamReportTest {
    @Test fun roundTripsAndKeepsTheLatest() {
        var s = ""
        for (i in 1..(MissedScamReport.MAX + 5)) s = MissedScamReport.append(s, MissedScamReport(i.toLong(), setOf(ReportTag.OTP_ASKED), "note|with\nbars"))
        val all = MissedScamReport.parseAll(s)
        assertEquals(MissedScamReport.MAX, all.size)
        assertEquals((MissedScamReport.MAX + 5).toLong(), all.last().atEpochMs)
        assertEquals(setOf(ReportTag.OTP_ASKED), all.last().tags)
        assertFalse(all.last().note.contains("|")); assertFalse(all.last().note.contains("\n"))
    }
    @Test fun emptyTagsAllowed() {
        val r = MissedScamReport(1, emptySet(), "")
        assertEquals(r, MissedScamReport.decode(r.encode()))
    }
    @Test fun garbageLineIsIgnored() = assertNull(MissedScamReport.decode("nonsense"))
}

class CallHistoryTest {
    private val e = HistoryEntry(1000, 45_000, RiskLevel.HIGH, listOf(Tactic.CREDENTIAL_REQUEST, Tactic.REMOTE_ACCESS), "+91 98xxxxxx10", "abc123")

    @Test fun roundTrips() = assertEquals(e, HistoryEntry.decode(e.encode()))
    @Test fun handlesNoCallerOrHash() {
        val bare = HistoryEntry(1, 2, RiskLevel.LOW, emptyList(), null, null)
        assertEquals(bare, HistoryEntry.decode(bare.encode()))
    }
    @Test fun keepsTheLatestHundred() {
        var s = ""
        for (i in 1..120) s = HistoryEntry.append(s, e.copy(atEpochMs = i.toLong()))
        val all = HistoryEntry.parseAll(s)
        assertEquals(100, all.size); assertEquals(120L, all.last().atEpochMs); assertEquals(21L, all.first().atEpochMs)
    }
    @Test fun badLineIgnored() = assertNull(HistoryEntry.decode("x|y"))
}

class PracticeScenariosTest {
    @Test fun everyScenarioIsRatedAppropriatelyByTheRealEngineInEveryLanguage() {
        val engine = RiskEngine()
        for (s in PracticeScenarios.all) for (l in Lang.values()) {
            val level = engine.evaluate(s.line(l)).level
            assertTrue("${s.id} $l was $level", level >= RiskLevel.MEDIUM)
        }
    }
    @Test fun idsAreUniqueAndByIdFallsBackSafely() {
        assertEquals(PracticeScenarios.all.size, PracticeScenarios.all.map { it.id }.distinct().size)
        assertEquals(PracticeScenarios.all.first(), PracticeScenarios.byId("does-not-exist"))
        assertEquals("otp", PracticeScenarios.byId("otp").id)
    }
    @Test fun originalPracticeScriptStillMatchesTheOtpScenario() {
        for (l in Lang.values()) assertEquals(PracticeScenarios.byId("otp").line(l), PracticeScript.line(l))
    }
}
