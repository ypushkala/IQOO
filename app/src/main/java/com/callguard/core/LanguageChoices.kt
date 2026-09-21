package com.callguard.core

/** What language the *summary* is shown and read in. */
enum class SummaryLanguage { SAME_AS_SCREEN, ENGLISH, HINDI, TELUGU, MATCH_CALL }

/**
 * Four independent language choices, because people do not use one language for everything: someone can speak Telugu
 * on calls (so the spoken warning follows the call) yet prefer reading English summaries and menus.
 */
data class LanguageChoices(
    /** Menus, buttons and on-screen messages. */
    val screen: Lang = Lang.EN,
    /** The spoken warning (and its language when the call is in Telugu/Hindi). */
    val spoken: LanguageSetting = LanguageSetting.AUTO,
    /** The post-call summary card, its read-aloud, and the notification. */
    val summary: SummaryLanguage = SummaryLanguage.SAME_AS_SCREEN,
    /** The pre-written message sent to the family contact. */
    val familyMessage: Lang = Lang.EN,
)

object LanguageResolver {
    /** [callLang] is the language heard on the current/last call (English until Hindi or Telugu is heard). */
    fun spoken(c: LanguageChoices, callLang: Lang): Lang = when (c.spoken) {
        LanguageSetting.AUTO -> callLang
        LanguageSetting.ENGLISH -> Lang.EN
        LanguageSetting.HINDI -> Lang.HI
        LanguageSetting.TELUGU -> Lang.TE
    }

    fun summary(c: LanguageChoices, callLang: Lang): Lang = when (c.summary) {
        SummaryLanguage.SAME_AS_SCREEN -> c.screen
        SummaryLanguage.ENGLISH -> Lang.EN
        SummaryLanguage.HINDI -> Lang.HI
        SummaryLanguage.TELUGU -> Lang.TE
        SummaryLanguage.MATCH_CALL -> callLang
    }

    fun summaryChoiceFor(lang: Lang): SummaryLanguage = when (lang) { Lang.EN -> SummaryLanguage.ENGLISH; Lang.HI -> SummaryLanguage.HINDI; Lang.TE -> SummaryLanguage.TELUGU }

    /** The language the summary toggle flips to next (English -> Hindi -> Telugu -> English). */
    fun next(lang: Lang): Lang = when (lang) { Lang.EN -> Lang.HI; Lang.HI -> Lang.TE; Lang.TE -> Lang.EN }
}
