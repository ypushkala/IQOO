package com.callguard.core

import java.text.Normalizer

/**
 * Maps Devanagari and Hinglish (Hindi in Latin letters) onto the canonical English vocabulary the
 * rules already use, so a single rule set covers English, Hindi and Hinglish.
 *
 *  1. [scriptsToLatin]: known scam vocabulary and Hindi function words written in Devanagari or in
 *     Urdu (Arabic) script become Latin tokens ("ओटीपी" -> "otp", "किसी को" -> "kisi ko",
 *     "ایندسک" -> "anydesk"); Whisper often writes Hindi speech in Urdu script. Unknown script text is
 *     left for the caller to drop.
 *  2. [hinglishToEnglish]: Hindi word order and negation are rewritten into the English phrases the
 *     rules and [AdvisoryContext] understand ("kisi ko apna otp kabhi mat batana" -> "never share otp",
 *     "account block ho jayega" -> "account blocked").
 */
object HindiNormalizer {
    /** Devanagari letters and signs, excluding the danda/double danda (U+0964/0965), which end sentences. */
    private const val DEV = "\\u0900-\\u0963\\u0966-\\u097F"
    private val hasDevanagari = Regex("[$DEV]")

    fun containsDevanagari(s: String) = hasDevanagari.containsMatchIn(s)

    // ---- Urdu (Arabic script) -> Latin ------------------------------------------------------
    private const val ARABIC = "\\u0600-\\u06FF\\u0750-\\u077F"
    private val hasArabic = Regex("[$ARABIC]")
    private val arabicMarks = Regex("[\\u064B-\\u065F\\u0670\\u200C\\u200D]")

    /** Folds Arabic-script letter variants so one spelling of each word is enough in the lexicon. */
    private fun foldArabic(s: String) = arabicMarks.replace(
        s.replace('\u064A', '\u06CC').replace('\u0649', '\u06CC').replace('\u0643', '\u06A9').replace('\u06C1', '\u06C1'), "",
    )

    private val urduLexicon: List<Pair<Regex, String>> = listOf(
        "ایندسک" to "anydesk", "اینڈسک" to "anydesk", "اینی ڈیسک" to "anydesk", "اینی ڈسک" to "anydesk", "ٹیم ویور" to "teamviewer",
        "او ٹی پی" to "otp", "اوٹی پی" to "otp", "اوٹیپی" to "otp", "کے وائی سی" to "kyc", "سی بی آئی" to "cbi",
        "ڈیجیٹل ارسٹ" to "digital arrest", "ڈیجیٹل اریسٹ" to "digital arrest", "گرفتار" to "arrest", "پولیس" to "police",
        "کسی کو" to "kisi ko", "خسی کو" to "kisi ko", "کسی سے" to "kisi se", "اپنا" to "apna", "اپنار" to "apna", "اپنی" to "apni",
        "آپ کا" to "aapka", "آپکا" to "aapka", "کبھی" to "kabhi", "مت" to "mat", "میت" to "mat", "مات" to "mat", "نہیں" to "nahi", "نہی" to "nahi",
        "بتانا" to "batana", "بتاؤ" to "batana", "بتائیں" to "batana", "بتایئے" to "batana", "بتائیے" to "batana", "بتا" to "batana",
        "دینا" to "dena", "دیجیے" to "dena", "ہو جائے گا" to "ho jayega", "ہوجائے گا" to "ho jayega", "ہو گیا" to "ho gaya", "ہوگیا" to "ho gaya",
        "سم" to "sim", "کارڈ" to "card", "بلاک" to "block", "بند" to "band", "اکاؤنٹ" to "account", "اکاونٹ" to "account",
        "کھاتا" to "account", "کھاتہ" to "account", "ایکسپائر" to "expire", "ختم" to "khatam",
        "لاٹری" to "lottery", "انعام" to "prize", "ریفنڈ" to "refund", "پیمنٹ" to "payment",
        "فوراً" to "immediately", "فوری" to "immediately", "جلدی" to "immediately", "ضروری" to "urgent",
    ).map { (k, v) -> foldArabic(k) to v }
        .sortedByDescending { it.first.length }
        .map { (k, v) -> Regex("(?<![$ARABIC])${Regex.escape(k)}(?![$ARABIC])") to " $v " }

