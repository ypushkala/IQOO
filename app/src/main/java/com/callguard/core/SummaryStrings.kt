package com.callguard.core

enum class Lang(val tag: String) { EN("en"), HI("hi"), TE("te") }

/** All user-facing text that exists in more than one language: summary, advice, family message, tactic names. */
object Strings {
    private fun <T> pick(lang: Lang, en: T, hi: T, te: T): T = when (lang) { Lang.EN -> en; Lang.HI -> hi; Lang.TE -> te }

    fun tactic(t: Tactic, lang: Lang): String = when (lang) {
        Lang.EN -> t.label
        Lang.HI -> when (t) {
            Tactic.CREDENTIAL_REQUEST -> "ओटीपी/गोपनीय कोड माँगना"
            Tactic.REMOTE_ACCESS -> "रिमोट-एक्सेस ऐप (AnyDesk आदि)"
            Tactic.ACCOUNT_THREAT -> "केवाईसी/खाता/सिम बंद होने की धमकी"
            Tactic.AUTHORITY_THREAT -> "पुलिस/सरकारी अधिकारी बनकर डराना"
            Tactic.PAYMENT_LURE -> "इनाम/पैसे का लालच"
            Tactic.URGENCY -> "जल्दबाज़ी का दबाव"
            Tactic.SECRECY -> "किसी को न बताने का दबाव"
            Tactic.OTHER -> "संदिग्ध बात"
            Tactic.NUMBER_RISK -> "संदिग्ध कॉलर नंबर"
            Tactic.IDENTITY_MISMATCH -> "अपनी पहचान की पुष्टि न होना"
        }
        Lang.TE -> when (t) {
            Tactic.CREDENTIAL_REQUEST -> "ఓటీపీ/రహస్య కోడ్ అడగడం"
            Tactic.REMOTE_ACCESS -> "రిమోట్-యాక్సెస్ యాప్ (AnyDesk మొదలైనవి)"
            Tactic.ACCOUNT_THREAT -> "కేవైసీ/ఖాతా/సిమ్ బ్లాక్ అవుతుందని బెదిరింపు"
            Tactic.AUTHORITY_THREAT -> "పోలీసు/అధికారిగా నటించి భయపెట్టడం"
            Tactic.PAYMENT_LURE -> "బహుమతి/డబ్బు ఆశ చూపడం"
            Tactic.URGENCY -> "తొందర పెట్టడం"
            Tactic.SECRECY -> "ఎవరికీ చెప్పవద్దని ఒత్తిడి"
            Tactic.OTHER -> "అనుమానాస్పద విషయం"
            Tactic.NUMBER_RISK -> "అనుమానాస్పద కాలర్ నంబర్"
            Tactic.IDENTITY_MISMATCH -> "గుర్తింపు నిర్ధారణ కాకపోవడం"
        }
    }

    fun headline(level: RiskLevel, lang: Lang): String = when (lang) {
        Lang.EN -> when (level) {
            RiskLevel.HIGH -> "High risk: this call looked like a scam"
            RiskLevel.MEDIUM -> "Suspicious call: be careful"
            RiskLevel.LOW -> "Nothing suspicious was detected"
        }
        Lang.HI -> when (level) {
            RiskLevel.HIGH -> "बहुत ख़तरा: यह कॉल धोखाधड़ी लग रही थी"
            RiskLevel.MEDIUM -> "संदिग्ध कॉल: सावधान रहें"
            RiskLevel.LOW -> "कुछ भी संदिग्ध नहीं मिला"
        }
        Lang.TE -> when (level) {
            RiskLevel.HIGH -> "చాలా ప్రమాదం: ఈ కాల్ మోసంలా అనిపించింది"
            RiskLevel.MEDIUM -> "అనుమానాస్పద కాల్: జాగ్రత్తగా ఉండండి"
            RiskLevel.LOW -> "అనుమానాస్పదంగా ఏమీ కనిపించలేదు"
        }
    }

    fun levelWord(level: RiskLevel, lang: Lang): String = when (lang) {
        Lang.EN -> level.name.lowercase()
        Lang.HI -> when (level) { RiskLevel.HIGH -> "अधिक ख़तरा"; RiskLevel.MEDIUM -> "संदिग्ध"; RiskLevel.LOW -> "कम" }
        Lang.TE -> when (level) { RiskLevel.HIGH -> "అధిక ప్రమాదం"; RiskLevel.MEDIUM -> "అనుమానాస్పదం"; RiskLevel.LOW -> "తక్కువ" }
    }

