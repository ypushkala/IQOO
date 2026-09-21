package com.callguard.core

/**
 * "My language": one choice that sets all four language settings sensibly. The spoken warning follows the call (AUTO), the
 * summary and the family message use the person's language. The four separate settings stay available under "Advanced".
 */
object MyLanguage {
    fun choices(lang: Lang) = LanguageChoices(screen = lang, spoken = LanguageSetting.AUTO, summary = SummaryLanguage.SAME_AS_SCREEN, familyMessage = lang)
}
