package com.callguard.core

/** The short, localized "because ..." line shown live during a call — one tactic, no quoted transcript. */
object AlertReason {
    /** Picks the most serious tactic among the signals that just alerted, highest level first. */
    fun topTactic(signals: List<Signal>): Tactic? = signals.filter { !it.advisory }.maxByOrNull { it.level.ordinal }?.tactic

    fun line(signals: List<Signal>, lang: Lang): String? = lineForTactic(topTactic(signals), lang)

    fun lineForTactic(t: Tactic?, lang: Lang): String? {
        if (t == null) return null
        val why = Strings.tactic(t, lang)
        return when (lang) {
            Lang.EN -> "Because: $why"
            Lang.HI -> "कारण: $why"
            Lang.TE -> "కారణం: $why"
        }
    }
}
