package com.callguard.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsUpTranslationTest {
    private val rep = NumberReputation(listOf(ScamPrefix("92", 2, "country code often reported for scam calls")))
    private val risky = rep.assess(CallerContext(number = "+92 300 1234567", inContacts = false, firstTime = true))

    @Test fun reasonsAreCodedAndStillEnglishForTheSummary() {
        assertTrue(NumberReason.INTERNATIONAL in risky.reasonCodes)
        assertTrue("international number" in risky.reasons)
    }
    @Test fun everyReasonHasAllThreeLanguages() {
        for (r in NumberReason.values()) for (l in Lang.values()) assertTrue("$r $l", r.text(l).isNotBlank())
    }
    @Test fun theHeadsUpBodyFollowsTheScreenLanguage() {
        val hi = CallerAlertText.build(risky, Lang.HI, false)!!.body
        val te = CallerAlertText.build(risky, Lang.TE, false)!!.body
        assertTrue("अंतरराष्ट्रीय नंबर" in hi); assertFalse("international" in hi)
        assertTrue("అంతర్జాతీయ నంబర్" in te); assertFalse("international" in te)
        assertTrue("international number" in CallerAlertText.build(risky, Lang.EN, false)!!.body.lowercase())
    }
    @Test fun calmingReasonsAreNotShownAsWarnings() {
        val a = rep.assess(CallerContext(number = "+92 300 1234567", inContacts = true, userTrusted = true, verification = Verification.FAILED))
        val body = CallerAlertText.build(a, Lang.EN, true)
        if (body != null) { assertFalse("saved contact" in body.body); assertFalse("marked this number as safe" in body.body) }
    }
    @Test fun noPhoneNumberInTheText() {
        for (l in Lang.values()) assertFalse("300 1234567" in CallerAlertText.build(risky, l, false)!!.body)
        assertNotNull(risky.displayNumber)
    }
}
