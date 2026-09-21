package com.callguard.core

/** Things that must be true for CallGuard to protect a call without anyone opening the app. */
enum class HealthItem(val required: Boolean) {
    MIC_PERMISSION(true), PHONE_PERMISSION(true), NOTIFICATIONS(true), PROTECTION_RUNNING(true),
    CALLER_ID_ROLE(false), BATTERY_EXEMPTION(false), MODELS(false),
}

data class HealthInputs(
    val mic: Boolean, val phone: Boolean,
    /** True also when the phone's Android version does not need the permission. */
    val notifications: Boolean,
    val running: Boolean, val callerIdRole: Boolean, val batteryExempt: Boolean,
    /** True when the on-device language models are installed (optional: rules and Whisper work without them). */
    val models: Boolean,
)

data class HealthReport(val missing: List<HealthItem>) {
    /** Required items that are missing: protection is not working. */
    val blocking: List<HealthItem> get() = missing.filter { it.required }
    /** Optional items that would make protection better or more reliable. */
    val recommended: List<HealthItem> get() = missing.filter { !it.required }
    val protectionOn: Boolean get() = blocking.isEmpty()
    /** The first thing to fix: blocking items first, in the order they are listed. */
    val next: HealthItem? get() = blocking.firstOrNull() ?: recommended.firstOrNull()
}

object HealthCheck {
    fun evaluate(i: HealthInputs): HealthReport = HealthReport(HealthItem.values().filter { item ->
        when (item) {
            HealthItem.MIC_PERMISSION -> !i.mic
            HealthItem.PHONE_PERMISSION -> !i.phone
            HealthItem.NOTIFICATIONS -> !i.notifications
            HealthItem.PROTECTION_RUNNING -> !i.running
            HealthItem.CALLER_ID_ROLE -> !i.callerIdRole
            HealthItem.BATTERY_EXEMPTION -> !i.batteryExempt
            HealthItem.MODELS -> !i.models
        }
    })
}

/** Localised wording for the health card and the setup screen. */
object HealthText {
    private data class T(val name: Triple<String, String, String>, val why: Triple<String, String, String>, val fix: Triple<String, String, String>)
    private fun t(en: String, hi: String, te: String) = Triple(en, hi, te)

