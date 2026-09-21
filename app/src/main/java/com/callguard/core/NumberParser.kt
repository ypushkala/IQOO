package com.callguard.core

enum class NumberKind(val label: String) {
    PRIVATE("withheld number"),
    INDIA_MOBILE("Indian mobile number"),
    INDIA_LANDLINE("Indian landline"),
    /** 160-series: the range TRAI reserves for banks'/financial institutions' service and transaction calls. */
    SERIES_160("160-series institutional number"),
    /** 140-series: registered telemarketers (promotional calls). */
    SERIES_140("140-series telemarketer"),
    TOLL_FREE("toll-free number"),
    INTERNATIONAL("international number"),
    SHORT_CODE("short code"),
    UNKNOWN("unrecognised number"),
}

data class ParsedNumber(val kind: NumberKind, val digits: String, val isInternational: Boolean = false) {
    /** Digits comparable with prefix lists: international numbers keep their country code, Indian ones keep "91". */
    val e164Digits: String get() = if (kind == NumberKind.INDIA_MOBILE || kind == NumberKind.INDIA_LANDLINE) "91$digits" else digits
}

/**
 * Classifies a caller number as reported by Android (usually E.164 like "+919876543210", but local
 * formats and junk happen). Pure and offline. Assumptions about Indian numbering are documented per rule.
 */
object NumberParser {
    private val mobile = Regex("^[6-9]\\d{9}$")                 // Indian mobiles are 10 digits starting 6-9
    private val series160 = Regex("^160\\d{7,9}$")              // assumption: 160 + 7-9 digits
    private val series140 = Regex("^140\\d{7}$")
    private val tollFree = Regex("^(?:1800|1860)\\d{6,7}$")
    private val landline = Regex("^0[1-9]\\d{8,10}$")           // trunk 0 + STD code + subscriber

    fun parse(raw: String?): ParsedNumber {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("private", true) || trimmed.equals("unknown", true) || trimmed.equals("anonymous", true) || trimmed == "-1" || trimmed == "-2")
            return ParsedNumber(NumberKind.PRIVATE, "")
        val plus = trimmed.startsWith("+") || trimmed.startsWith("00")
        var digits = trimmed.filter { it.isDigit() }
        if (trimmed.startsWith("00")) digits = digits.removePrefix("00")
        if (digits.isEmpty()) return ParsedNumber(NumberKind.PRIVATE, "")

        if (plus) {
            if (digits.startsWith("91") && digits.length == 12) return national(digits.substring(2))
            return ParsedNumber(NumberKind.INTERNATIONAL, digits, isInternational = true)
        }
        if (digits.length == 12 && digits.startsWith("91") && mobile.matches(digits.substring(2))) return national(digits.substring(2))
        if (digits.length == 11 && digits.startsWith("0") && mobile.matches(digits.substring(1))) return national(digits.substring(1))
        return national(digits)
    }

    private fun national(d: String): ParsedNumber = when {
        mobile.matches(d) -> ParsedNumber(NumberKind.INDIA_MOBILE, d)
        series160.matches(d) -> ParsedNumber(NumberKind.SERIES_160, d)
        series140.matches(d) -> ParsedNumber(NumberKind.SERIES_140, d)
        tollFree.matches(d) -> ParsedNumber(NumberKind.TOLL_FREE, d)
        landline.matches(d) -> ParsedNumber(NumberKind.INDIA_LANDLINE, d)
        d.length in 3..6 -> ParsedNumber(NumberKind.SHORT_CODE, d)
        else -> ParsedNumber(NumberKind.UNKNOWN, d)
    }
}
