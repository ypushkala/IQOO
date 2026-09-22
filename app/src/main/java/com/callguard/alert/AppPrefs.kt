package com.callguard.alert

import android.content.Context
import com.callguard.core.AnalyseScope
import com.callguard.core.FamilyContact
import com.callguard.core.FamilyContacts
import com.callguard.core.Lang
import com.callguard.core.LanguageChoices
import com.callguard.core.LanguageSetting
import com.callguard.core.MyLanguage
import com.callguard.core.SummaryLanguage

/**
 * Per-device settings in private app storage (excluded from backup, never uploaded): accessibility
 * mode, warning language, and the trusted family contact the user picked.
 */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("callguard_prefs", Context.MODE_PRIVATE)

    var accessibility: Boolean
        get() = prefs.getBoolean("accessibility_mode", false)
        set(v) { prefs.edit().putBoolean("accessibility_mode", v).apply() }

    /** The spoken-warning language (stored under the original "language" key, so existing choices carry over). */
    var language: LanguageSetting
        get() = runCatching { LanguageSetting.valueOf(prefs.getString("language", "AUTO")!!) }.getOrDefault(LanguageSetting.AUTO)
        set(v) { prefs.edit().putString("language", v.name).apply() }

    var screenLanguage: Lang
        get() = runCatching { Lang.valueOf(prefs.getString("screen_language", "EN")!!) }.getOrDefault(Lang.EN)
        set(v) { prefs.edit().putString("screen_language", v.name).apply() }

    var summaryLanguage: SummaryLanguage
        get() = runCatching { SummaryLanguage.valueOf(prefs.getString("summary_language", "SAME_AS_SCREEN")!!) }.getOrDefault(SummaryLanguage.SAME_AS_SCREEN)
        set(v) { prefs.edit().putString("summary_language", v.name).apply() }

    var familyMessageLanguage: Lang
        get() = runCatching { Lang.valueOf(prefs.getString("family_message_language", "EN")!!) }.getOrDefault(Lang.EN)
        set(v) { prefs.edit().putString("family_message_language", v.name).apply() }

    /** False until the user has seen the Languages page once (first run). */
    var languagesChosen: Boolean
        get() = prefs.getBoolean("languages_chosen", false)
        set(v) { prefs.edit().putBoolean("languages_chosen", v).apply() }

    /** Short periodic beep during analysed calls so the other party can tell. On by default; a legal review is needed before release. */
    var disclosureBeep: Boolean
        get() = prefs.getBoolean("disclosure_beep", true)
        set(v) { prefs.edit().putBoolean("disclosure_beep", v).apply() }

    /** The auto-start / background page cannot be read back, so the user confirms it by hand. */
    var autostartAcknowledged: Boolean
        get() = prefs.getBoolean("autostart_ack", false)
        set(v) { prefs.edit().putBoolean("autostart_ack", v).apply() }

    /** The setup wizard opens by itself once, on first run; after that it is one tap away from the health card. */
    var setupOffered: Boolean
        get() = prefs.getBoolean("setup_offered", false)
        set(v) { prefs.edit().putBoolean("setup_offered", v).apply() }

    // ---- "Get ready" wizard state ----
    var modelsDeferred: Boolean          // "English only for now" chosen on the language-files step
        get() = prefs.getBoolean("models_deferred", false); set(v) { prefs.edit().putBoolean("models_deferred", v).apply() }
    var roleDeclined: Boolean            // the caller-ID role was offered and declined: never leave the user stuck on it
        get() = prefs.getBoolean("role_declined", false); set(v) { prefs.edit().putBoolean("role_declined", v).apply() }
    var batteryAsked: Boolean
        get() = prefs.getBoolean("battery_asked", false); set(v) { prefs.edit().putBoolean("battery_asked", v).apply() }
    var permsAsked: Int                  // after two refusals Android stops asking; the wizard then opens the app's settings page
        get() = prefs.getInt("perms_asked", 0); set(v) { prefs.edit().putInt("perms_asked", v).apply() }
    var practiceDone: Boolean
        get() = prefs.getBoolean("practice_done", false); set(v) { prefs.edit().putBoolean("practice_done", v).apply() }
    var setupStartedAt: Long             // 0 = the wizard has not been opened yet
        get() = prefs.getLong("setup_started", 0); set(v) { prefs.edit().putLong("setup_started", v).apply() }
    var setupTaps: Int
        get() = prefs.getInt("setup_taps", 0); set(v) { prefs.edit().putInt("setup_taps", v).apply() }
    var setupRuns: String
        get() = prefs.getString("setup_runs", "") ?: ""; set(v) { prefs.edit().putString("setup_runs", v).apply() }

    /** Which calls to analyse. Default: unknown numbers only (recommended). */
    var analyseScope: AnalyseScope
        get() = runCatching { AnalyseScope.valueOf(prefs.getString("analyse_scope", "UNKNOWN_ONLY")!!) }.getOrDefault(AnalyseScope.UNKNOWN_ONLY)
        set(v) { prefs.edit().putString("analyse_scope", v.name).apply() }

    /** The user's own bank fraud helpline. Entered by the user, never bundled (a wrong number here would be dangerous). */
    var bankHelpline: String
        get() = prefs.getString("bank_helpline", "") ?: ""
        set(v) { prefs.edit().putString("bank_helpline", v.filter { it.isDigit() || it == '+' }).apply() }

    /** Automatic family SMS: off until the user agrees on the consent screen. */
    var autoFamilyAlert: Boolean
        get() = prefs.getBoolean("auto_family_alert", false)
        set(v) { prefs.edit().putBoolean("auto_family_alert", v).apply() }
    var includeCallerInAlert: Boolean
        get() = prefs.getBoolean("alert_include_caller", true)
        set(v) { prefs.edit().putBoolean("alert_include_caller", v).apply() }
    var familyAlertLog: String
        get() = prefs.getString("family_alert_log", "") ?: ""
        set(v) { prefs.edit().putString("family_alert_log", v).apply() }

    /** One tap sets all four language settings (see [MyLanguage]). */
    fun setMyLanguage(lang: Lang) {
        val c = MyLanguage.choices(lang)
        screenLanguage = c.screen; language = c.spoken; summaryLanguage = c.summary; familyMessageLanguage = c.familyMessage
        languagesChosen = true
    }

    val choices: LanguageChoices get() = LanguageChoices(screenLanguage, language, summaryLanguage, familyMessageLanguage)

    /** Up to three family contacts. The single contact saved by older versions is carried over. */
    var familyContacts: List<FamilyContact>
        get() {
            val stored = prefs.getString("family_contacts", null)
            if (stored != null) return FamilyContacts.decode(stored)
            val n = prefs.getString("family_number", null) ?: return emptyList()
            return listOf(FamilyContact(prefs.getString("family_name", null), n))
        }
        set(v) { prefs.edit().putString("family_contacts", FamilyContacts.encode(v)).remove("family_name").remove("family_number").apply() }

    fun addFamily(name: String?, number: String) { familyContacts = FamilyContacts.add(familyContacts, FamilyContact(name, number)) }
    fun removeFamily(c: FamilyContact) { familyContacts = FamilyContacts.remove(familyContacts, c) }

    // ---- Wave 2: ambient trust, history, follow-through ----
    /** Calls the caller-ID service saw from an unknown number while protection was off. */
    var unprotectedCallLog: String
        get() = prefs.getString("unprotected_calls", "") ?: ""; set(v) { prefs.edit().putString("unprotected_calls", v).apply() }
    var callHistory: String
        get() = prefs.getString("call_history", "") ?: ""; set(v) { prefs.edit().putString("call_history", v).apply() }
    var missedScamReports: String
        get() = prefs.getString("missed_scam_reports", "") ?: ""; set(v) { prefs.edit().putString("missed_scam_reports", v).apply() }
    /** Accessibility mode was suggested once already (the phone's own large-text setting was on); do not nag again. */
    var accessibilitySuggested: Boolean
        get() = prefs.getBoolean("accessibility_suggested", false); set(v) { prefs.edit().putBoolean("accessibility_suggested", v).apply() }
    var trustStepSeen: Boolean
        get() = prefs.getBoolean("trust_step_seen", false); set(v) { prefs.edit().putBoolean("trust_step_seen", v).apply() }
}