    /** Devanagari and Urdu-script vocabulary -> Latin tokens. Text with neither is returned untouched. */
    fun scriptsToLatin(text: String): String {
        var s = text
        if (hasArabic.containsMatchIn(s)) {
            s = foldArabic(s)
            for ((re, to) in urduLexicon) s = re.replace(s, to)
        }
        return devanagariToLatin(s)
    }

    // ---- Devanagari -> Latin --------------------------------------------------------------
    private val lexicon: List<Pair<Regex, String>> = listOf(
        // phrases
        "वन टाइम पासवर्ड" to "otp", "डिजिटल अरेस्ट" to "digital arrest", "वेरिफिकेशन कोड" to "verification code",
        "रिमोट एक्सेस" to "remote access", "स्क्रीन शेयर" to "screen share", "क्विक सपोर्ट" to "quicksupport",
        "एनी डेस्क" to "anydesk", "टीम व्यूअर" to "teamviewer", "के वाई सी" to "kyc", "ओ टी पी" to "otp",
        "सी बी आई" to "cbi", "साइबर क्राइम" to "cyber crime", "प्रोसेसिंग फीस" to "processing fee",
        "किसी को" to "kisi ko", "किसी से" to "kisi se", "से" to "se", "बोल" to "bol", "बात" to "baat", "कॉल" to "call", "बता दीजिए" to "batana", "बता दीजिये" to "batana",
        "हो जाएगा" to "ho jayega", "हो जायेगा" to "ho jayega", "हो जाएगी" to "ho jayega", "हो जाएँगे" to "ho jayega",
        "हो गया" to "ho gaya", "हो गई" to "ho gaya", "हो गयी" to "ho gaya", "हो गए" to "ho gaya",
        // spellings the Omnilingual model writes for Hindi/Hinglish speech (phonetic Devanagari, sometimes mixed with Latin letters)
        "दिजितल अरिस्ट" to "digital arrest", "डिजिटल अरिस्ट" to "digital arrest", "और tीpी" to "otp", "और तीपी" to "otp", "otीpी" to "otp",
        "रoटीपी" to "otp", "औवतीपी" to "otp", "ओ टीपी" to "otp", "एनी देस" to "anydesk", "एनी डेस" to "anydesk", "एनेडेस्क" to "anydesk",
        "तीमव्यूर" to "teamviewer", "डाउन लोड" to "download", "डाउन लोर्ड" to "download", "दाउनलोड" to "download",
        "सिमकार्ड" to "sim card", "सिमकार्" to "sim card", "लौत्री" to "lottery", "काइक" to "kyc", "वॉरंट" to "warrant", "वारंट" to "warrant",
        "तुरंट" to "immediately", "मैट" to "mat", "माँगता" to "mangta", "मांगता" to "mangta", "मालता" to "mangta",
        "होने वाला है" to "ho jayega", "होने वाला" to "ho jayega", "जायएगा" to "jayega", "हो" to "ho",
        // credentials / remote access / KYC
        "ओटीपी" to "otp", "ओटिपी" to "otp", "पिन" to "pin", "सीवीवी" to "cvv", "कोड" to "code", "पासवर्ड" to "password",
        "एनीडेस्क" to "anydesk", "एनिडेस्क" to "anydesk", "टीमव्यूअर" to "teamviewer", "टीमव्यूवर" to "teamviewer",
        "क्विकसपोर्ट" to "quicksupport", "डाउनलोड" to "download", "इंस्टॉल" to "install",
        "केवाईसी" to "kyc", "केवायसी" to "kyc", "एक्सपायर" to "expire", "एक्सपायर्ड" to "expire",
        "खत्म" to "khatam", "ख़त्म" to "khatam", "समाप्त" to "khatam",
        // account / SIM
        "खाता" to "account", "खाते" to "account", "अकाउंट" to "account", "अकाउन्ट" to "account", "सिम" to "sim",
        "कार्ड" to "card", "बैंक" to "bank", "नंबर" to "number", "बंद" to "band", "बन्द" to "band",
        "ब्लॉक" to "block", "ब्लॉक्ड" to "block",
        // authority
        "पुलिस" to "police", "सीबीआई" to "cbi", "कस्टम" to "customs", "कस्टम्स" to "customs", "नारकोटिक्स" to "narcotics",
        "अरेस्ट" to "arrest", "गिरफ्तार" to "arrest", "गिरफ़्तार" to "arrest", "गिरफ्तारी" to "arrest",
        "वारंट" to "warrant", "केस" to "case", "मामला" to "case",
        // payment / lure
        "लॉटरी" to "lottery", "इनाम" to "prize", "रिफंड" to "refund", "पेमेंट" to "payment", "ट्रांसफर" to "transfer",
        "कैशबैक" to "cashback", "फीस" to "fee",
        // pressure
        "तुरंत" to "immediately", "फौरन" to "immediately", "फ़ौरन" to "immediately",
        "ज़रूरी" to "urgent", "जरूरी" to "urgent", "गुप्त" to "secret", "राज़" to "raaz", "सीक्रेट" to "secret", "रखना" to "rakhna",
        // function words used by the negation / request patterns
        "कभी" to "kabhi", "मत" to "mat", "नहीं" to "nahi", "नही" to "nahi", "न" to "na",
        "बताना" to "batana", "बताइए" to "batana", "बताइये" to "batana", "बताएं" to "batana", "बताएँ" to "batana",
        "बतायें" to "batana", "बताओ" to "batana", "बता" to "batana", "दीजिए" to "dena", "दीजिये" to "dena",
        "देना" to "dena", "अपना" to "apna", "अपनी" to "apni", "आपका" to "aapka", "आपकी" to "aapki",
        "शेयर" to "share", "कीजिए" to "karna", "कीजिये" to "karna", "करना" to "karna", "करें" to "karna", "करो" to "karna",
    ).map { (k, v) -> nfc(k) to v }
        .sortedByDescending { it.first.length } // longest phrase first
        .map { (k, v) -> Regex("(?<![$DEV])${Regex.escape(k)}(?![$DEV])") to " $v " }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    fun devanagariToLatin(text: String): String {
        if (!containsDevanagari(text)) return text
        var s = nfc(text)
        for ((re, to) in lexicon) s = re.replace(s, to)
        return s
    }

