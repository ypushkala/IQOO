package com.callguard.core

/** One step of the setup wizard. [optional] steps may be skipped for now; required ones may not. */
data class WizardStep(val key: String, val done: Boolean, val optional: Boolean)

/** Which step the wizard shows in full: the first one that is not done and not skipped. Everything else is a one-line row. */
object SetupWizard {
    fun focusIndex(steps: List<WizardStep>, skipped: Set<String>): Int =
        steps.indexOfFirst { !it.done && !(it.optional && it.key in skipped) }

    fun doneCount(steps: List<WizardStep>) = steps.count { it.done }

    /** True when nothing needs the user any more (skipped optional steps do not count). */
    fun finished(steps: List<WizardStep>, skipped: Set<String>) = focusIndex(steps, skipped) < 0

    /** 1-based number of the focused step among the steps still to do, e.g. "step 2 of 5" while 2 are already done. */
    fun position(steps: List<WizardStep>, skipped: Set<String>): Pair<Int, Int>? {
        val i = focusIndex(steps, skipped)
        if (i < 0) return null
        return (i + 1) to steps.size
    }
}
