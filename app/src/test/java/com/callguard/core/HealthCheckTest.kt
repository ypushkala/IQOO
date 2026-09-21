package com.callguard.core

import org.junit.Assert.*
import org.junit.Test

class HealthCheckTest {
    private val all = HealthInputs(mic = true, phone = true, notifications = true, running = true, callerIdRole = true, batteryExempt = true, models = true)

    @Test fun everythingInPlaceIsGreen() {
        val r = HealthCheck.evaluate(all)
        assertTrue(r.protectionOn); assertTrue(r.missing.isEmpty()); assertNull(r.next)
        assertEquals("Protection is ON", HealthText.cardTitle(r, Lang.EN)); assertEquals("Everything is set up.", HealthText.cardBody(r, Lang.EN))
    }
    @Test fun aMissingRequiredItemMeansProtectionIsOffAndNamesIt() {
        val r = HealthCheck.evaluate(all.copy(mic = false))
        assertFalse(r.protectionOn); assertEquals(listOf(HealthItem.MIC_PERMISSION), r.blocking); assertEquals(HealthItem.MIC_PERMISSION, r.next)
        assertEquals("Needs attention", HealthText.cardTitle(r, Lang.EN)); assertEquals("Let CallGuard hear the call", HealthText.cardBody(r, Lang.EN))
    }
    @Test fun notRunningIsBlockingToo() = assertEquals(HealthItem.PROTECTION_RUNNING, HealthCheck.evaluate(all.copy(running = false)).next)
    @Test fun optionalItemsOnlyRecommend() {
        val r = HealthCheck.evaluate(all.copy(callerIdRole = false, batteryExempt = false, models = false))
        assertTrue(r.protectionOn); assertEquals(3, r.recommended.size); assertEquals(HealthItem.CALLER_ID_ROLE, r.next)
        assertEquals("3 improvements recommended", HealthText.cardBody(r, Lang.EN))
        assertEquals("1 improvement recommended", HealthText.cardBody(HealthCheck.evaluate(all.copy(models = false)), Lang.EN))
    }
    @Test fun blockingItemsComeBeforeRecommendedAndKeepTheirOrder() {
        val r = HealthCheck.evaluate(HealthInputs(mic = false, phone = false, notifications = true, running = false, callerIdRole = false, batteryExempt = true, models = true))
        assertEquals(listOf(HealthItem.MIC_PERMISSION, HealthItem.PHONE_PERMISSION, HealthItem.PROTECTION_RUNNING), r.blocking)
        assertEquals(HealthItem.MIC_PERMISSION, r.next)
    }
    @Test fun requiredFlagsMatchTheDocumentedRule() {
        assertEquals(setOf(HealthItem.MIC_PERMISSION, HealthItem.PHONE_PERMISSION, HealthItem.NOTIFICATIONS, HealthItem.PROTECTION_RUNNING), HealthItem.values().filter { it.required }.toSet())
    }
    @Test fun everyItemHasNameWhyAndFixInEveryLanguageAndScript() {
        fun dev(s: String) = s.any { it.code in 0x0900..0x097F }
        fun tel(s: String) = s.any { it.code in 0x0C00..0x0C7F }
        for (i in HealthItem.values()) for (l in Lang.values()) for (s in listOf(HealthText.name(i, l), HealthText.why(i, l), HealthText.fix(i, l))) {
            assertTrue("$i/$l", s.isNotBlank()); if (l == Lang.HI) assertTrue("$i hi: $s", dev(s)); if (l == Lang.TE) assertTrue("$i te: $s", tel(s))
        }
        for (l in Lang.values()) for (s in listOf(HealthText.setupButton(l), HealthText.setupTitle(l), HealthText.setupIntro(l), HealthText.done(l), HealthText.autostartName(l), HealthText.autostartWhy(l), HealthText.openSettings(l))) assertTrue(s.isNotBlank())
    }
    @Test fun cardTextIsLocalisedForAllStates() {
        val states = listOf(all, all.copy(mic = false), all.copy(models = false))
        for (st in states) for (l in Lang.values()) { val r = HealthCheck.evaluate(st); assertTrue(HealthText.cardTitle(r, l).isNotBlank() && HealthText.cardBody(r, l).isNotBlank()) }
        assertTrue(HealthText.cardTitle(HealthCheck.evaluate(all), Lang.TE).any { it.code in 0x0C00..0x0C7F })
    }
}

class BrandAutostartTest {
    @Test fun iqooAndVivoHaveTheirBackgroundStartPage() {
        for (m in listOf("vivo", "iQOO", "IQOO", "Vivo")) assertTrue(m, BrandAutostart.candidates(m).any { it.pkg.contains("iqoo") || it.pkg.contains("vivo") })
    }
    @Test fun majorBrandsAreCovered() {
        for (m in listOf("Xiaomi", "Redmi", "POCO", "OPPO", "realme", "OnePlus", "HUAWEI", "Honor", "samsung")) assertTrue(m, BrandAutostart.candidates(m).isNotEmpty())
    }
    @Test fun brandsThatNeedNothingReturnNothing() {
        for (m in listOf("Google", "Motorola", "Nothing", "", "  ")) assertTrue(m, BrandAutostart.candidates(m).isEmpty())
    }
    @Test fun targetsAreWellFormed() {
        for (m in listOf("xiaomi", "vivo", "oppo", "oneplus", "huawei", "samsung")) for (t in BrandAutostart.candidates(m)) {
            assertTrue(t.pkg.matches(Regex("[a-z0-9_.]+"))); assertTrue(t.cls.startsWith(t.pkg) || t.cls.matches(Regex("[A-Za-z0-9_.$]+")))
        }
    }
}
