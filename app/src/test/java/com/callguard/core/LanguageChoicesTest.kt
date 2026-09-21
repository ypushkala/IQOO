package com.callguard.core

import org.junit.Assert.*
import org.junit.Test

class LanguageResolverTest {
    // The motivating case: speaks Telugu on calls, prefers reading English.
    private val teluguSpeakerEnglishReader = LanguageChoices(screen = Lang.EN, spoken = LanguageSetting.AUTO, summary = SummaryLanguage.SAME_AS_SCREEN, familyMessage = Lang.TE)

    @Test fun teluguCallGetsATeluguWarningAndAnEnglishSummary() {
        assertEquals(Lang.TE, LanguageResolver.spoken(teluguSpeakerEnglishReader, callLang = Lang.TE))
        assertEquals(Lang.EN, LanguageResolver.summary(teluguSpeakerEnglishReader, callLang = Lang.TE))
    }
    @Test fun defaultsAreEnglishScreenAndAutoWarning() {
        val d = LanguageChoices()
        assertEquals(Lang.EN, d.screen); assertEquals(LanguageSetting.AUTO, d.spoken); assertEquals(SummaryLanguage.SAME_AS_SCREEN, d.summary); assertEquals(Lang.EN, d.familyMessage)
        assertEquals(Lang.EN, LanguageResolver.summary(d, Lang.TE)) // an English reader is not switched to Telugu by the call
    }
    @Test fun spokenWarningMatrix() {
        for (call in Lang.values()) {
            assertEquals(call, LanguageResolver.spoken(LanguageChoices(spoken = LanguageSetting.AUTO), call))
            assertEquals(Lang.EN, LanguageResolver.spoken(LanguageChoices(spoken = LanguageSetting.ENGLISH), call))
            assertEquals(Lang.HI, LanguageResolver.spoken(LanguageChoices(spoken = LanguageSetting.HINDI), call))
            assertEquals(Lang.TE, LanguageResolver.spoken(LanguageChoices(spoken = LanguageSetting.TELUGU), call))
        }
    }
    @Test fun summaryMatrix() {
        for (screen in Lang.values()) for (call in Lang.values()) {
            assertEquals(screen, LanguageResolver.summary(LanguageChoices(screen = screen, summary = SummaryLanguage.SAME_AS_SCREEN), call))
            assertEquals(call, LanguageResolver.summary(LanguageChoices(screen = screen, summary = SummaryLanguage.MATCH_CALL), call))
            assertEquals(Lang.EN, LanguageResolver.summary(LanguageChoices(screen = screen, summary = SummaryLanguage.ENGLISH), call))
            assertEquals(Lang.HI, LanguageResolver.summary(LanguageChoices(screen = screen, summary = SummaryLanguage.HINDI), call))
            assertEquals(Lang.TE, LanguageResolver.summary(LanguageChoices(screen = screen, summary = SummaryLanguage.TELUGU), call))
        }
    }
    @Test fun theSummaryToggleCyclesAndCanBeSaved() {
        assertEquals(Lang.HI, LanguageResolver.next(Lang.EN)); assertEquals(Lang.TE, LanguageResolver.next(Lang.HI)); assertEquals(Lang.EN, LanguageResolver.next(Lang.TE))
        for (l in Lang.values()) assertEquals(l, LanguageResolver.summary(LanguageChoices(summary = LanguageResolver.summaryChoiceFor(l)), Lang.EN))
    }
    @Test fun eachSettingIsIndependent() {
        val c = LanguageChoices(screen = Lang.TE, spoken = LanguageSetting.HINDI, summary = SummaryLanguage.ENGLISH, familyMessage = Lang.HI)
        assertEquals(Lang.HI, LanguageResolver.spoken(c, Lang.EN)); assertEquals(Lang.EN, LanguageResolver.summary(c, Lang.TE)); assertEquals(Lang.TE, c.screen)
    }
}

class UiStringsTest {
    private fun dev(s: String) = s.any { it.code in 0x0900..0x097F }
    private fun tel(s: String) = s.any { it.code in 0x0C00..0x0C7F }
    private fun placeholders(s: String) = Regex("%s").findAll(s).count()

    @Test fun everyKeyIsDefinedInEveryLanguage() {
        for (k in Ui.values()) for (l in Lang.values()) assertTrue("$k/$l", UiStrings.get(k, l).isNotBlank())
    }
    @Test fun hindiAndTeluguAreReallyInTheirScript() {
        for (k in Ui.values()) { assertTrue("$k hi", dev(UiStrings.get(k, Lang.HI))); assertTrue("$k te", tel(UiStrings.get(k, Lang.TE))) }
    }
    @Test fun englishIsNeverInTheOtherLanguagesColumn() {
        for (k in Ui.values()) { assertFalse("$k", tel(UiStrings.get(k, Lang.EN)) || dev(UiStrings.get(k, Lang.EN))) }
    }
    @Test fun placeholdersMatchAcrossLanguages() {
        for (k in Ui.values()) { val n = placeholders(UiStrings.get(k, Lang.EN)); for (l in Lang.values()) assertEquals("$k/$l", n, placeholders(UiStrings.get(k, l))) }
    }
    @Test fun formattedStringsWork() {
        assertEquals("Family contact: Amma (tap to change)", UiStrings.fmt(Ui.FAMILY_CONTACT_FMT, Lang.EN, "Amma"))
        assertTrue("Amma" in UiStrings.fmt(Ui.FAMILY_CONTACT_FMT, Lang.TE, "Amma"))
        assertEquals("No Telugu voice is installed on this phone", UiStrings.fmt(Ui.NO_VOICE_FMT, Lang.EN, "Telugu"))
    }
    @Test fun riskAndAlertLines() {
        for (l in Lang.values()) for (lv in RiskLevel.values()) assertTrue(UiStrings.risk(lv, l).isNotBlank())
        assertEquals("RISK: HIGH", UiStrings.risk(RiskLevel.HIGH, Lang.EN)); assertEquals("", UiStrings.alertLine(RiskLevel.LOW, Lang.TE))
        assertTrue(tel(UiStrings.alertLine(RiskLevel.HIGH, Lang.TE))); assertTrue(dev(UiStrings.alertLine(RiskLevel.MEDIUM, Lang.HI)))
    }
    @Test fun languageNamesAreTheirOwnAndOptionsCoverEverySetting() {
        assertEquals("తెలుగు", UiStrings.name(Lang.TE)); assertEquals("हिन्दी", UiStrings.name(Lang.HI)); assertEquals("English", UiStrings.name(Lang.EN))
        for (l in Lang.values()) { for (s in LanguageSetting.values()) assertTrue(UiStrings.spokenOption(s, l).isNotBlank()); for (s in SummaryLanguage.values()) assertTrue(UiStrings.summaryOption(s, l).isNotBlank()) }
    }
}
