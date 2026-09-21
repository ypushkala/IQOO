package com.callguard.core

/** What the caller got out of the user. The user ticks these; the checklist adapts. */
enum class Situation { SHARED_CODE, INSTALLED_APP, PAID_MONEY }

/** What tapping a step does. Nothing here needs the internet permission: the portal opens in the browser. */
enum class StepAction { CALL_BANK, CALL_1930, OPEN_PORTAL, OPEN_APP_SETTINGS, NONE }

enum class StepId { CALL_BANK, BLOCK_CARD_UPI, UNINSTALL_APP, CHANGE_PASSWORDS, CALL_1930, REPORT_PORTAL, HANG_UP_STAY_CALM }

data class RecoveryStep(val id: StepId, val action: StepAction)

/**
 * Ordered "I already fell for it" checklist. Order matters: money and access first, reporting after.
 * No bank number is bundled: the user enters their own bank's helpline (never guessed).
 */
object RecoveryPlan {
    const val PORTAL_URL = "https://cybercrime.gov.in"
    const val HELPLINE = "1930"

    fun steps(s: Set<Situation>): List<RecoveryStep> {
        val out = ArrayList<RecoveryStep>()
        if (s.isEmpty()) return listOf(RecoveryStep(StepId.HANG_UP_STAY_CALM, StepAction.NONE), RecoveryStep(StepId.CALL_1930, StepAction.CALL_1930))
        // a live remote-access session lets them keep emptying the account, so cut it first
        if (Situation.INSTALLED_APP in s) out += RecoveryStep(StepId.UNINSTALL_APP, StepAction.OPEN_APP_SETTINGS)
        if (Situation.PAID_MONEY in s) out += RecoveryStep(StepId.CALL_1930, StepAction.CALL_1930) // the golden hour: 1930 first when money moved
        if (Situation.SHARED_CODE in s || Situation.PAID_MONEY in s) {
            out += RecoveryStep(StepId.CALL_BANK, StepAction.CALL_BANK)
            out += RecoveryStep(StepId.BLOCK_CARD_UPI, StepAction.NONE)
        }
        if (Situation.INSTALLED_APP in s) {
            out += RecoveryStep(StepId.CHANGE_PASSWORDS, StepAction.NONE)
            if (Situation.SHARED_CODE !in s && Situation.PAID_MONEY !in s) out += RecoveryStep(StepId.CALL_BANK, StepAction.CALL_BANK)
        }
        if (Situation.SHARED_CODE in s && Situation.INSTALLED_APP !in s) out += RecoveryStep(StepId.CHANGE_PASSWORDS, StepAction.NONE)
        if (Situation.PAID_MONEY !in s) out += RecoveryStep(StepId.CALL_1930, StepAction.CALL_1930)
        out += RecoveryStep(StepId.REPORT_PORTAL, StepAction.OPEN_PORTAL)
        return out.distinctBy { it.id }
    }

    /** Situations worth pre-selecting from what the call contained (the user can change them). */
    fun likelySituations(tactics: Set<Tactic>): Set<Situation> = buildSet {
        if (Tactic.CREDENTIAL_REQUEST in tactics) add(Situation.SHARED_CODE)
        if (Tactic.REMOTE_ACCESS in tactics) add(Situation.INSTALLED_APP)
    }.let { if (it.isEmpty()) emptySet() else it }

    /** The single most urgent advice line for the top of the summary card, or null when the call had no serious tactic. */
    fun urgentTactic(tactics: Set<Tactic>): Tactic? = listOf(Tactic.REMOTE_ACCESS, Tactic.CREDENTIAL_REQUEST, Tactic.AUTHORITY_THREAT, Tactic.ACCOUNT_THREAT, Tactic.PAYMENT_LURE)
        .firstOrNull { it in tactics }

    fun situationLabel(s: Situation, lang: Lang): String = when (s) {
        Situation.SHARED_CODE -> when (lang) { Lang.EN -> "I shared an OTP, PIN or CVV"; Lang.HI -> "मैंने OTP, PIN या CVV बता दिया"; Lang.TE -> "నేను OTP, PIN లేదా CVV చెప్పాను" }
        Situation.INSTALLED_APP -> when (lang) { Lang.EN -> "I installed an app they asked for"; Lang.HI -> "मैंने उनके कहने पर ऐप इंस्टॉल किया"; Lang.TE -> "వారు చెప్పిన యాప్ ఇన్‌స్టాల్ చేశాను" }
        Situation.PAID_MONEY -> when (lang) { Lang.EN -> "I sent money"; Lang.HI -> "मैंने पैसे भेज दिए"; Lang.TE -> "నేను డబ్బు పంపాను" }
    }