    private val items: Map<HealthItem, T> = mapOf(
        HealthItem.MIC_PERMISSION to T(
            t("Let CallGuard hear the call", "CallGuard को कॉल सुनने दें", "CallGuard కాల్ వినడానికి అనుమతించండి"),
            t("CallGuard listens to the call through the microphone. This happens only on your phone.", "CallGuard माइक्रोफ़ोन से कॉल सुनता है। यह सिर्फ़ आपके फ़ोन पर होता है।", "CallGuard మైక్రోఫోన్ ద్వారా కాల్ వింటుంది. ఇది మీ ఫోన్‌లోనే జరుగుతుంది."),
            t("Allow", "अनुमति दें", "అనుమతించండి")),
        HealthItem.PHONE_PERMISSION to T(
            t("Know when a call starts", "कॉल कब शुरू हो, यह जानना", "కాల్ ఎప్పుడు మొదలవుతుందో తెలుసుకోవడం"),
            t("To know when a call starts and ends.", "यह जानने के लिए कि कॉल कब शुरू और ख़त्म होती है।", "కాల్ ఎప్పుడు మొదలై ఎప్పుడు ముగుస్తుందో తెలుసుకోవడానికి."),
            t("Allow", "अनुमति दें", "అనుమతించండి")),
        HealthItem.NOTIFICATIONS to T(
            t("Show warnings", "चेतावनी दिखाना", "హెచ్చరికలు చూపడం"),
            t("To warn you about risky calls and show the call summary.", "जोखिम भरी कॉल की चेतावनी और कॉल का सारांश दिखाने के लिए।", "ప్రమాదకర కాల్స్ గురించి హెచ్చరించడానికి, కాల్ సారాంశం చూపడానికి."),
            t("Allow", "अनुमति दें", "అనుమతించండి")),
        HealthItem.PROTECTION_RUNNING to T(
            t("Protection on", "सुरक्षा चालू", "రక్షణ ఆన్"),
            t("CallGuard must be running to protect your calls. It needs one tap after every restart.", "कॉल बचाने के लिए CallGuard का चलता रहना ज़रूरी है। हर रीस्टार्ट के बाद एक बार दबाना होगा।", "కాల్స్‌ను రక్షించడానికి CallGuard నడుస్తూ ఉండాలి. ప్రతి రీస్టార్ట్ తర్వాత ఒకసారి నొక్కాలి."),
            t("Turn on protection", "सुरक्षा चालू करें", "రక్షణను ఆన్ చేయండి")),
        HealthItem.CALLER_ID_ROLE to T(
            t("Warn me before I answer", "उठाने से पहले मुझे चेतावनी दें", "నేను ఎత్తే ముందే నన్ను హెచ్చరించండి"),
            t("Warns you before you answer if the number looks risky.", "नंबर जोखिम भरा लगे तो उठाने से पहले चेतावनी देता है।", "నంబర్ ప్రమాదకరంగా అనిపిస్తే ఫోన్ ఎత్తే ముందే హెచ్చరిస్తుంది."),
            t("Turn on", "चालू करें", "ఆన్ చేయండి")),
        HealthItem.BATTERY_EXEMPTION to T(
            t("Keep protection running", "सुरक्षा चलती रखें", "రక్షణను నడుస్తూ ఉంచండి"),
            t("So your phone does not switch CallGuard off when it is idle.", "ताकि फ़ोन खाली होने पर CallGuard बंद न हो जाए।", "ఫోన్ ఖాళీగా ఉన్నప్పుడు CallGuard ఆగిపోకుండా ఉండటానికి."),
            t("Allow", "अनुमति दें", "అనుమతించండి")),
        HealthItem.MODELS to T(
            t("Download my language", "मेरी भाषा डाउनलोड करें", "నా భాషను డౌన్‌లోడ్ చేయండి"),
            t("Lets CallGuard understand calls in Hindi and Telugu. English works without it.", "CallGuard को हिंदी और तेलुगु कॉल समझने देता है। इसके बिना अंग्रेज़ी चलती है।", "CallGuard కు హిందీ, తెలుగు కాల్స్ అర్థమయ్యేలా చేస్తుంది. ఇది లేకపోయినా ఆంగ్లం పనిచేస్తుంది."),
            t("Get language files", "भाषा फ़ाइलें लें", "భాషా ఫైళ్లను పొందండి")),
    )

    private fun <X> pick(tr: Triple<X, X, X>, l: Lang) = when (l) { Lang.EN -> tr.first; Lang.HI -> tr.second; Lang.TE -> tr.third }
    fun name(i: HealthItem, l: Lang) = pick(items.getValue(i).name, l)
    fun why(i: HealthItem, l: Lang) = pick(items.getValue(i).why, l)
    fun fix(i: HealthItem, l: Lang) = pick(items.getValue(i).fix, l)

    fun cardTitle(r: HealthReport, l: Lang): String = when {
        !r.protectionOn -> when (l) { Lang.EN -> "Needs attention"; Lang.HI -> "ध्यान देना ज़रूरी है"; Lang.TE -> "శ్రద్ధ అవసరం" }
        else -> when (l) { Lang.EN -> "Protection is ON"; Lang.HI -> "सुरक्षा चालू है"; Lang.TE -> "రక్షణ ఆన్‌లో ఉంది" }
    }

    fun cardBody(r: HealthReport, l: Lang): String = when {
        !r.protectionOn -> r.blocking.first().let { if (it == HealthItem.PROTECTION_RUNNING) fix(it, l) else name(it, l) } // what to do, in plain words
        r.recommended.isEmpty() -> when (l) { Lang.EN -> "Everything is set up."; Lang.HI -> "सब कुछ तैयार है।"; Lang.TE -> "అన్నీ సిద్ధంగా ఉన్నాయి." }
        else -> when (l) {
            Lang.EN -> "${r.recommended.size} improvement${if (r.recommended.size == 1) "" else "s"} recommended"
            Lang.HI -> "${r.recommended.size} सुधार सुझाए गए"
            Lang.TE -> "${r.recommended.size} మెరుగుదలలు సూచించబడ్డాయి"
        }
    }

