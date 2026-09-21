package com.callguard.core

/**
 * Turns the current caller assessment into signals. It ignores the words except for one thing: a
 * bank/institution claim, which it checks against the number. Rules:
 *  - suspicious number (ELEVATED/HIGH): an informational LOW signal, never an alert on its own;
 *  - claims to be a bank but the number is not a 160-series/toll-free number and not a saved
 *    contact: MEDIUM "unverified caller identity".
 */
class NumberSignalSource(private val assessment: () -> NumberAssessment?) : SignalSource {
    /** True while the current caller is HIGH risk (used by the engine to amplify already-suspicious content). */
    fun isHighRisk(): Boolean = assessment()?.level == NumberRisk.HIGH

    override fun analyze(normalizedText: String): List<Signal> {
        val a = assessment() ?: return emptyList()
        val out = ArrayList<Signal>()
        if (a.level >= NumberRisk.ELEVATED) {
            out += Signal("number", "number-risk", Tactic.NUMBER_RISK, RiskLevel.LOW, a.kind.label, reason = a.reasons.joinToString(", "))
        }
        if (a.cannotBackInstitutionClaim) {
            InstitutionClaim.find(normalizedText)?.let { claim ->
                out += Signal("number", "identity-mismatch", Tactic.IDENTITY_MISMATCH, RiskLevel.MEDIUM, "institution claim from ${a.kind.label}",
                    reason = "Claims to be an institution ('$claim') but is calling from a ${a.kind.label}")
            }
        }
        return out
    }
}
