package com.callguard.core

/**
 * Fuses deterministic rules with model signals into one risk level.
 *
 * Model signals can only raise the result, never lower it (levels are combined with max), and
 * their influence is capped so a small model cannot alarm on its own or override a warning:
 *  - a rule signal of MEDIUM+ corroborates the model: no global cap;
 *  - rules recognised an advisory ("never share your OTP") and nothing else: capped at LOW;
 *  - otherwise the model alone: capped at MEDIUM.
 * On top of that, each tactic has its own ceiling ([Tactic.maxModelLevel]): a model may say HIGH
 * only for credential requests, remote access and authority threats, keeping KYC/account threats
 * at MEDIUM as the rules define them.
 * The caller number (see [NumberSignalSource]) is fused last, as context rather than evidence.
 * If the model is absent, slow or fails, the rules alone decide, exactly as before.
 */
class RiskEngine(
    private val rules: List<SignalSource> = listOf(KeywordSignalSource()),
    private val models: List<SignalSource> = emptyList(),
    /** Caller-number context. It can add information and amplify suspicious content, never alert alone or lower a level. */
    private val number: NumberSignalSource? = null,
) {
    fun evaluate(rawText: String): DetectionResult {
        val text = TextNormalizer.normalize(rawText)
        val ruleSignals = rules.flatMap { it.analyze(text) }
        val numberSignals = number?.analyze(text).orEmpty()
        if (text.isEmpty() && numberSignals.isEmpty()) return DetectionResult.NONE
        val cap = when {
            ruleSignals.any { it.level >= RiskLevel.MEDIUM } -> RiskLevel.HIGH
            ruleSignals.any { it.advisory } -> RiskLevel.LOW
            else -> RiskLevel.MEDIUM
        }
        val modelSignals = models.flatMap { it.analyze(text) }
            .map { sig -> minOf(cap, sig.tactic.maxModelLevel).let { if (sig.level > it) sig.copy(level = it) else sig } }
        // Number as a multiplier: a HIGH-risk number turns already-suspicious content (a rule hit or an
        // identity mismatch, at MEDIUM) into HIGH. A bad number with harmless words stays LOW.
        val contentLevel = (ruleSignals + numberSignals.filter { it.tactic == Tactic.IDENTITY_MISMATCH }).maxOfOrNull { it.level } ?: RiskLevel.LOW
        val boost = if (number?.isHighRisk() == true && contentLevel == RiskLevel.MEDIUM)
            listOf(Signal("number", "number-boost", Tactic.NUMBER_RISK, RiskLevel.HIGH, "suspicious number",
                reason = "A high-risk caller number makes this call's content more dangerous"))
        else emptyList()
        val signals = (ruleSignals + modelSignals + numberSignals + boost)
            .sortedByDescending { it.level } // stable: the strongest signal wins each key
            .distinctBy { it.key }
        val level = signals.maxOfOrNull { it.level } ?: RiskLevel.LOW
        return DetectionResult(level, signals)
    }
}
