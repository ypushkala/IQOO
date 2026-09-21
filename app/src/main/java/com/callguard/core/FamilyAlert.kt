package com.callguard.core

/** The pre-filled message for a trusted family member. Never contains audio or transcript. */
object FamilyAlert {
    fun message(level: RiskLevel, tactics: List<Tactic>, caller: String?, lang: Lang): String {
        val why = tactics.take(3).joinToString(", ") { Strings.tactic(it, lang) }
        val risk = Strings.levelWord(level, lang)
        val hasCaller = !caller.isNullOrBlank()
        return when (lang) {
            Lang.EN -> buildString {
                append("CallGuard alert: I may be getting a scam call right now")
                append(if (why.isEmpty()) " ($risk risk)." else " ($risk risk: $why).")
                if (hasCaller) append(" Caller: $caller.")
                append(" Please call me. (Sent from my phone. No audio or call text is shared.)")
            }
            Lang.HI -> buildString {
                append("CallGuard चेतावनी: मुझे अभी शायद ठगी वाला कॉल आ रहा है")
                append(if (why.isEmpty()) " ($risk)।" else " ($risk: $why)।")
                if (hasCaller) append(" कॉलर: $caller।")
                append(" कृपया मुझे फ़ोन करें। (मेरे फ़ोन से भेजा गया। कोई ऑडियो या बातचीत नहीं भेजी गई है।)")
            }
            Lang.TE -> buildString {
                append("CallGuard హెచ్చరిక: నాకు ఇప్పుడే మోసపు కాల్ వస్తున్నట్లు ఉంది")
                append(if (why.isEmpty()) " ($risk)." else " ($risk: $why).")
                if (hasCaller) append(" కాలర్: $caller.")
                append(" దయచేసి నాకు ఫోన్ చేయండి. (నా ఫోన్ నుండి పంపబడింది. ఆడియో లేదా సంభాషణ ఏదీ పంపబడలేదు.)")
            }
        }
    }
}
