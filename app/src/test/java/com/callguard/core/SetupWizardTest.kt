package com.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupWizardTest {
    private fun s(vararg d: Triple<String, Boolean, Boolean>) = d.map { WizardStep(it.first, it.second, it.third) }
    private val steps = s(Triple("perms", true, false), Triple("role", false, true), Triple("battery", false, true), Triple("run", false, false))

    @Test fun focusIsTheFirstStepNotDone() = assertEquals(1, SetupWizard.focusIndex(steps, emptySet()))
    @Test fun skippingAnOptionalStepMovesOn() = assertEquals(2, SetupWizard.focusIndex(steps, setOf("role")))
    @Test fun aRequiredStepCannotBeSkipped() = assertEquals(0, SetupWizard.focusIndex(s(Triple("run", false, false)), setOf("run")))
    @Test fun finishedWhenOnlySkippedOptionalStepsRemain() {
        val allButOptional = s(Triple("perms", true, false), Triple("role", false, true))
        assertFalse(SetupWizard.finished(allButOptional, emptySet()))
        assertTrue(SetupWizard.finished(allButOptional, setOf("role")))
        assertNull(SetupWizard.position(allButOptional, setOf("role")))
    }
    @Test fun positionAndCount() {
        assertEquals(2 to 4, SetupWizard.position(steps, emptySet()))
        assertEquals(1, SetupWizard.doneCount(steps))
    }
}
