package com.callguard.core

/** One rehearsal scenario: a pretend caller line and the tactic it demonstrates. */
data class PracticeScenario(val id: String, val tactic: Tactic, val lineEn: String, val lineHi: String, val lineTe: String) {
    fun line(lang: Lang): String = when (lang) { Lang.EN -> lineEn; Lang.HI -> lineHi; Lang.TE -> lineTe }
}

/** A small library of common scam openers, turning the practice call into standing scam-literacy, not just one demo line. */
object PracticeScenarios {
    val all = listOf(
        PracticeScenario("otp", Tactic.CREDENTIAL_REQUEST,
            "This is your bank. Please tell me your OTP immediately.",
            "आपका ओटीपी बताइए, यह बहुत ज़रूरी है।",
            "మీ otp చెప్పండి ఇది చాలా అధ్యవసరం"),
        PracticeScenario("arrest", Tactic.AUTHORITY_THREAT,
            "This is the CBI. There is a case against you. You are under digital arrest, do not tell anyone.",
            "मैं सीबीआई से बोल रहा हूँ, आप डिजिटल अरेस्ट में हैं, किसी को मत बताना।",
            "నేను సిబిఐ నుండి మాట్లాడుతున్నాను, మీరు డిజిటల్ అరెస్ట్‌లో ఉన్నారు, ఎవరికీ చెప్పకండి."),
        // Real lottery scams almost always pair the fee request with a threat, which is what actually crosses the alert
        // threshold (a bare "pay a fee to claim a prize" is deliberately too weak alone, to avoid false alarms on real refunds).
        PracticeScenario("lottery", Tactic.PAYMENT_LURE,
            "Congratulations, you have won a lottery prize of ten lakh rupees. Pay the processing fee today or your account will be blocked.",
            "बधाई हो, आपने दस लाख रुपये की लॉटरी जीती है। फ़ीस न भरी तो आपका खाता आज बंद हो जाएगा।",
            "అభినందనలు, మీరు పది లక్షల రూపాయల లాటరీ గెలిచారు. ఫీజు కట్టకపోతే మీ ఖాతా ఈ రోజు బ్లాక్ అవుతుంది."),
        PracticeScenario("kyc", Tactic.ACCOUNT_THREAT,
            "Your KYC has expired and your account will be blocked today unless you verify now.",
            "आपकी केवाईसी खत्म हो गई है, आज सत्यापित नहीं किया तो खाता बंद हो जाएगा।",
            "మీ KYC గడువు ముగిసింది, ఈరోజు ధృవీకరించకపోతే ఖాతా బ్లాక్ అవుతుంది."),
    )

    fun byId(id: String): PracticeScenario = all.firstOrNull { it.id == id } ?: all.first()
}
