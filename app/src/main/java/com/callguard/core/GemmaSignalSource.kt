package com.callguard.core

/**
 * Serves the most recent Gemma verdict to [RiskEngine]. Inference happens elsewhere, on its own
 * thread; [analyze] never runs the model and just returns the cached verdict while it is fresh.
 * LOW verdicts produce no signal (they can never change the fused level anyway).
 */
class GemmaSignalSource(
    private val ttlMs: Long = 30_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) : SignalSource {
    @Volatile private var verdict: GemmaVerdict? = null
    @Volatile private var atMs = 0L

    fun update(v: GemmaVerdict) { verdict = v; atMs = clock() }
    fun clear() { verdict = null }
    fun latest(): GemmaVerdict? = verdict?.takeIf { clock() - atMs < ttlMs }

    override fun analyze(normalizedText: String): List<Signal> {
        val v = latest() ?: return emptyList()
        if (v.risk == RiskLevel.LOW) return emptyList()
        return listOf(Signal("gemma", "gemma", mapTactic(v.tactic), v.risk, v.tactic.lowercase(), reason = v.reason))
    }

    companion object {
        fun mapTactic(t: String): Tactic {
            val s = t.lowercase()
            return when {
                Regex("\\b(?:otp|pin|cvv)\\b|credential|password|verification|passcode").containsMatchIn(s) -> Tactic.CREDENTIAL_REQUEST
                Regex("remote|anydesk|teamviewer|screen").containsMatchIn(s) -> Tactic.REMOTE_ACCESS
                Regex("kyc|account|sim|block").containsMatchIn(s) -> Tactic.ACCOUNT_THREAT
                Regex("authority|police|\\bcbi\\b|arrest|customs|legal|impersonat").containsMatchIn(s) -> Tactic.AUTHORITY_THREAT
                Regex("payment|refund|lottery|prize|transfer|money").containsMatchIn(s) -> Tactic.PAYMENT_LURE
                Regex("urgen").containsMatchIn(s) -> Tactic.URGENCY
                Regex("secre|isolat").containsMatchIn(s) -> Tactic.SECRECY
                else -> Tactic.OTHER
            }
        }
    }
}
