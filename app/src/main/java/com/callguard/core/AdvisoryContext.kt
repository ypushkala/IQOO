package com.callguard.core

/**
 * Recognises a credential/remote-access term used in a warning ("never share your OTP", "your
 * bank will never ask for your PIN", "the OTP should never be shared") rather than as a request.
 *
 * Works on [TextNormalizer] output and never looks past a sentence boundary ("."). Every negated
 * cue in the sentence is tried, and a cue only counts if it is within a few words of the term
 * with no request word ("tell", "just", "now", ...) in between, so "don't worry, just tell me the
 * OTP" is still a request. When unsure it answers false, i.e. keeps the higher risk.
 */
object AdvisoryContext {
    private const val BOUNDARY = " . "
    private const val WINDOW_CHARS = 100
    private const val MAX_GAP_WORDS = 6

    private val cue = Regex(
        "\\b(?:never|do not|dont|does not|doesnt|will not|wont|should not|shouldnt|must not|mustnt|not to|avoid|beware of)" +
            "(?: (?:ever|to|you|please|kindly))* " +
            "(?:share|give|reveal|disclose|provide|send|forward|read out|read|ask|asks|install|download|enter|type)\\b"
    )
    private val requestCues = setOf(
        "tell", "give", "send", "read", "say", "enter", "type", "provide", "now", "just", "but", "so", "however", "instead",
    )
    /** Warning that follows the term: "otp should never be shared", "the pin is confidential". */
    private val trailing = Regex(
        "^(?: \\w+){0,3}? (?:(?:(?:should|must|will|can|could|is|are) (?:never|not)|never|not|shouldnt|mustnt|cannot|cant|wont) (?:be )?" +
            "(?:shared|given|told|disclosed|revealed|asked|requested|provided)" +
            "|(?:is|are) (?:confidential|secret|private))\\b"
    )

    /**
     * Hindi/Telugu put the verb after the object ("OTP batao", "OTP చెప్పండి" = "OTP tell"), so a request verb
     * straight after the term means it is a request, whatever warning came before it.
     */
    private val requestAfter = Regex("^(?: \\w+)?(?: \\w+)? (?:cheppandi|ivvandi|batao|bataiye|bataiyega|batayein|bhejo|bhejiye|dijiye)\\b")

    /** [text] is normalized; the flagged term spans [matchStart] until [matchEnd] (exclusive). */
    fun isAdvisory(text: String, matchStart: Int, matchEnd: Int): Boolean {
        if (requestAfter.containsMatchIn(text.substring(matchEnd).substringBefore(BOUNDARY))) return false
        val before = text.substring(0, matchStart).substringAfterLast(BOUNDARY).takeLast(WINDOW_CHARS).trimEnd()
        val precededByWarning = cue.findAll(before).any { m ->
            val gap = before.substring(m.range.last + 1).trim()
            val words = if (gap.isEmpty()) emptyList() else gap.split(' ')
            words.size <= MAX_GAP_WORDS && words.none { it in requestCues }
        }
        if (precededByWarning) return true
        val after = text.substring(matchEnd).substringBefore(BOUNDARY)
        return trailing.containsMatchIn(after)
    }
}
