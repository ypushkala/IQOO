package com.callguard.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NumberParserTest {
    private fun kind(s: String?) = NumberParser.parse(s).kind

    @Test fun indianMobilesInEveryCommonFormat() {
        for (s in listOf("+919876543210", "09876543210", "919876543210", "9876543210", "98765 43210", "+91 98765-43210", "(+91) 98765 43210"))
            assertEquals(s, NumberKind.INDIA_MOBILE, kind(s))
        assertEquals("9876543210", NumberParser.parse("+91 98765-43210").digits)
        assertEquals("919876543210", NumberParser.parse("09876543210").e164Digits)
    }
    @Test fun institutionalAndTelemarketingSeries() {
        assertEquals(NumberKind.SERIES_160, kind("1600123456")); assertEquals(NumberKind.SERIES_160, kind("160012345678"))
        assertEquals(NumberKind.SERIES_140, kind("1401234567"))
        assertEquals(NumberKind.TOLL_FREE, kind("18001234567")); assertEquals(NumberKind.TOLL_FREE, kind("1860123456"))
    }
    @Test fun internationalNumbersKeepTheirCountryCode() {
        assertEquals(NumberKind.INTERNATIONAL, kind("+14155550123")); assertEquals("14155550123", NumberParser.parse("+14155550123").e164Digits)
        assertEquals(NumberKind.INTERNATIONAL, kind("00923001234567")); assertEquals("923001234567", NumberParser.parse("00923001234567").e164Digits)
        assertTrue(NumberParser.parse("+84901234567").isInternational)
    }
    @Test fun landlinesShortCodesAndJunk() {
        assertEquals(NumberKind.INDIA_LANDLINE, kind("02212345678")); assertEquals(NumberKind.INDIA_LANDLINE, kind("0112345678"))
        assertEquals(NumberKind.SHORT_CODE, kind("12345")); assertEquals(NumberKind.SHORT_CODE, kind("567"))
        assertEquals(NumberKind.UNKNOWN, kind("1234567890")); assertEquals(NumberKind.UNKNOWN, kind("+911234567890"))
    }
    @Test fun withheldNumbers() {
        for (s in listOf(null, "", "   ", "private", "Unknown", "anonymous", "-1", "-2", "+++")) assertEquals("[$s]", NumberKind.PRIVATE, kind(s))
    }
    @Test fun neverThrowsOnGarbage() {
        for (s in listOf("abc", "+", "00", "🙂", "1".repeat(200), "+91", "91"))
            assertNotNull(NumberParser.parse(s))
    }
}

class ScamPrefixListTest {
    @Test fun parsesCommentsAndSkipsBadLines() {
        val l = ScamPrefixList.parse("""
            # comment
            92|2|label a      # trailing comment
            +84|3|label b
            bad line
            12|9|weight out of range
            |2|no prefix
            60|1
        """.trimIndent())
        assertEquals(listOf(ScamPrefix("92", 2, "label a"), ScamPrefix("84", 3, "label b"), ScamPrefix("60", 1, "listed prefix")), l)
    }
    @Test fun theBundledListIsCountryLevelOnlyAndWellFormed() {
        val text = File("src/main/assets/scam_prefixes.txt").readText()
        val l = ScamPrefixList.parse(text)
        assertTrue(l.size >= 3)
        assertTrue("no individual phone numbers in the bundled list", l.all { it.prefix.length <= 3 })
        assertTrue("SAMPLE label present", text.contains("SAMPLE DATA"))
        assertFalse("must not list India itself", l.any { it.prefix == "91" })
    }
}

class NumberReputationTest {
    private val rep = NumberReputation(listOf(ScamPrefix("92", 2, "reported prefix"), ScamPrefix("923", 4, "specific range")))
    private fun a(number: String?, contacts: Boolean? = null, first: Boolean? = null, v: Verification = Verification.UNKNOWN) =
        rep.assess(CallerContext(number, contacts, first, v))

