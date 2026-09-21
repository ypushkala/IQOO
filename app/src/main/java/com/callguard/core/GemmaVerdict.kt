package com.callguard.core

/** A validated classification from the on-device language model. */
data class GemmaVerdict(val risk: RiskLevel, val tactic: String, val reason: String)

/**
 * Strict, crash-proof parser for the model's reply. The model is asked for
 * `{"risk":"LOW|MEDIUM|HIGH","tactic":"...","reason":"..."}`; anything that is not exactly that
 * shape (after tolerating prose or a ```json fence around it) is rejected as null.
 */
object GemmaOutputParser {
    private const val MAX_RAW = 2_000
    private const val MAX_TACTIC = 40
    private const val MAX_REASON = 160

    fun parse(raw: String?): GemmaVerdict? {
        if (raw.isNullOrBlank()) return null
        val text = raw.take(MAX_RAW)
        val start = text.indexOf('{')
        val end = text.indexOf('}', start + 1)
        if (start < 0 || end < 0) return null
        val fields = parseFlatObject(text.substring(start, end + 1)) ?: return null
        val risk = when (fields["risk"]?.trim()?.uppercase()) {
            "LOW" -> RiskLevel.LOW
            "MEDIUM" -> RiskLevel.MEDIUM
            "HIGH" -> RiskLevel.HIGH
            else -> return null
        }
        val tactic = clean(fields["tactic"] ?: return null, MAX_TACTIC)
        val reason = clean(fields["reason"] ?: "", MAX_REASON)
        if (tactic.isEmpty()) return null
        return GemmaVerdict(risk, tactic, reason)
    }

    private fun clean(s: String, max: Int) = s.replace(Regex("\\s+"), " ").trim().take(max)

    /** Parses `{"k":"v", ...}` with string values only; returns null on any deviation. */
    private fun parseFlatObject(s: String): Map<String, String>? {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun str(): String? {
            if (i >= s.length || s[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) return null
                        when (val e = s[i++]) {
                            'n', 't', 'r' -> sb.append(' ')
                            '"', '\\', '/' -> sb.append(e)
                            'u' -> { if (i + 4 > s.length) return null; i += 4; sb.append('?') }
                            else -> return null
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return null
        }
        ws(); if (i >= s.length || s[i] != '{') return null
        i++
        val out = LinkedHashMap<String, String>()
        ws()
        if (i < s.length && s[i] == '}') return out
        while (true) {
            ws(); val k = str() ?: return null
            ws(); if (i >= s.length || s[i] != ':') return null
            i++; ws()
            val v = str() ?: return null
            out[k.lowercase()] = v
            ws()
            if (i >= s.length) return null
            when (s[i++]) { ',' -> continue; '}' -> return out; else -> return null }
        }
    }
}