    // ---- Hinglish word order / negation -> English phrases ---------------------------------
    private const val TERM = "(otp|pin|cvv|cvc|verification code|passcode|password)"
    private const val TELL = "(?:batana|batao|bataiye|bataiyega|bataye|batayein|bataen|dena|dijiye|do|de|share karna|share karo|share kare|share karein|share|bhejna|bhejo)"
    private const val NEG = "(?:mat|nahi|nhi|na)"
    private const val ASK = "(?:mangta|mangte|maangta|maangte|mangti|maangti|poochta|puchta|puchte|poochte)"

    private val advisorySelf = Regex(
        "\\b(?:kisi (?:ko|se) )?(?:apna |apni |apne |aapka |aapki )?(?:kabhi )?$TERM (?:kisi (?:ko|se) )?(?:kabhi )?$NEG (?:kisi (?:ko|se) )?$TELL\\b"
    )
    /** "kisi ko apna otp kabhi mat ..." with a garbled or missing verb: the negation alone makes it a warning. */
    private val advisoryLoose = Regex(
        "\\bkisi (?:ko|se) (?:apna |apni |apne |aapka |aapki )?$TERM(?: \\w+){0,2} (?:mat|mata|nahi|nhi)\\b"
    )
    private val advisoryBank = Regex("\\b(?:kabhi )?$TERM (?:kabhi )?(?:nahi|nhi|mat) $ASK\\b")
    private val secrecyTell = Regex("\\bkisi (?:ko|se) $NEG (?:batana|batao|bataiye|bataiyega|bataye|batayein|bataen|kehna|bolna|bolo)\\b")
    private val secrecyKeep = Regex("\\b(?:secret|raaz|raz|gupt|confidential) (?:rakhna|rakhiye|rakhiyega|rakhen|rakho)\\b")
    private val stateChange = Regex(
        "\\b(block|blocked|band|khatam|expire|expired|suspend|suspended) ho " +
            "(?:jayega|jaega|jayegi|jaegi|jaaega|jayenge|jaenge|gaya|gayi|gaye|chuka hai|chuki hai)\\b"
    )
    private val bandKarDiya = Regex("\\b(?:band|block) kar diya (?:jayega|jaega|gaya|jaaega)\\b")
    private val urgent = Regex("\\b(?:turant|fauran|jaldi se|jaldi)\\b")
    private val arrestWords = Regex("\\b(?:girftar|giraftar|girftaar|girfaar)(?:i)?\\b")

