package com.callguard.core

/** Which calls CallGuard analyses. Unknown-numbers-only saves battery and is more private: your own contacts are not listened to. */
enum class AnalyseScope { UNKNOWN_ONLY, ALL }

object ListenPolicy {
    data class Decision(val listen: Boolean, val reason: String)

    /**
     * [caller] is what the caller-ID role learned while the phone rang (null if the role is off or the call was outgoing).
     * With no information CallGuard listens: staying protected wins over saving power. A number the user marked "not a scam"
     * is still listened to, because a trusted number can be spoofed or taken over.
     */
    fun decide(scope: AnalyseScope, caller: NumberAssessment?): Decision = when {
        scope == AnalyseScope.ALL -> Decision(true, "analysing all calls")
        caller == null -> Decision(true, "caller not known")
        caller.inContacts == true -> Decision(false, "saved contact")
        else -> Decision(true, "unknown number")
    }
}
