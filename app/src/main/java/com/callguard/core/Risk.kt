package com.callguard.core

enum class RiskLevel { LOW, MEDIUM, HIGH }

enum class Tactic(
    val label: String,
    /** Highest level a *model* may assign for this tactic (mirrors the rule policy: KYC/account is MEDIUM, etc.). */
    val maxModelLevel: RiskLevel,
) {
    CREDENTIAL_REQUEST("OTP/credential request", RiskLevel.HIGH),
    REMOTE_ACCESS("remote-access app", RiskLevel.HIGH),
    ACCOUNT_THREAT("KYC/account/SIM threat", RiskLevel.MEDIUM),
    AUTHORITY_THREAT("authority impersonation", RiskLevel.HIGH),
    PAYMENT_LURE("payment/prize lure", RiskLevel.MEDIUM),
    URGENCY("urgency pressure", RiskLevel.LOW),
    SECRECY("secrecy demand", RiskLevel.MEDIUM),
    OTHER("suspicious pattern", RiskLevel.LOW), // free-form tactics the rules do not know may inform, never alert
    NUMBER_RISK("suspicious caller number", RiskLevel.LOW),
    IDENTITY_MISMATCH("unverified caller identity", RiskLevel.MEDIUM),
}

/** One piece of evidence from any signal source (keywords now, Gemma in Phase 2). */
data class Signal(
    val source: String,
    val ruleId: String,
    val tactic: Tactic,
    val level: RiskLevel,
    val matched: String,
    /** True when a rule matched but was downgraded because the phrase was a warning ("never share your OTP"). */
    val advisory: Boolean = false,
    /** Short model-written explanation; empty for rule signals. */
    val reason: String = "",
) {
    /** Stable identity used for alert debouncing. */
    val key: String get() = "$ruleId:$matched"
}

data class DetectionResult(val level: RiskLevel, val signals: List<Signal>) {
    companion object {
        val NONE = DetectionResult(RiskLevel.LOW, emptyList())
    }
}