    fun callAt(time: String, duration: String, lang: Lang) = pick(lang,
        "Call at $time, lasted $duration.", "कॉल का समय: $time, अवधि $duration।", "కాల్ సమయం: $time, వ్యవధి $duration.")
    fun caller(s: String, lang: Lang) = pick(lang, "Caller: $s", "कॉलर: $s", "కాలర్: $s")
    fun tacticsHeader(lang: Lang) = pick(lang, "This call used these tactics:", "इस कॉल में ये तरीके अपनाए गए:", "ఈ కాల్‌లో ఉపయోగించిన పద్ధతులు:")
    fun findingLine(label: String, level: String, at: String, lang: Lang) = pick(lang,
        "• $label ($level, at $at)", "• $label ($level, $at पर)", "• $label ($level, $at వద్ద)")
    fun heard(phrases: String, lang: Lang) = pick(lang, " — heard: $phrases", " — सुना गया: $phrases", " — వినిపించింది: $phrases")
    fun alsoHeard(items: String, lang: Lang) = pick(lang, "Also heard: $items.", "यह भी सुना गया: $items।", "ఇవి కూడా వినిపించాయి: $items.")
    fun safetyWarning(lang: Lang) = pick(lang,
        "A safety warning (like \"never share your OTP\") was also heard.",
        "एक सुरक्षा चेतावनी (जैसे \"अपना OTP किसी को न बताएं\") भी सुनाई दी।",
        "భద్రతా హెచ్చరిక (ఉదా: \"మీ OTP ఎవరికీ చెప్పకండి\") కూడా వినిపించింది.")
    fun warnedYou(n: Int, lang: Lang) = pick(lang,
        "CallGuard warned you $n time${if (n == 1) "" else "s"}.", "CallGuard ने आपको $n बार चेताया।", "CallGuard మిమ్మల్ని $n సార్లు హెచ్చరించింది.")
    fun whatToDo(lang: Lang) = pick(lang, "What to do:", "क्या करें:", "ఏమి చేయాలి:")

    fun months(lang: Lang): List<String> = when (lang) {
        Lang.EN -> listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        Lang.HI -> listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
        Lang.TE -> listOf("జనవరి", "ఫిబ్రవరి", "మార్చి", "ఏప్రిల్", "మే", "జూన్", "జూలై", "ఆగస్టు", "సెప్టెంబర్", "అక్టోబర్", "నవంబర్", "డిసెంబర్")
    }

