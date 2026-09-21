package com.callguard.core

/**
 * Committed segments plus the in-progress partial, trimmed to the most recent [maxChars].
 * A segment can carry an English translation (for Hindi/Hinglish/regional speech): the native text
 * is what the user sees and what the transliterated rules read, the English is what the English
 * rules and Gemma read.
 */
class RollingTranscript(private val maxChars: Int = 600) {
    private class Segment(val native: String, val english: String?) {
        /**
         * True when the native text is itself a safety warning ("kisi ko apna OTP kabhi mat batana").
         * The translation of a warning is often mangled into a request ("Someone should tell their OTP"),
         * so for such a segment only the native text is analysed; the translation is still displayed.
         */
        private val nativeIsAdvisory: Boolean by lazy {
            TextNormalizer.normalize(native).let { it.contains("never share ") || it.contains("never ask for ") }
        }
        val analysisParts: List<String> get() = if (nativeIsAdvisory) listOf(native) else listOfNotNull(native, english)
        val display get() = if (english == null) native else "$native ($english)"
        /**
         * What Gemma reads: English if translated; Latin-script Hinglish natives are kept too. A native script with no
         * translation (Telugu, or Hindi read by the Indic model) is given as the normalizer's Latin canonical text
         * ("aapka kyc khatam ho gaya hai ... account band ho jayega"), because a 1B model cannot read the script itself.
         */
        val forModel get() = when {
            english == null -> if (isLatin(native)) native else TextNormalizer.normalize(native)
            isLatin(native) -> "$native. $english"
            else -> english
        }
    }

    private val committed = ArrayDeque<Segment>()
    private var partial = ""

    @Synchronized fun setPartial(text: String) { partial = text.trim() }

    @Synchronized fun commit(text: String, english: String? = null) {
        val t = text.trim()
        partial = ""
        if (t.isEmpty()) return
        val e = english?.trim()?.takeIf { it.isNotEmpty() && !it.equals(t, ignoreCase = true) }
        committed.addLast(Segment(t, e))
        while (committed.size > 1 && committed.sumOf { it.display.length + 1 } > maxChars) committed.removeFirst()
    }

    /** What the screen shows. */
    @Synchronized fun text(): String = (committed.map { it.display } + partial).filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * Text for the rules. Native text and its English translation are separate sentences (the
     * translation is left out when the native text is an advisory, see [Segment]), and
     * segments (VAD-separated, so a real pause apart) are separate sentences too, so a phrase can
     * never straddle two of them.
     */
    @Synchronized fun analysisText(): String =
        (committed.flatMap { it.analysisParts } + partial).filter { it.isNotEmpty() }.joinToString(" . ")

    /** Recent text for Gemma (English where available). */
    @Synchronized fun modelText(): String = (committed.map { it.forModel } + partial).filter { it.isNotEmpty() }.joinToString(" ")

    @Synchronized fun clear() { committed.clear(); partial = "" }

    companion object {
        /** True if the text has no Indian-script letters (i.e. English or romanized Hinglish/Telugu). */
        fun isLatin(s: String) = !IndicScript.hasIndicLetters(s)
    }
}
