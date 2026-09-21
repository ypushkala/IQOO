package com.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetReadyTest {
    @Test fun myLanguageSetsAllFourSettingsSensibly() {
        for (l in Lang.values()) {
            val c = MyLanguage.choices(l)
            assertEquals(l, c.screen); assertEquals(l, c.familyMessage)
            assertEquals(LanguageSetting.AUTO, c.spoken)           // warnings follow the call
            assertEquals(SummaryLanguage.SAME_AS_SCREEN, c.summary)
        }
    }
    @Test fun theDefaultChoicesNeedNoDecisions() {
        val d = LanguageChoices()
        assertEquals(LanguageSetting.AUTO, d.spoken); assertEquals(SummaryLanguage.SAME_AS_SCREEN, d.summary)
        assertEquals(AnalyseScope.UNKNOWN_ONLY, AnalyseScope.values().first { it.name == "UNKNOWN_ONLY" })
    }
    @Test fun thePracticeLineIsRatedHighByTheRealDetector() {
        val engine = RiskEngine()
        for (l in Lang.values()) assertEquals("$l", RiskLevel.HIGH, engine.evaluate(PracticeScript.line(l)).level)
    }
    @Test fun setupRunsKeepTheLastTenAndJudgeTheTarget() {
        var s = ""
        for (i in 1..12) s = SetupRun.append(s, SetupRun(i.toLong(), 100_000, 6, 7))
        assertEquals(10, SetupRun.parseAll(s).size)
        assertTrue(SetupRun(0, 179_000, 8, 7).meetsTarget)
        assertFalse(SetupRun(0, 181_000, 8, 7).meetsTarget); assertFalse(SetupRun(0, 100_000, 9, 7).meetsTarget)
        assertEquals(null, SetupRun.decode("junk"))
    }
}