    @Test fun ordinaryUnknownIndianMobileIsNormal() = assertEquals(NumberRisk.NORMAL, a("+919876543210").level)
    @Test fun unsavedFirstTimeIndianMobileIsOnlyElevated() {
        val r = a("+919876543210", contacts = false, first = true)
        assertEquals(2, r.score); assertEquals(NumberRisk.ELEVATED, r.level)
    }
    @Test fun savedContactIsTrusted() = assertEquals(NumberRisk.NORMAL, a("+919876543210", contacts = true, first = false).level)
    @Test fun internationalAloneIsElevatedAndWithContextIsHigh() {
        assertEquals(NumberRisk.ELEVATED, a("+14155550123").level)
        assertEquals(NumberRisk.HIGH, a("+14155550123", contacts = false, first = true).level)
    }
    @Test fun listedPrefixAddsPointsAndTheLongestMatchWins() {
        assertEquals(NumberRisk.HIGH, a("+92 300 1234567").level)          // 3 + 4 (923 range)
        assertEquals(7, a("+923001234567").score)
        assertEquals(5, a("+925551234567").score)                           // 3 + 2 (92 only)
    }
    @Test fun withheldIsElevated() = assertEquals(NumberRisk.ELEVATED, a(null).level)
    @Test fun institutionalSeriesLowersSuspicion() {
        assertEquals(NumberRisk.NORMAL, a("1600123456", contacts = false, first = true).level)
        assertTrue(a("1600123456").isInstitutionalSeries)
    }
    @Test fun failedVerificationIsHighAndPassedHelps() {
        assertEquals(NumberRisk.HIGH, a("+919876543210", v = Verification.FAILED).level)
        assertEquals(-1, a("+919876543210", v = Verification.PASSED).score)
    }
    @Test fun aSavedContactWithFailedVerificationIsNotHigh() =
        assertEquals(NumberRisk.NORMAL, a("+919876543210", contacts = true, v = Verification.FAILED).level)
    @Test fun explainsItself() {
        val r = a("+14155550123", contacts = false, first = true)
        assertTrue(r.reasons.containsAll(listOf("international number", "not in your contacts", "first call from this number")))
        assertTrue(r.summary.startsWith("+14155550123"))
        assertEquals("Hidden number", a(null).displayNumber)
    }
    @Test fun noListStillWorks() = assertEquals(3, NumberReputation().assess(CallerContext("+92300123456")).score)
}

class InstitutionClaimTest {
    private fun claim(s: String) = InstitutionClaim.find(TextNormalizer.normalize(s))

    @Test fun englishClaims() {
        for (s in listOf("This is HDFC Bank calling", "I am calling from SBI", "We are calling from your bank", "This is the RBI", "Calling from Paytm KYC team",
            "I am speaking from the bank about your credit card"))
            assertNotNull(s, claim(s))
    }
    @Test fun hinglishAndDevanagariClaims() {
        assertNotNull(claim("Main HDFC bank se bol raha hoon"))
        assertNotNull(claim("Sir main SBI se baat kar raha hoon"))
        assertNotNull(claim("मैं बैंक से बोल रहा हूँ"))
    }
    @Test fun ordinaryMentionsOfBanksAreNotClaims() {
        for (s in listOf("The bank is closed today", "I went to the bank yesterday", "I am at the bank", "My bank called me last week", "How was your day?",
            "Never share your OTP with your bank"))
            assertNull(s, claim(s))
    }
}

class NumberSignalAndFusionTest {
    private var current: NumberAssessment? = null
    private val rep = NumberReputation(listOf(ScamPrefix("92", 2, "reported prefix")))
    private val src = NumberSignalSource { current }
    private val engine = RiskEngine(number = src)
    private fun caller(number: String?, contacts: Boolean? = null, first: Boolean? = null, v: Verification = Verification.UNKNOWN) {
        current = rep.assess(CallerContext(number, contacts, first, v))
    }
    private fun level(t: String) = engine.evaluate(t).level
    private val badNumber = "+14155550123"

    // --- no information => no effect ---
    @Test fun withoutCallerInfoNothingChanges() {
        current = null
        assertEquals(RiskLevel.HIGH, level("Please tell me your OTP")); assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired"))
        assertEquals(RiskLevel.LOW, level("This is HDFC bank calling")); assertEquals(DetectionResult.NONE, engine.evaluate(""))
    }

    // --- the number multiplies suspicious content, never creates it ---
    @Test fun highRiskNumberAmplifiesMediumContentToHigh() {
        caller(badNumber, contacts = false, first = true)
        val r = engine.evaluate("Your KYC has expired")
        assertEquals(RiskLevel.HIGH, r.level)
        assertTrue(r.signals.any { it.source == "number" && it.level == RiskLevel.HIGH })
    }
    @Test fun highRiskNumberWithHarmlessWordsStaysLow() {
        caller(badNumber, contacts = false, first = true)
        for (t in listOf("How was your day?", "Hello, is this a good time?", "")) assertEquals(t, RiskLevel.LOW, level(t))
    }
    @Test fun elevatedNumberDoesNotAmplify() {
        caller("+14155550123") // international alone: score 3
        assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired"))
    }
    @Test fun normalNumberNeverLowersAnything() {
        caller("+919876543210", contacts = true, first = false)
        assertEquals(RiskLevel.HIGH, level("Please tell me your OTP")); assertEquals(RiskLevel.HIGH, level("Install AnyDesk"))
        assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired"))
        caller("1600123456"); assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired"))
    }
    @Test fun advisoryStaysLowEvenFromABadNumber() {
        caller(badNumber, contacts = false, first = true)
        assertEquals(RiskLevel.LOW, level("Never share your OTP with anyone."))
    }

