package com.callguard.core

enum class AutoAlertDecision { SEND, OFF, NO_CONSENT, NO_CONTACT, NO_PERMISSION, NOT_HIGH, ALREADY_SENT }

/**
 * Opt-in automatic family SMS. It sends only when the user turned it on through the consent screen, a contact is chosen, the
 * SMS permission is granted, the call reached HIGH, and nothing was sent for this call yet. Default: off.
 */
object AutoFamilyAlert {
    fun decide(enabled: Boolean, consented: Boolean, hasContact: Boolean, hasPermission: Boolean, level: RiskLevel, alreadySent: Boolean): AutoAlertDecision = when {
        !enabled -> AutoAlertDecision.OFF
        !consented -> AutoAlertDecision.NO_CONSENT
        !hasContact -> AutoAlertDecision.NO_CONTACT
        !hasPermission -> AutoAlertDecision.NO_PERMISSION
        level != RiskLevel.HIGH -> AutoAlertDecision.NOT_HIGH
        alreadySent -> AutoAlertDecision.ALREADY_SENT
        else -> AutoAlertDecision.SEND
    }

    /** The sample shown on the consent screen: exactly the text that would be sent, with an example caller. */
    fun sampleMessage(lang: Lang, includeCaller: Boolean): String =
        FamilyAlert.message(RiskLevel.HIGH, listOf(Tactic.CREDENTIAL_REQUEST, Tactic.REMOTE_ACCESS), if (includeCaller) "+91 98xxxxxx10" else null, lang)
}

/** One line of the "what was sent" log. Holds no message text and no caller number. */
data class AlertLogEntry(val atEpochMs: Long, val outcome: Outcome, val contactName: String) {
    enum class Outcome { SENT, FAILED, TEST }

    fun encode() = "$atEpochMs|${outcome.name}|${contactName.replace("|", " ").replace("\n", " ")}"

    companion object {
        const val MAX = 20
        fun decode(line: String): AlertLogEntry? {
            val p = line.split('|', limit = 3)
            if (p.size != 3) return null
            return AlertLogEntry(p[0].toLongOrNull() ?: return null, runCatching { Outcome.valueOf(p[1]) }.getOrNull() ?: return null, p[2])
        }
        fun parseAll(stored: String): List<AlertLogEntry> = stored.split('\n').mapNotNull { decode(it) }
        fun append(stored: String, e: AlertLogEntry): String = (parseAll(stored) + e).takeLast(MAX).joinToString("\n") { it.encode() }
    }
}
