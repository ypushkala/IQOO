package com.callguard.core

/**
 * Normalizes ASR output before matching: lowercase, punctuation stripped, spelled-out letters
 * merged ("O T P" -> "otp") and split brand names re-joined ("any desk" -> "anydesk").
 *
 * Sentence ends (". ! ? ;" followed by space/end) survive as a standalone "." token so rules can
 * tell sentences apart. `\w+` gaps in rules never cross it. Leading/trailing ones are trimmed.
 */
object TextNormalizer {
    private val apostrophes = Regex("['’`]")
    private const val MARK = '\u0001'
    // not after a lone letter, so "K.Y.C. has" keeps its acronym
    private val sentenceEnd = Regex("(?<!\\b[a-z])[.!?;\u0964]+(?=\\s|$)")
    private val nonAlnum = Regex("[^a-z0-9$MARK]+")
    private val marks = Regex("(?:\\s*$MARK)+\\s*")
    private val edgeBoundaries = Regex("^(?:\\. ?)+|(?: ?\\.)+$")
    private val spelledLetters = Regex("(?<![a-z0-9])[a-z](?: [a-z])+(?![a-z0-9])")
    private val joins = listOf(
        Regex("\\bany desk\\b") to "anydesk",
        Regex("\\bteam viewer\\b") to "teamviewer",
        Regex("\\bquick support\\b") to "quicksupport",
        // Mishearings observed from the on-device ASR on real speech (acronyms are its weak spot).
        Regex("\\b(?:install|download|open|use) (?:(?:a|an|and|any) desk|any (?:disk|dusk))\\b") to "install anydesk",
        Regex("\\bany (?:disk|dusk) (?:download|install)\\b") to "anydesk download",
        Regex("\\b(?:oh|o) (?:teepee|(?:tee|t|de|d) (?:pee|p|be|b))\\b") to "otp",
        Regex("\\b(?:kill icy|cave i see|kay why see|kay y c|k i c)\\b") to "kyc",
    )

    fun normalize(raw: String): String {
        var s = HindiNormalizer.scriptsToLatin(TeluguNormalizer.teluguToLatin(raw.lowercase().replace(apostrophes, "")))
        s = s.replace(sentenceEnd, " $MARK ")
        s = s.replace(nonAlnum, " ").trim()
        s = spelledLetters.replace(s) { it.value.replace(" ", "") }
        for ((re, to) in joins) s = re.replace(s, to)
        s = HindiNormalizer.hinglishToEnglish(s)
        s = TeluguNormalizer.rewrite(s)
        s = marks.replace(s, " . ").trim()
        return edgeBoundaries.replace(s, "").trim()
    }
}
