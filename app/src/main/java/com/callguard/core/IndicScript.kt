package com.callguard.core

enum class Script { DEVANAGARI, ARABIC, TELUGU, TAMIL, KANNADA, MALAYALAM, BENGALI, GUJARATI, GURMUKHI }

/** Identifies which Indian writing system a transcript is in. Speech recognisers that write native script make the script the language signal. */
object IndicScript {
    private fun scriptOf(c: Char): Script? = when (c.code) {
        in 0x0900..0x097F -> Script.DEVANAGARI
        in 0x0600..0x06FF, in 0x0750..0x077F -> Script.ARABIC
        in 0x0C00..0x0C7F -> Script.TELUGU
        in 0x0B80..0x0BFF -> Script.TAMIL
        in 0x0C80..0x0CFF -> Script.KANNADA
        in 0x0D00..0x0D7F -> Script.MALAYALAM
        in 0x0980..0x09FF -> Script.BENGALI
        in 0x0A80..0x0AFF -> Script.GUJARATI
        in 0x0A00..0x0A7F -> Script.GURMUKHI
        else -> null
    }

    /** True if the text contains any letter of an Indian writing system (even a single one). */
    fun hasIndicLetters(text: String): Boolean = text.any { scriptOf(it) != null }

    /** The dominant non-Latin script (at least two letters of it), or null for English/Latin text. */
    fun detect(text: String): Script? {
        val counts = HashMap<Script, Int>()
        for (c in text) scriptOf(c)?.let { counts.merge(it, 1, Int::plus) }
        return counts.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key
    }

    /** The language this CallGuard build can answer in for a script; null for scripts it can only display. */
    fun langOf(script: Script?): Lang? = when (script) {
        Script.DEVANAGARI, Script.ARABIC -> Lang.HI   // Devanagari = Hindi (Marathi shares it), Arabic script = Urdu-style Hindi
        Script.TELUGU -> Lang.TE
        else -> null
    }

    /** Short language code for logs and the model's language routing. */
    fun code(script: Script?): String = when (script) {
        Script.DEVANAGARI -> "hi"; Script.ARABIC -> "ur"; Script.TELUGU -> "te"; Script.TAMIL -> "ta"; Script.KANNADA -> "kn"
        Script.MALAYALAM -> "ml"; Script.BENGALI -> "bn"; Script.GUJARATI -> "gu"; Script.GURMUKHI -> "pa"; null -> "en"
    }
}
