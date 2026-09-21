package com.callguard.core

/** How a warning is delivered: what is said (and in which language), how fast, how loud, and how it buzzes. */
data class WarningPlan(
    val messages: List<String>,
    val speechRate: Float,
    /** Play on the alarm stream at maximum volume (restored afterwards) so it cuts through a loud call. */
    val boostVolume: Boolean,
    val vibration: LongArray,
    val lang: Lang = Lang.EN,
) {
    override fun equals(other: Any?) = other is WarningPlan && messages == other.messages && speechRate == other.speechRate &&
        boostVolume == other.boostVolume && vibration.contentEquals(other.vibration) && lang == other.lang
    override fun hashCode() = messages.hashCode() * 31 + vibration.contentHashCode()
}

/** Which language warnings and the summary use. */
enum class LanguageSetting { AUTO, ENGLISH, HINDI, TELUGU }

object WarningPlans {
    // Normal mode speaks a SHORT warning (about 5 s): while it plays, the caller's words are masked (two voices at once cannot be
    // separated by the recogniser). The full advice stays on screen and in the summary. Accessibility mode keeps the long,
    // repeated version on purpose, because people who need it benefit from the complete instruction.
    // Wording avoids words the detector listens for (codes, OTP, "tell anyone", "install <app>" ...), because with the
    // phone on speaker the microphone can hear the warning itself. A test runs every message through the engine.
    private const val HIGH_STD = "Warning! This looks like a scam. Do not share codes. Hang up."
    private const val MED_STD = "Caution. This may be a scam. Do not share personal details."
    private const val HIGH_ACC = "Danger! This is a scam call. Hang up now. Do not give out any codes. Do not install any apps."
    private const val MED_ACC = "Be careful! This call may be a scam. Do not give out personal details."

    private const val HIGH_STD_HI = "चेतावनी! यह ठगी लग रही है। कोई कोड न बताएं। फ़ोन काट दें।"
    private const val MED_STD_HI = "सावधान! यह ठगी हो सकती है। जानकारी न दें।"
    private const val HIGH_ACC_HI = "ख़तरा! यह धोखाधड़ी वाला कॉल है। फ़ोन काट दें। कोई कोड न दें। कोई ऐप इंस्टॉल न करें।"
    private const val MED_ACC_HI = "सावधान रहें! यह कॉल धोखाधड़ी हो सकती है। अपनी निजी जानकारी न दें।"

    private const val HIGH_STD_TE = "హెచ్చరిక! ఇది మోసంలా ఉంది. కోడ్‌లు చెప్పకండి. ఫోన్ కట్ చేయండి."
    private const val MED_STD_TE = "జాగ్రత్త! ఇది మోసం కావచ్చు. వ్యక్తిగత వివరాలు ఇవ్వకండి."
    private const val HIGH_ACC_TE = "ప్రమాదం! ఇది మోసపు కాల్. వెంటనే ఫోన్ కట్ చేయండి. ఎలాంటి కోడ్‌లు ఇవ్వకండి. ఏ యాప్‌లూ ఇన్‌స్టాల్ చేయకండి."
    private const val MED_ACC_TE = "జాగ్రత్తగా ఉండండి! ఈ కాల్ మోసం కావచ్చు. మీ వ్యక్తిగత వివరాలు ఇవ్వకండి."

    private fun text(level: RiskLevel, accessible: Boolean, lang: Lang): String {
        val high = level == RiskLevel.HIGH
        return when (lang) {
            Lang.EN -> if (accessible) (if (high) HIGH_ACC else MED_ACC) else (if (high) HIGH_STD else MED_STD)
            Lang.HI -> if (accessible) (if (high) HIGH_ACC_HI else MED_ACC_HI) else (if (high) HIGH_STD_HI else MED_STD_HI)
            Lang.TE -> if (accessible) (if (high) HIGH_ACC_TE else MED_ACC_TE) else (if (high) HIGH_STD_TE else MED_STD_TE)
        }
    }

    fun plan(level: RiskLevel, accessible: Boolean, lang: Lang = Lang.EN): WarningPlan {
        val high = level == RiskLevel.HIGH
        return if (!accessible) {
            WarningPlan(
                messages = listOf(text(level, false, lang)),
                speechRate = 1.0f, boostVolume = false,
                vibration = if (high) longArrayOf(0, 500, 150, 500, 150, 500) else longArrayOf(0, 400), lang = lang,
            )
        } else {
            val m = text(level, true, lang)
            WarningPlan(
                messages = listOf(m, m), speechRate = 0.85f, boostVolume = true,
                vibration = if (high) longArrayOf(0, 800, 200, 800, 200, 800, 200, 800) else longArrayOf(0, 700, 200, 700), lang = lang,
            )
        }
    }

    val allMessages: List<String> = listOf(HIGH_STD, MED_STD, HIGH_ACC, MED_ACC, HIGH_STD_HI, MED_STD_HI, HIGH_ACC_HI, MED_ACC_HI, HIGH_STD_TE, MED_STD_TE, HIGH_ACC_TE, MED_ACC_TE)
}

/**
 * Decides warning/summary language. AUTO follows the call: the language of the most recent segments
 * (script of the transcript, detected language code, or common Hinglish / romanised-Telugu words).
 */
class CallLanguageTracker(private val window: Int = 3) {
    private val recent = ArrayDeque<Lang?>()

    fun onSegment(native: String, detectedLang: String) {
        recent.addLast(spokenLang(native, detectedLang))
        while (recent.size > window) recent.removeFirst()
    }

    /** The most recent non-English language heard in the window, or English. */
    fun callLanguage(): Lang = recent.lastOrNull { it != null } ?: Lang.EN
    fun callLooksHindi(): Boolean = recent.any { it == Lang.HI }
    fun reset() = recent.clear()

    fun resolve(setting: LanguageSetting): Lang = when (setting) {
        LanguageSetting.ENGLISH -> Lang.EN
        LanguageSetting.HINDI -> Lang.HI
        LanguageSetting.TELUGU -> Lang.TE
        LanguageSetting.AUTO -> callLanguage()
    }

    companion object {
        private val hinglishWords = Regex("\\b(?:aap|aapka|aapki|aapko|hai|hain|hoon|hun|kijiye|bataiye|batao|batana|kisi|nahi|jayega|raha|karna|kya|mera|apna)\\b")
        private val teluguWords = Regex("\\b(?:evariki|yavari|cheppandi|cheppakandi|cheppak\\w*|ivvandi|ivvakandi|mee|meeru|avutundi|yeppudu|eppudu|chala|idi|nenu|cheyandi)\\b")

        /** The non-English language a segment looks like, or null for English. */
        fun spokenLang(native: String, detectedLang: String): Lang? {
            IndicScript.langOf(IndicScript.detect(native))?.let { return it }
            val lower = native.lowercase()
            return when {
                detectedLang == "te" || teluguWords.containsMatchIn(lower) -> Lang.TE
                detectedLang == "hi" || detectedLang == "ur" || hinglishWords.containsMatchIn(lower) -> Lang.HI
                else -> null
            }
        }

        fun isHindiLike(native: String, lang: String): Boolean = spokenLang(native, lang) == Lang.HI
    }
}