    fun adviceFor(tactics: Set<Tactic>, level: RiskLevel, lang: Lang): List<String> {
        if (level == RiskLevel.LOW && tactics.isEmpty()) return listOf(pick(lang,
            "No action needed. If anything felt wrong, you can still report the number on 1930.",
            "कुछ करने की ज़रूरत नहीं। कुछ ग़लत लगा हो तो 1930 पर नंबर की शिकायत कर सकते हैं।",
            "ఏమీ చేయాల్సిన అవసరం లేదు. ఏదైనా తప్పుగా అనిపిస్తే, 1930కి నంబర్ గురించి ఫిర్యాదు చేయవచ్చు."))
        val out = ArrayList<String>()
        fun add(en: String, hi: String, te: String) { out += pick(lang, en, hi, te) }
        // most urgent first: a live remote-access session beats a shared code
        if (Tactic.REMOTE_ACCESS in tactics) add(
            "If you installed a screen-sharing app (AnyDesk, TeamViewer, QuickSupport), uninstall it now, change your banking passwords and call your bank.",
            "अगर आपने AnyDesk, TeamViewer जैसा कोई स्क्रीन-शेयरिंग ऐप इंस्टॉल किया है, तो उसे अभी हटा दें, बैंकिंग पासवर्ड बदलें और अपने बैंक को फ़ोन करें।",
            "AnyDesk, TeamViewer లాంటి స్క్రీన్-షేరింగ్ యాప్ ఇన్‌స్టాల్ చేసి ఉంటే, ఇప్పుడే దాన్ని తొలగించండి, బ్యాంకింగ్ పాస్‌వర్డ్‌లు మార్చండి, మీ బ్యాంకుకు ఫోన్ చేయండి.")
        if (Tactic.CREDENTIAL_REQUEST in tactics) add(
            "Never share an OTP, PIN or CVV with anyone. If you already did, call your bank right away and dial 1930.",
            "OTP, PIN या CVV किसी को कभी न बताएं। अगर बता दिया हो, तो तुरंत अपने बैंक को फ़ोन करें और 1930 पर कॉल करें।",
            "OTP, PIN లేదా CVV ఎవరికీ ఎప్పుడూ చెప్పకండి. ఇప్పటికే చెప్పి ఉంటే, వెంటనే మీ బ్యాంకుకు ఫోన్ చేసి 1930కి కాల్ చేయండి.")
        if (Tactic.AUTHORITY_THREAT in tactics) add(
            "Police, CBI and customs never arrest anyone over a phone call. \"Digital arrest\" does not exist. Hang up.",
            "पुलिस, CBI या कस्टम कभी फ़ोन पर गिरफ़्तारी नहीं करते। \"डिजिटल अरेस्ट\" जैसी कोई चीज़ नहीं होती। फ़ोन काट दें।",
            "పోలీసులు, CBI, కస్టమ్స్ ఫోన్ ద్వారా ఎవరినీ అరెస్ట్ చేయరు. \"డిజిటల్ అరెస్ట్\" అనేది లేనే లేదు. ఫోన్ కట్ చేయండి.")
        if (Tactic.ACCOUNT_THREAT in tactics) add(
            "Banks and telecom companies do not block accounts or SIMs by phone. Check using the official app or the number on your card.",
            "बैंक और टेलीकॉम कंपनियाँ फ़ोन पर खाता या सिम बंद नहीं करतीं। आधिकारिक ऐप या कार्ड पर छपे नंबर से जाँच करें।",
            "బ్యాంకులు, టెలికాం కంపెనీలు ఫోన్‌లో ఖాతా లేదా సిమ్‌ను బ్లాక్ చేయవు. అధికారిక యాప్ ద్వారా లేదా కార్డుపై ఉన్న నంబర్ ద్వారా సరిచూసుకోండి.")
        if (Tactic.PAYMENT_LURE in tactics) add(
            "Do not pay any fee to claim a prize, refund or lottery.",
            "इनाम, रिफ़ंड या लॉटरी के लिए कोई फ़ीस न दें।",
            "బహుమతి, రీఫండ్ లేదా లాటరీ కోసం ఎలాంటి ఫీజు చెల్లించవద్దు.")
        if (Tactic.SECRECY in tactics) add(
            "Scammers ask you to keep quiet. Tell a family member or friend about this call.",
            "ठग चुप रहने को कहते हैं। इस कॉल के बारे में परिवार के किसी सदस्य या दोस्त को ज़रूर बताएं।",
            "మోసగాళ్లు మౌనంగా ఉండమని చెబుతారు. ఈ కాల్ గురించి కుటుంబ సభ్యులకు లేదా స్నేహితులకు తప్పక చెప్పండి.")
        if (Tactic.IDENTITY_MISMATCH in tactics || Tactic.NUMBER_RISK in tactics) add(
            "Do not call this number back. Verify the caller through the organisation's official website or app.",
            "इस नंबर पर वापस फ़ोन न करें। संस्था की आधिकारिक वेबसाइट या ऐप से जाँच करें।",
            "ఈ నంబర్‌కు తిరిగి కాల్ చేయవద్దు. సంస్థ అధికారిక వెబ్‌సైట్ లేదా యాప్ ద్వారా సరిచూసుకోండి.")
        if (out.isEmpty()) add(
            "Be careful with any request for money, codes or app installs on a call you did not expect.",
            "पैसे, कोड या ऐप इंस्टॉल करने की किसी भी अनचाही माँग से सावधान रहें।",
            "ఊహించని కాల్‌లో డబ్బు, కోడ్‌లు లేదా యాప్ ఇన్‌స్టాల్ చేయమని అడిగితే జాగ్రత్తగా ఉండండి.")
        add("You can report the number to the Cyber Crime Helpline 1930 or at cybercrime.gov.in.",
            "आप इस नंबर की शिकायत साइबर क्राइम हेल्पलाइन 1930 या cybercrime.gov.in पर कर सकते हैं।",
            "ఈ నంబర్ గురించి సైబర్ క్రైమ్ హెల్ప్‌లైన్ 1930కి లేదా cybercrime.gov.in లో ఫిర్యాదు చేయవచ్చు.")
        return out
    }
}
