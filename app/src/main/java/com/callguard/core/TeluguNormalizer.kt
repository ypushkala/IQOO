package com.callguard.core

import java.text.Normalizer

/**
 * Maps Telugu (script and romanised) onto the canonical English vocabulary the rules use, exactly as
 * [HindiNormalizer] does for Hindi. The vocabulary comes from what the Omnilingual model actually
 * writes for Telugu speech (including its mixed-script output such as "సbi" for "CBI").
 *
 * Telugu is agglutinative ("అరెస్ట్లో" = arrest + "in"), so scam words are matched without a right word
 * boundary; leftover suffix letters are unknown script and are dropped by the caller.
 */
object TeluguNormalizer {
    private const val TE = "\\u0C00-\\u0C7F"
    private val hasTelugu = Regex("[$TE]")
    private val joiners = Regex("[\\u200C\\u200D]")

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC).replace(joiners, "")

    // longest key first, so "డిజిటల్ అరెస్ట్" wins over "అరెస్ట్"
    private val lexicon: List<Pair<Regex, String>> = listOf(
        // credentials / remote access / KYC
        "ఓటీపీ" to "otp", "ఓటిపి" to "otp", "ఓ టీ పీ" to "otp", "పిన్" to "pin", "సీవీవీ" to "cvv", "పాస్వర్డ్" to "password",
        "కోడ్" to "code", "వెరిఫికేషన్" to "verification",
        "ఎనీడెస్క్" to "anydesk", "ఎనిడెస్క్" to "anydesk", "ఎ్నిడెస్క్" to "anydesk", "ఎన్నిడెస్క్" to "anydesk", "ఎనీ డెస్క్" to "anydesk", "ఎని డెస్క్" to "anydesk",
        "టీమ్ వ్యూయర్" to "teamviewer", "టీమ్వ్యూయర్" to "teamviewer",
        "డౌన్లోడ్" to "download", "డౌనలోడ్" to "download", "దావున్లోడ్" to "download", "ఇన్స్టాల్" to "install",
        "కేవైసీ" to "kyc", "కేవైసి" to "kyc", "కెవైసి" to "kyc", "కెవైసీ" to "kyc",
        // account / SIM
        "ఖాతా" to "account", "కాత" to "account", "అకౌంట్" to "account", "అకౌంటు" to "account",
        "బ్లాక్" to "block", "బ్లోక్" to "block", "బ్లాకు" to "block", "బ్లోక" to "block", "బ్లాక" to "block", "బ్లొక్" to "block", "బంద్" to "band",
        "సిమ్ కార్డ్" to "sim card", "సిమ్కార్డ్" to "sim card", "సింకార్డ్" to "sim card", "సిమ్" to "sim", "కార్డ్" to "card", "బ్యాంక్" to "bank", "బ్యాంకు" to "bank",
        "గడువు ముగిసింది" to "expired", "గడువు మొగిసింది" to "expired", "గడువు ముగిసిపోయింది" to "expired", "గడువు తీరింది" to "expired",
        // authority
        "డిజిటల్ అరెస్ట్" to "digital arrest", "డిజిటల్ అరెస్టు" to "digital arrest", "అరెస్ట్" to "arrest", "అరెస్టు" to "arrest",
        "పోలీసులు" to "police", "పోలీస్" to "police", "పోలీసు" to "police", "సీబీఐ" to "cbi", "సbi" to "cbi", "వారెంట్" to "warrant", "కేసు" to "case",
        "కస్టమ్స్" to "customs", "నార్కోటిక్స్" to "narcotics",
        // payment / lure
        "లాటరీ" to "lottery", "బహుమతి" to "prize", "రీఫండ్" to "refund", "రిఫండ్" to "refund",
        "చెల్లింపు" to "payment", "చెల్లించండి" to "payment", "చెళ్లించండి" to "payment", "ఫీజు" to "fee", "ఫీజీ" to "fee", "ట్రాన్స్ఫర్" to "transfer",
        // pressure
        "అత్యవసరం" to "urgent", "అధ్యవసరం" to "urgent", "అత్యవసర" to "urgent", "తక్షణం" to "immediately", "వెంటనే" to "immediately", "రహస్యం" to "secret",
        // function words used by the rewrite patterns
        "చెప్పకండి" to "cheppakandi", "చెప్పకూడదు" to "cheppakandi", "చెప్పొద్దు" to "cheppakandi", "చెప్పండి" to "cheppandi",
        "ఇవ్వకండి" to "ivvakandi", "ఇవ్వొద్దు" to "ivvakandi", "ఇవ్వండి" to "ivvandi",
        "ఎవరికీ" to "evariki", "ఎవరికి" to "evariki", "ఎవరికైనా" to "evariki",
        "ఎప్పుడూ" to "eppudu", "ఎప్పుడు" to "eppudu", "ఎన్నడూ" to "eppudu",
        "షేర్ చేయకండి" to "share cheyakandi", "షేర్ చేయవద్దు" to "share cheyakandi", "షేర్" to "share",
        "అవుతుందని" to "avutundi", "అవుతుంది" to "avutundi", "అవుతోంది" to "avutundi", "అబుతుంది" to "avutundi", "అవుతున్నది" to "avutundi",
    ).map { (k, v) -> nfc(k) to v }
        .sortedByDescending { it.first.length }
        .map { (k, v) -> Regex("(?<![$TE])${Regex.escape(k)}") to " $v " }

    fun containsTelugu(s: String) = hasTelugu.containsMatchIn(s)

    fun teluguToLatin(text: String): String {
        if (!containsTelugu(text)) return text
        var s = nfc(text)
        for ((re, to) in lexicon) s = re.replace(s, to)
        return s
    }

    private const val TERM = "(otp|pin|cvv|cvc|verification code|passcode|password)"
    private const val NO = "(?:cheppakandi|ivvakandi|share cheyakandi)"
    private val advisory = Regex("\\bevariki (?:eppudu )?(?:mee )?$TERM (?:eppudu )?$NO\\b|\\b(?:mee )?$TERM (?:evariki )?(?:eppudu )?(?:evariki )?$NO\\b")
    private val secrecy = Regex("\\bevariki (?:eppudu )?(?:cheppakandi|ivvakandi)\\b")
    private val blocked = Regex("\\b(?:block|band) avutundi\\b")

    /** Spellings Whisper produces for Telugu speech in Latin letters. */
    private val variants = listOf(
        Regex("\\b(?:yavari ?ki|evarikee?|evarikaina)\\b") to "evariki",
        Regex("\\b(?:yeppadu|eppudoo?|eppudu)\\b") to "eppudu",
        Regex("\\bcheppak\\w*\\b") to "cheppakandi",
        Regex("\\b(?:cheppandi|cheppandhi)\\b") to "cheppandi",
    )

    fun rewrite(s: String): String {
        var t = s
        for ((re, to) in variants) t = re.replace(t, to)
        // an advisory rewrite must consume the term, so it can be recognised by AdvisoryContext as "never share <term>"
        t = advisory.replace(t) { m -> "never share ${m.groupValues.filter { it.isNotEmpty() }.drop(1).first { it in setOf("otp", "pin", "cvv", "cvc", "verification code", "passcode", "password") }}" }
        t = secrecy.replace(t, "do not tell anyone")
        t = blocked.replace(t, "blocked")
        return t
    }
}
