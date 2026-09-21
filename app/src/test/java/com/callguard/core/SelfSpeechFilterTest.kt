package com.callguard.core

import org.junit.Assert.*
import org.junit.Test

class SelfSpeechFilterTest {
    private val hi = WarningPlans.plan(RiskLevel.HIGH, false, Lang.EN).messages
    private val te = WarningPlans.plan(RiskLevel.HIGH, false, Lang.TE).messages
    private val hiHindi = WarningPlans.plan(RiskLevel.HIGH, false, Lang.HI).messages

    @Test fun ownWarningHeardBackIsDropped() {
        val f = SelfSpeechFilter(); f.begin(1_000, hi)
        // what an ASR typically writes for our English warning, with small errors
        assertTrue(f.shouldDrop(2_000, 5_000, "warning this looks like a scam do not share codes hang up", 6_000))
        assertTrue(f.shouldDrop(2_000, 4_000, "Warning! This looks like a scam.", 6_000))
        val long = WarningPlans.plan(RiskLevel.HIGH, true, Lang.EN).messages; val g = SelfSpeechFilter(); g.begin(1_000, long)  // accessibility mode keeps the long text
        assertTrue(g.shouldDrop(2_000, 6_000, "danger this is a scam call hang up now do not give out any codes do not install any apps", 7_000))
    }
    @Test fun aCallerTalkingOverTheWarningIsKept() {
        val f = SelfSpeechFilter(); f.begin(1_000, hi)
        for (caller in listOf("Please tell me your OTP immediately", "Install AnyDesk so I can help you", "Your KYC has expired and your account will be blocked today"))
            assertFalse(caller, f.shouldDrop(2_000, 5_000, caller, 6_000))
    }
    @Test fun worksForTeluguAndHindiWhateverTheSpellingOfTheRecogniser() {
        val f = SelfSpeechFilter(); f.begin(0, te + hiHindi)
        assertTrue(f.shouldDrop(500, 4_000, "హెచ్చరిక ఇది మోసంలా ఉంది కోడ్లు చెప్పకండి ఫోన్ కట్ చేయండి", 5_000))
        assertTrue(f.shouldDrop(500, 4_000, "चेतावनी यह ठगी लग रही है कोई कोड न बताएं फोन काट दें", 5_000))
        assertFalse(f.shouldDrop(500, 4_000, "మీ otp చెప్పండి ఇది చాలా అత్యవసరం", 5_000))
        assertFalse(f.shouldDrop(500, 4_000, "आपका ओटीपी बताइए यह बहुत जरूरी है", 5_000))
    }
    @Test fun onlyDuringOurSpeechPlusATail() {
        val f = SelfSpeechFilter(tailMs = 1_500); f.begin(10_000, hi); f.end(14_000)
        val own = "warning this looks like a scam do not share codes hang up"
        assertFalse("before we spoke", f.shouldDrop(0, 9_000, own, 20_000))
        assertTrue("during", f.shouldDrop(11_000, 13_000, own, 20_000))
        assertTrue("in the tail (echo/reverb)", f.shouldDrop(15_000, 15_400, own, 20_000))
        assertFalse("after the tail", f.shouldDrop(16_000, 18_000, own, 20_000))
    }
    @Test fun aWarningThatNeverReportsItsEndExpiresOnItsOwn() {
        val f = SelfSpeechFilter(maxWindowMs = 10_000, tailMs = 1_000); f.begin(0, hi)
        val own = "warning this looks like a scam do not share codes hang up"
        assertTrue(f.shouldDrop(3_000, 5_000, own, 6_000))
        assertFalse(f.shouldDrop(60_000, 62_000, own, 62_500))
    }
    @Test fun shortFragmentsAreNeverDropped() {
        val f = SelfSpeechFilter(); f.begin(0, hi)
        for (s in listOf("", "hi", "call", "scam")) assertFalse(s, f.shouldDrop(100, 900, s, 1_000))
    }
    @Test fun withNoWindowNothingIsDropped() =
        assertFalse(SelfSpeechFilter().shouldDrop(0, 1_000, "warning this looks like a scam do not share codes hang up", 2_000))
    @Test fun twoWarningsAreBothRemembered() {
        val f = SelfSpeechFilter(); f.begin(0, hi); f.end(4_000); f.begin(5_000, te); f.end(9_000)
        assertTrue(f.shouldDrop(1_000, 3_000, "warning this looks like a scam do not share codes hang up", 9_500))
        assertTrue(f.shouldDrop(6_000, 8_000, "హెచ్చరిక ఇది మోసంలా ఉంది కోడ్లు చెప్పకండి", 9_500))
    }
    @Test fun oldWindowsArePruned() {
        val f = SelfSpeechFilter(); f.begin(0, hi); f.end(1_000); f.begin(500_000, hi)
        assertEquals(1, f.activeWindows())
    }
    @Test fun similarityBasics() {
        assertEquals(1.0, SelfSpeechFilter.similarity("Do not share any codes", "do not share any codes."), 1e-9)
        assertTrue(SelfSpeechFilter.similarity("How was your day", "Do not share any codes and do not install any apps") < 0.3)
        assertEquals(0.0, SelfSpeechFilter.similarity("", "anything"), 1e-9)
    }
    @Test fun noSpokenWarningIsSimilarToNormalCallerSpeechInAnyLanguage() {
        val caller = listOf("Please tell me your OTP immediately.", "Install AnyDesk so I can help you.", "Your KYC has expired and your account will be blocked today.",
            "This is the CBI, you are under digital arrest, do not tell anyone.", "మీ otp చెప్పండి ఇది చాలా అత్యవసరం", "నేను cbi నుండి మాట్లాడుతున్నాను మీరు డిజిటల్ అరెస్ట్ లో ఉన్నారు ఎవరికీ చెప్పకండి",
            "आपका ओटीपी बताइए यह बहुत जरूरी है", "मैं सीबीआई से बोल रहा हूँ आप डिजिटल अरेस्ट में हैं किसी को मत बताना")
        for (m in WarningPlans.allMessages) for (c in caller)
            assertTrue("$c vs $m", SelfSpeechFilter.similarity(c, m) < SelfSpeechFilter.DEFAULT_THRESHOLD)
    }
}