    // --- bank claim vs. number ---
    @Test fun bankClaimFromAnOrdinaryMobileIsFlaggedMedium() {
        caller("+919876543210", contacts = false, first = true)
        val r = engine.evaluate("This is HDFC Bank calling")
        assertEquals(RiskLevel.MEDIUM, r.level)
        assertTrue(r.signals.any { it.tactic == Tactic.IDENTITY_MISMATCH })
    }
    @Test fun bankClaimFromAnInstitutionalOrSavedNumberIsFine() {
        caller("1600123456"); assertEquals(RiskLevel.LOW, level("This is HDFC Bank calling"))
        caller("18001234567"); assertEquals(RiskLevel.LOW, level("This is HDFC Bank calling"))
        caller("+919876543210", contacts = true); assertEquals(RiskLevel.LOW, level("This is HDFC Bank calling"))
    }
    @Test fun bankClaimFromAHighRiskNumberIsHigh() {
        caller("+923001234567", contacts = false, first = true)
        assertEquals(RiskLevel.HIGH, level("Main HDFC bank se bol raha hoon"))
    }
    @Test fun withheldNumberCannotBackABankClaim() {
        caller(null); assertEquals(RiskLevel.MEDIUM, level("I am calling from SBI"))
    }

    // --- informational only, never an alert by itself ---
    @Test fun suspiciousNumberAloneAddsAnInformationalSignalAndNoAlert() {
        caller(badNumber, contacts = false, first = true)
        val r = engine.evaluate("")
        assertEquals(RiskLevel.LOW, r.level); assertTrue(r.signals.any { it.tactic == Tactic.NUMBER_RISK && it.level == RiskLevel.LOW })
        assertTrue(AlertDebouncer().onDetection(r, 0).isEmpty())
        assertTrue(AlertDebouncer().onDetection(engine.evaluate("How are you"), 0).isEmpty())
    }
    @Test fun alertsFireOnEscalationOnly() {
        caller("+919876543210", contacts = false, first = true)
        val d = AlertDebouncer()
        assertEquals(1, d.onDetection(engine.evaluate("This is HDFC bank calling"), 0).size)      // MEDIUM identity mismatch
        assertTrue(d.onDetection(engine.evaluate("This is HDFC bank calling"), 2_000).isEmpty())   // same thing: silent
        caller(badNumber, contacts = false, first = true)
        val esc = d.onDetection(engine.evaluate("This is HDFC bank calling"), 4_000)                 // now HIGH: escalation alerts
        assertTrue(esc.isNotEmpty()); assertEquals(RiskLevel.HIGH, esc.maxOf { it.level })
    }

    // --- fusion with the model ---
    @Test fun identityMismatchDoesNotCorroborateGemma() {
        val g = GemmaSignalSource(clock = { 0L }).also { it.update(GemmaVerdict(RiskLevel.HIGH, "credential request", "r")) }
        val e = RiskEngine(models = listOf(g), number = src)
        caller("+919876543210", contacts = false, first = true)
        val r = e.evaluate("This is HDFC bank calling") // rules: none; identity MEDIUM; Gemma uncorroborated -> capped MEDIUM
        assertEquals(RiskLevel.MEDIUM, r.level)
    }
    @Test fun hindiContentIsAmplifiedToo() {
        caller(badNumber, contacts = false, first = true)
        assertEquals(RiskLevel.HIGH, level("आपका सिम कार्ड दो घंटे में बंद हो जाएगा।"))
    }
}

class NumberHashTest {
    @Test fun deterministicSaltedAndOpaque() {
        val h = NumberHash.hash("salt", "9876543210")
        assertEquals(h, NumberHash.hash("salt", "9876543210")); assertEquals(16, h.length)
        assertNotEquals(h, NumberHash.hash("other", "9876543210")); assertNotEquals(h, NumberHash.hash("salt", "9876543211"))
        assertFalse(h.contains("9876543210"))
    }
}
