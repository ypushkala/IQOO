package com.callguard.core

/**
 * Detects a caller claiming to be a bank / financial institution ("this is HDFC Bank calling",
 * "bank se bol raha hoon"). Runs on [TextNormalizer] output, so it also sees Hinglish and Devanagari.
 */
object InstitutionClaim {
    private const val NAMES = "(?:sbi|state bank|hdfc|icici|axis|kotak|pnb|punjab national|bank of baroda|canara|union bank|idfc|indusind|yes bank|" +
        "paytm|phonepe|google pay|amazon pay|credit card|debit card|your bank|the bank|bank|rbi|reserve bank|nbfc|insurance|lic)"
    private val english = Regex("\\b(?:calling|speaking|call|this is|i am|i m|we are|message) (?:from |with )?(?:the |your )?$NAMES(?: \\w+){0,2}?\\b")
    private val hinglish = Regex("\\b$NAMES(?: \\w+){0,2} se (?:bol|baat|call|phone|main)\\b")

    /** The matched claim, or null. */
    fun find(normalizedText: String): String? =
        (english.find(normalizedText) ?: hinglish.find(normalizedText))?.value?.trim()
}