class ShortWarningTest {
    private fun words(s: String) = s.split(Regex("\\s+")).count { it.isNotBlank() }

    @Test fun normalModeIsShortAndAccessibilityModeStaysLong() {
        for (l in Lang.values()) for (lv in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH)) {
            val std = WarningPlans.plan(lv, false, l); val acc = WarningPlans.plan(lv, true, l)
            assertEquals(1, std.messages.size)                     // spoken once
            assertEquals(2, acc.messages.size)                     // accessibility repeats it
            assertTrue("$l $lv short ${std.messages[0]}", words(std.messages[0]) <= 14)
            assertTrue("$l $lv long", words(acc.messages[0]) > words(std.messages[0]))
        }
    }
    // ~5 s of speech at rate 1.0. Measured on the F17: 5.4-6.4 s for messages of 41-62 characters, so cap the length.
    @Test fun normalModeWarningsStayWithinTheSpokenTimeBudget() {
        for (l in Lang.values()) {
            val med = WarningPlans.plan(RiskLevel.MEDIUM, false, l).messages[0]
            val high = WarningPlans.plan(RiskLevel.HIGH, false, l).messages[0]
            assertTrue("$l high ${high.length}", high.length <= 64)
            assertTrue("$l medium ${med.length}", med.length <= 60)
            assertTrue("$l medium must not be longer than high", med.length <= high.length)
        }
    }
    @Test fun theShortHighWarningStillSaysTheThreeThingsThatMatter() {
        val en = WarningPlans.plan(RiskLevel.HIGH, false, Lang.EN).messages[0].lowercase()
        assertTrue("scam" in en && "codes" in en && "hang up" in en)
        val te = WarningPlans.plan(RiskLevel.HIGH, false, Lang.TE).messages[0]
        assertTrue("మోసం" in te && "కోడ్" in te && "కట్" in te)
        val hi = WarningPlans.plan(RiskLevel.HIGH, false, Lang.HI).messages[0]
        assertTrue("ठगी" in hi && "कोड" in hi && "काट" in hi)
    }
    @Test fun shortWarningsCannotTriggerTheDetectorAndAreNotMistakenForCallers() {
        val engine = RiskEngine()
        for (l in Lang.values()) for (lv in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH)) for (m in WarningPlans.plan(lv, false, l).messages) {
            assertEquals(m, RiskLevel.LOW, engine.evaluate(m).level)
            for (c in listOf("Please tell me your OTP immediately.", "Never share your OTP with anyone.", "Install AnyDesk so I can help you.", "మీ otp ఎవరికి ఎప్పుడూ చెప్పకండి", "किसी को अपना ओटीपी कभी मत बताइए"))
                assertTrue("$c vs $m", SelfSpeechFilter.similarity(c, m) < SelfSpeechFilter.DEFAULT_THRESHOLD)
        }
    }
}