    fun stepText(id: StepId, lang: Lang, bankSet: Boolean): String = when (id) {
        StepId.HANG_UP_STAY_CALM -> when (lang) {
            Lang.EN -> "Hang up. Do not call the number back. Nothing has been lost yet if you shared nothing."
            Lang.HI -> "फ़ोन काट दें। उसी नंबर पर वापस कॉल न करें। कुछ साझा नहीं किया तो कुछ नहीं खोया।"
            Lang.TE -> "ఫోన్ కట్ చేయండి. అదే నంబర్‌కు తిరిగి కాల్ చేయకండి. ఏమీ చెప్పకపోతే ఏమీ కోల్పోలేదు."
        }
        StepId.CALL_BANK -> when (lang) {
            Lang.EN -> if (bankSet) "Call your bank's fraud helpline now (your saved number)." else "Call your bank's fraud helpline now. Use the number on your card or the bank's official app."
            Lang.HI -> if (bankSet) "अभी अपने बैंक की फ़्रॉड हेल्पलाइन पर कॉल करें (आपका सहेजा नंबर)।" else "अभी अपने बैंक की फ़्रॉड हेल्पलाइन पर कॉल करें। कार्ड पर छपा नंबर या बैंक का आधिकारिक ऐप इस्तेमाल करें।"
            Lang.TE -> if (bankSet) "ఇప్పుడే మీ బ్యాంక్ ఫ్రాడ్ హెల్ప్‌లైన్‌కు కాల్ చేయండి (మీరు సేవ్ చేసిన నంబర్)." else "ఇప్పుడే మీ బ్యాంక్ ఫ్రాడ్ హెల్ప్‌లైన్‌కు కాల్ చేయండి. కార్డుపై ఉన్న నంబర్ లేదా బ్యాంక్ అధికారిక యాప్ వాడండి."
        }
        StepId.BLOCK_CARD_UPI -> when (lang) {
            Lang.EN -> "Ask the bank to block the card and UPI, and to freeze transfers."
            Lang.HI -> "बैंक से कार्ड और UPI ब्लॉक करने और ट्रांसफ़र रोकने को कहें।"
            Lang.TE -> "కార్డ్, UPI బ్లాక్ చేసి, ట్రాన్స్‌ఫర్లు ఆపమని బ్యాంక్‌ను అడగండి."
        }
        StepId.UNINSTALL_APP -> when (lang) {
            Lang.EN -> "Uninstall the app they made you install (AnyDesk, TeamViewer, QuickSupport). Turn off screen sharing."
            Lang.HI -> "जो ऐप उन्होंने इंस्टॉल कराया (AnyDesk, TeamViewer, QuickSupport) उसे हटाएं। स्क्रीन शेयरिंग बंद करें।"
            Lang.TE -> "వారు ఇన్‌స్టాల్ చేయించిన యాప్ (AnyDesk, TeamViewer, QuickSupport) తొలగించండి. స్క్రీన్ షేరింగ్ ఆపండి."
        }
        StepId.CHANGE_PASSWORDS -> when (lang) {
            Lang.EN -> "Change your banking and UPI PINs and passwords from a safe device."
            Lang.HI -> "सुरक्षित डिवाइस से अपने बैंकिंग और UPI PIN व पासवर्ड बदलें।"
            Lang.TE -> "సురక్షిత పరికరం నుండి మీ బ్యాంకింగ్, UPI PIN‌లు, పాస్‌వర్డ్‌లు మార్చండి."
        }
        StepId.CALL_1930 -> when (lang) {
            Lang.EN -> "Call 1930, the national cyber-fraud helpline. Report quickly: early reports can help freeze money."
            Lang.HI -> "राष्ट्रीय साइबर-फ़्रॉड हेल्पलाइन 1930 पर कॉल करें। जल्दी शिकायत करें: जल्दी करने से पैसे रुक सकते हैं।"
            Lang.TE -> "జాతీయ సైబర్-మోసం హెల్ప్‌లైన్ 1930కి కాల్ చేయండి. త్వరగా ఫిర్యాదు చేయండి: త్వరగా చేస్తే డబ్బు ఆపే అవకాశం ఉంటుంది."
        }
        StepId.REPORT_PORTAL -> when (lang) {
            Lang.EN -> "Report at cybercrime.gov.in (opens your browser)."
            Lang.HI -> "cybercrime.gov.in पर शिकायत करें (ब्राउज़र खुलेगा)।"
            Lang.TE -> "cybercrime.gov.inలో ఫిర్యాదు చేయండి (బ్రౌజర్ తెరుచుకుంటుంది)."
        }
    }
}
