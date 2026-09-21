package com.callguard.core

/** One line of the bundled prefix list: numbers whose E.164 digits start with [prefix] get [weight] risk points. */
data class ScamPrefix(val prefix: String, val weight: Int, val label: String)

/** Parses `prefix|weight|label` lines (digits only prefix, e.g. `92|2|label`); `#` starts a comment; bad lines are skipped. */
object ScamPrefixList {
    fun parse(text: String): List<ScamPrefix> = text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val p = line.split('|').map { it.trim() }
            val prefix = p.getOrNull(0)?.removePrefix("+")?.filter { it.isDigit() }.orEmpty()
            val weight = p.getOrNull(1)?.toIntOrNull()
            if (prefix.isEmpty() || weight == null || weight !in 1..5) null else ScamPrefix(prefix, weight, p.getOrElse(2) { "listed prefix" })
        }.toList()
}

/** Why a number scored the way it did. Kept as codes so the heads-up can be shown in any language. */
enum class NumberReason(val en: String, val hi: String, val te: String) {
    WITHHELD("number withheld", "नंबर छिपाया गया है", "నంబర్ దాచబడింది"),
    INTERNATIONAL("international number", "अंतरराष्ट्रीय नंबर", "అంతర్జాతీయ నంబర్"),
    SERIES_160("160-series institutional number", "160 श्रृंखला का संस्थागत नंबर", "160 సిరీస్ సంస్థాగత నంబర్"),
    SERIES_140("promotional 140-series", "140 श्रृंखला का प्रचार नंबर", "140 సిరీస్ ప్రచార నంబర్"),
    SHORT_CODE("short code on a voice call", "वॉइस कॉल पर छोटा कोड नंबर", "వాయిస్ కాల్‌లో చిన్న కోడ్ నంబర్"),
    LISTED_PREFIX("listed prefix", "स्कैम में अक्सर दिखने वाली नंबर श्रेणी", "స్కామ్‌లలో తరచుగా కనిపించే నంబర్ శ్రేణి"),
    VERIFY_FAILED("caller-ID verification failed (possible spoofing)", "कॉलर-आईडी सत्यापन विफल (नकली नंबर हो सकता है)", "కాలర్-ఐడీ ధృవీకరణ విఫలమైంది (నకిలీ నంబర్ కావచ్చు)"),
    VERIFIED("caller-ID verified", "कॉलर-आईडी सत्यापित", "కాలర్-ఐడీ ధృవీకరించబడింది"),
    IN_CONTACTS("saved contact", "सहेजा हुआ संपर्क", "సేవ్ చేసిన కాంటాక్ట్"),
    NOT_IN_CONTACTS("not in your contacts", "आपके संपर्कों में नहीं है", "మీ కాంటాక్ట్‌లలో లేదు"),
    FIRST_CALL("first call from this number", "इस नंबर से पहली कॉल", "ఈ నంబర్ నుండి మొదటి కాల్"),
    USER_SAFE("you marked this number as safe", "आपने इस नंबर को सुरक्षित चिह्नित किया है", "మీరు ఈ నంబర్‌ను సురక్షితంగా గుర్తించారు");

    fun text(lang: Lang) = when (lang) { Lang.EN -> en; Lang.HI -> hi; Lang.TE -> te }
}

enum class Verification { UNKNOWN, PASSED, FAILED }

enum class NumberRisk { NORMAL, ELEVATED, HIGH }

/** What Android (and, on-device only, the contacts and our own history) tells us about the caller. */
data class CallerContext(
    /** null or blank = the network withheld the number. */
    val number: String?,
    /** null = unknown (no contacts permission). */
    val inContacts: Boolean? = null,
    /** null = unknown. True = CallGuard has never seen this number before. */
    val firstTime: Boolean? = null,
    val verification: Verification = Verification.UNKNOWN,
    /** The user answered "fine" about this number after a previous call. */
    val userTrusted: Boolean = false,
)

data class NumberAssessment(
    val kind: NumberKind,
    val score: Int,
    val level: NumberRisk,
    val reasons: List<String>,
    /** The same reasons as codes, so they can be shown in another language. */
    val reasonCodes: List<NumberReason> = emptyList(),
    val inContacts: Boolean?,
    val displayNumber: String,
    /** Salted hash of the number, set by the caller-ID service; null when unknown. */
    val numberHash: String? = null,
) {
    /** True when a caller claiming to be a bank could be checked against the 160/toll-free institutional ranges. */
    val isInstitutionalSeries: Boolean get() = kind == NumberKind.SERIES_160
    /** A number that cannot back a bank/institution claim (and is not a saved contact). */
    val cannotBackInstitutionClaim: Boolean
        get() = inContacts != true && kind !in setOf(NumberKind.SERIES_160, NumberKind.TOLL_FREE)
    val summary: String get() = "$displayNumber · ${kind.label}" + (if (reasons.isNotEmpty()) " · " + reasons.joinToString(", ") else "")
}

/**
 * Offline caller-number reputation: structure of the number + the bundled prefix list + contacts +
 * first-time + verification status. It only *scores*; how much that matters is decided in
 * [RiskEngine] (a number alone never raises an alert).
 */
class NumberReputation(private val prefixes: List<ScamPrefix> = emptyList()) {
    fun assess(ctx: CallerContext): NumberAssessment {
        val parsed = NumberParser.parse(ctx.number)
        var score = 0
        val why = ArrayList<String>()
        val codes = ArrayList<NumberReason>()
        fun add(points: Int, reason: NumberReason, en: String = reason.en) { score += points; why += en; codes += reason }

        when (parsed.kind) {
            NumberKind.PRIVATE -> add(2, NumberReason.WITHHELD)
            NumberKind.INTERNATIONAL -> add(3, NumberReason.INTERNATIONAL)
            NumberKind.SERIES_160 -> add(-2, NumberReason.SERIES_160)
            NumberKind.SERIES_140 -> add(1, NumberReason.SERIES_140)
            NumberKind.SHORT_CODE -> add(1, NumberReason.SHORT_CODE)
            else -> Unit
        }
        prefixes.filter { parsed.e164Digits.startsWith(it.prefix) }.maxByOrNull { it.prefix.length }?.let { add(it.weight, NumberReason.LISTED_PREFIX, it.label) }
        when (ctx.verification) {
            Verification.FAILED -> add(4, NumberReason.VERIFY_FAILED)
            Verification.PASSED -> add(-1, NumberReason.VERIFIED)
            Verification.UNKNOWN -> Unit
        }
        when (ctx.inContacts) {
            true -> add(-4, NumberReason.IN_CONTACTS)
            false -> add(1, NumberReason.NOT_IN_CONTACTS)
            null -> Unit
        }
        if (ctx.firstTime == true) add(1, NumberReason.FIRST_CALL)
        if (ctx.userTrusted) add(-3, NumberReason.USER_SAFE)

        val level = when {
            score >= 4 -> NumberRisk.HIGH
            score >= 2 -> NumberRisk.ELEVATED
            else -> NumberRisk.NORMAL
        }
        val shown = if (parsed.kind == NumberKind.PRIVATE) "Hidden number" else ctx.number!!.trim()
        return NumberAssessment(parsed.kind, score, level, why, codes, ctx.inContacts, shown)
    }
}