    fun setupButton(l: Lang) = when (l) { Lang.EN -> "Get ready"; Lang.HI -> "तैयार हों"; Lang.TE -> "సిద్ధం అవ్వండి" }
    fun setupTitle(l: Lang) = when (l) { Lang.EN -> "Get ready"; Lang.HI -> "तैयार हों"; Lang.TE -> "సిద్ధం అవ్వండి" }
    fun setupIntro(l: Lang) = when (l) {
        Lang.EN -> "Do each step once so CallGuard protects your calls without opening the app."
        Lang.HI -> "हर चरण एक बार पूरा करें, ताकि ऐप खोले बिना CallGuard आपकी कॉल की सुरक्षा करे।"
        Lang.TE -> "ప్రతి దశను ఒకసారి పూర్తి చేయండి, యాప్ తెరవకుండానే CallGuard మీ కాల్స్‌ను రక్షిస్తుంది."
    }
    fun done(l: Lang) = when (l) { Lang.EN -> "Done"; Lang.HI -> "हो गया"; Lang.TE -> "పూర్తయింది" }
    fun autostartName(l: Lang) = when (l) { Lang.EN -> "Keep protection running on this phone"; Lang.HI -> "इस फ़ोन पर सुरक्षा चलती रखें"; Lang.TE -> "ఈ ఫోన్‌లో రక్షణను నడుస్తూ ఉంచండి" }
    fun autostartWhy(l: Lang) = when (l) {
        Lang.EN -> "Your phone may switch CallGuard off to save battery. Open the settings and allow CallGuard to run in the background. Come back and tap \"I have done this\"."
        Lang.HI -> "आपका फ़ोन बैटरी बचाने के लिए CallGuard को बंद कर सकता है। सेटिंग खोलकर CallGuard को बैकग्राउंड में चलने की अनुमति दें। फिर लौटकर \"मैंने यह कर लिया\" दबाएं।"
        Lang.TE -> "బ్యాటరీ ఆదా చేయడానికి మీ ఫోన్ CallGuard ను ఆపివేయవచ్చు. సెట్టింగ్‌లు తెరిచి CallGuard ను బ్యాక్‌గ్రౌండ్‌లో నడవడానికి అనుమతించండి. తిరిగి వచ్చి \"నేను ఇది చేశాను\" నొక్కండి."
    }
    fun openSettings(l: Lang) = when (l) { Lang.EN -> "Open settings"; Lang.HI -> "सेटिंग खोलें"; Lang.TE -> "సెట్టింగ్‌లు తెరవండి" }
}

/**
 * Where each brand hides its "allow in the background" page. Component names come from community knowledge of vendor
 * settings apps and change between versions, so they are tried in order and the caller falls back to the app's own
 * settings page. Samsung has no auto-start list; its page is Battery > Background usage limits.
 */
object BrandAutostart {
    data class Target(val pkg: String, val cls: String)

    private val byBrand: List<Pair<List<String>, List<Target>>> = listOf(
        listOf("xiaomi", "redmi", "poco") to listOf(Target("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        listOf("vivo", "iqoo") to listOf(
            Target("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            Target("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Target("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
        listOf("oppo", "realme") to listOf(
            Target("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Target("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
        listOf("oneplus") to listOf(Target("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        listOf("huawei", "honor") to listOf(Target("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        listOf("samsung") to listOf(Target("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
    )

    /** Candidate settings pages for a phone maker (case-insensitive), most likely first. Empty if the brand needs nothing special. */
    fun candidates(manufacturer: String): List<Target> {
        val m = manufacturer.trim().lowercase()
        return byBrand.firstOrNull { (names, _) -> names.any { it == m || m.startsWith(it) } }?.second.orEmpty()
    }
}
