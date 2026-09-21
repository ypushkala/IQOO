package com.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPlanTest {
    private fun ids(vararg s: Situation) = RecoveryPlan.steps(s.toSet()).map { it.id }

    @Test fun moneySentPutsTheCyberHelplineFirstThenTheBank() {
        val i = ids(Situation.PAID_MONEY)
        assertEquals(StepId.CALL_1930, i[0]); assertEquals(StepId.CALL_BANK, i[1])
    }
    @Test fun sharedCodeStartsWithTheBank() = assertEquals(StepId.CALL_BANK, ids(Situation.SHARED_CODE)[0])
    @Test fun aLiveRemoteAppIsCutBeforeAnythingElse() = assertEquals(StepId.UNINSTALL_APP, ids(Situation.SHARED_CODE, Situation.INSTALLED_APP, Situation.PAID_MONEY)[0])
    @Test fun installedAppStartsWithUninstall() = assertEquals(StepId.UNINSTALL_APP, ids(Situation.INSTALLED_APP)[0])
    @Test fun noStepIsRepeatedAndReportingIsAlwaysLast() {
        for (mask in 1..7) {
            val s = Situation.values().filterIndexed { i, _ -> mask shr i and 1 == 1 }.toSet()
            val i = RecoveryPlan.steps(s).map { it.id }
            assertEquals(i.distinct(), i)
            assertEquals(StepId.REPORT_PORTAL, i.last())
            assertTrue(StepId.CALL_1930 in i)
        }
    }
    @Test fun nothingLostGivesTheCalmShortList() = assertEquals(listOf(StepId.HANG_UP_STAY_CALM, StepId.CALL_1930), ids())
    @Test fun everyStepAndSituationHasTextInAllLanguages() {
        for (l in Lang.values()) {
            for (id in StepId.values()) for (b in listOf(true, false)) assertTrue("$id $l", RecoveryPlan.stepText(id, l, b).isNotBlank())
            for (s in Situation.values()) assertTrue(RecoveryPlan.situationLabel(s, l).isNotBlank())
        }
    }
    @Test fun noBankNumberIsBundled() {
        for (l in Lang.values()) for (id in StepId.values()) assertFalse(Regex("\\b1800\\d{6,}|\\b\\d{10}\\b").containsMatchIn(RecoveryPlan.stepText(id, l, false)))
    }
    @Test fun tacticsPreselectAndPrioritise() {
        assertEquals(setOf(Situation.SHARED_CODE, Situation.INSTALLED_APP), RecoveryPlan.likelySituations(setOf(Tactic.CREDENTIAL_REQUEST, Tactic.REMOTE_ACCESS)))
        assertEquals(Tactic.REMOTE_ACCESS, RecoveryPlan.urgentTactic(setOf(Tactic.CREDENTIAL_REQUEST, Tactic.REMOTE_ACCESS)))
        assertNull(RecoveryPlan.urgentTactic(setOf(Tactic.URGENCY, Tactic.OTHER)))
    }
}