    /** Spelling variants seen in real Whisper output for Hindi speech (it glues and mis-spells short words). */
    private val variants = listOf(
        Regex("\\b(?:khisi|kissi|kesi)\\b") to "kisi",
        Regex("\\bkisiko\\b") to "kisi ko",
        Regex("\\b(?:apnar|apnaa|aapna)\\b") to "apna",
        Regex("\\bkabhimat\\b") to "kabhi mat",
        Regex("\\bkomat\\b") to "ko mat",
        Regex("\\blotri\\b") to "lottery",
        Regex("\\bpulis\\b") to "police",                          // common Hinglish romanisations
        Regex("\\b(?:warant|warrent|varant|varrant)\\b") to "warrant",
        Regex("\\b(?:k|ka|apka|aapka)otp\\b") to "otp",          // "aapka OTP" run together
        Regex("\\baccountage\\b") to "account",
        // Whisper garbles of "OTP batao" / "KYC khatam ho gaya" / "khata band ho jayega" seen on the device
        Regex("\\b(?:tp|otb|ootp|utp|otpi|otep)\\b(?= bata)") to "otp",
        Regex("\\b(?:ke|ki|k) (?:vaisi|vaisy|waisi|vaisee)\\b") to "kyc",
        Regex("\\b(?:khatm|krthm|khtm)\\w*") to "khatam ho gaya",
        Regex("\\bkhat[ae]\\b") to "account",
        Regex("\\bbanth\\w*") to "band ho jayega",
        Regex("\\bh[uo]j?a[yi]?[ie]?ga\\b") to "ho jayega",       // hojayega / hujayiga / hojaega ...
    )

    fun hinglishToEnglish(s: String): String {
        var t = s
        for ((re, to) in variants) t = re.replace(t, to)
        t = advisorySelf.replace(t) { "never share ${it.groupValues[1]}" }
        t = advisoryLoose.replace(t) { "never share ${it.groupValues[1]}" }
        t = advisoryBank.replace(t) { "never ask for ${it.groupValues[1]}" }
        t = secrecyTell.replace(t, "do not tell anyone")
        t = secrecyKeep.replace(t, "keep this secret")
        t = stateChange.replace(t) {
            when (it.groupValues[1]) {
                "block", "blocked", "band" -> "blocked"
                "suspend", "suspended" -> "suspended"
                else -> "expired"
            }
        }
        t = bandKarDiya.replace(t, "blocked")
        t = urgent.replace(t, "immediately")
        t = arrestWords.replace(t, "arrest")
        return t.replace(Regex("\\binaam\\b"), "prize")
    }
}
