package com.callguard.core

/** A source of scam evidence over the normalized rolling transcript. */
interface SignalSource {
    fun analyze(normalizedText: String): List<Signal>
}

/**
 * Tactic-tagged keyword/regex rules. Patterns run on [TextNormalizer] output (lowercase,
 * space-separated words), so `\b` is a reliable word boundary.
 */
class KeywordSignalSource : SignalSource {
    private class Rule(
        val id: String,
        val tactic: Tactic,
        val level: RiskLevel,
        val pattern: Regex,
        /** If this matches anywhere in the text, the rule fires at [escalatedLevel] instead. */
        val escalateIf: Regex? = null,
        val escalatedLevel: RiskLevel = level,
        /** Downgrade to LOW when the phrase is inside a warning ("never share your OTP"). */
        val advisoryAware: Boolean = false,
    )

    private fun r(p: String) = Regex(p)

    private val threatWords = "(?:blocked|block|deactivated|suspended|frozen|closed|disconnected|expired|expire|expires|expiring)"

    private val rules = listOf(
        Rule("cred", Tactic.CREDENTIAL_REQUEST, RiskLevel.HIGH,
            r("\\b(?:otp|cvv|cvc|pin(?! code)|one time (?:password|pin|code)|(?:verification|security|confirmation|authentication) code|passcode)\\b"),
            advisoryAware = true),
        Rule("remote-app", Tactic.REMOTE_ACCESS, RiskLevel.HIGH,
            r("\\b(?:anydesk|teamviewer|quicksupport|rustdesk|ammyy|airdroid|remote (?:access|support|desktop)|screen shar(?:e|ing))\\b"),
            advisoryAware = true),
        Rule("kyc", Tactic.ACCOUNT_THREAT, RiskLevel.MEDIUM,
            r("\\bkyc\\b(?: \\w+){0,4}? (?:expired|expire|expires|expiring|pending|suspended|blocked|incomplete|invalid|update|updated|verification|verify)\\b" +
                "|\\b(?:update|verify|complete) (?:your )?kyc\\b")),
        Rule("acct-block", Tactic.ACCOUNT_THREAT, RiskLevel.MEDIUM,
            r("\\b(?:sim|account|card|number|bank account)\\b(?: \\w+){0,5}? $threatWords\\b" +
                "|\\b$threatWords (?:your |the )?(?:sim|account|card)\\b")),
        Rule("digital-arrest", Tactic.AUTHORITY_THREAT, RiskLevel.HIGH,
            r("\\b(?:digital arrest|arrest warrant|money laundering)\\b")),
        Rule("authority", Tactic.AUTHORITY_THREAT, RiskLevel.MEDIUM,
            r("\\b(?:police|cbi|customs|narcotics|crime branch|cyber crime|enforcement directorate)\\b"),
            escalateIf = r("\\b(?:arrest|arrested|warrant|fir|case|seized|illegal|smuggling|drugs|parcel|custody|jail|laundering)\\b"),
            escalatedLevel = RiskLevel.HIGH),
        Rule("payment", Tactic.PAYMENT_LURE, RiskLevel.LOW,
            r("\\b(?:refund|lottery|prize|winner|cashback|payment|transfer)\\b")),
        // Pressure tactics. Urgency words are common in normal speech, so alone they stay LOW and
        // never alert. A bare "now" is too generic ("now I understand"); it only counts next to an
        // imperative ("act now", "pay now", "call me now").
        Rule("urgency", Tactic.URGENCY, RiskLevel.LOW,
            r("\\b(?:immediately|urgent|urgently|act now|final warning|expires|expiring" +
                "|(?:do|pay|send|transfer|share|tell|give|read|install|download|verify|confirm|update|complete|call|come|hurry|click|press)(?: \\w+){0,3} now)\\b")),
        // Isolation is a hallmark of these scams, so it is MEDIUM on its own.
        Rule("secrecy", Tactic.SECRECY, RiskLevel.MEDIUM,
            r("\\b(?:do not|dont|never) (?:tell|inform|let) (?:this to |about this to )?(?:anyone|anybody|any one|your family|your bank)\\b" +
                "|\\bkeep (?:this|it|the call|the matter|all this)(?: a)? (?:secret|confidential|private|between us)\\b" +
                "|\\b(?:do not|dont) share this(?! (?:otp|pin|cvv|cvc|code|password|passcode|number|card)\\b)")),
    )

    override fun analyze(normalizedText: String): List<Signal> {
        val out = ArrayList<Signal>()
        for (rule in rules) {
            for (m in rule.pattern.findAll(normalizedText)) {
                var level = if (rule.escalateIf?.containsMatchIn(normalizedText) == true) rule.escalatedLevel else rule.level
                var advisory = false
                if (rule.advisoryAware && AdvisoryContext.isAdvisory(normalizedText, m.range.first, m.range.last + 1)) {
                    level = RiskLevel.LOW
                    advisory = true
                }
                out += Signal("keyword", rule.id, rule.tactic, level, m.value, advisory)
            }
        }
        return out
    }
}
