package com.callguard.core

import com.callguard.audio.PcmRingBuffer
import org.junit.Assert.*
import org.junit.Test

class TextNormalizerTest {
    @Test fun lowercasesAndStripsPunctuation() =
        assertEquals("please tell me your otp", TextNormalizer.normalize("Please, tell me your OTP!"))
    @Test fun mergesSpelledLetters() {
        assertEquals("your otp is", TextNormalizer.normalize("your O T P is"))
        assertEquals("kyc", TextNormalizer.normalize("K.Y.C"))
    }
    @Test fun joinsSplitBrandNames() {
        assertEquals("install anydesk", TextNormalizer.normalize("Install ANY DESK"))
        assertEquals("open teamviewer", TextNormalizer.normalize("open Team Viewer"))
    }
    @Test fun stripsApostrophes() = assertEquals("dont share", TextNormalizer.normalize("Don't share"))
}

class RiskEngineTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level

    @Test fun observedAsrMishearingsStillDetected() {
        assertEquals(RiskLevel.HIGH, level("PLEASE TELL ME OH DE BE"))
        assertEquals(RiskLevel.HIGH, level("STILL ME O TEEPEE"))
        assertEquals(RiskLevel.HIGH, level("please install a desk on your phone"))
        assertEquals(RiskLevel.MEDIUM, level("KILL ICY HAS EXPIRED"))
        assertEquals(RiskLevel.MEDIUM, level("YOUR CAVE I SEE HAS EXPIRED"))
        assertEquals(RiskLevel.LOW, level("I sat at a desk all day"))
    }

    @Test fun otpIsHigh() = assertEquals(RiskLevel.HIGH, level("Please tell me your OTP"))
    @Test fun anydeskIsHigh() = assertEquals(RiskLevel.HIGH, level("Install AnyDesk"))
    @Test fun spokenAnyDeskIsHigh() = assertEquals(RiskLevel.HIGH, level("install ANY DESK now"))
    @Test fun cvvPinVerificationCodeHigh() {
        assertEquals(RiskLevel.HIGH, level("share your CVV"))
        assertEquals(RiskLevel.HIGH, level("what is your UPI PIN"))
        assertEquals(RiskLevel.HIGH, level("read the verification code"))
    }
    @Test fun remoteAccessHigh() {
        assertEquals(RiskLevel.HIGH, level("download TeamViewer QuickSupport"))
        assertEquals(RiskLevel.HIGH, level("we need remote access to your phone"))
    }
    @Test fun kycExpiredIsMedium() = assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired"))
    @Test fun accountAndSimBlockedMedium() {
        assertEquals(RiskLevel.MEDIUM, level("your account will be blocked today"))
        assertEquals(RiskLevel.MEDIUM, level("your SIM card will be blocked"))
    }
    @Test fun digitalArrestIsHigh() = assertEquals(RiskLevel.HIGH, level("You are under digital arrest"))
    @Test fun authorityWithThreatIsHigh() = assertEquals(RiskLevel.HIGH, level("This is CBI, there is a case against you"))
    @Test fun authorityAloneIsMedium() = assertEquals(RiskLevel.MEDIUM, level("I am calling from the police"))
    @Test fun genericTermsAreLow() {
        assertEquals(RiskLevel.LOW, level("You won a lottery prize"))
        assertEquals(RiskLevel.LOW, level("I want a refund for the payment"))
        assertEquals(RiskLevel.LOW, level("How was your day?"))
        assertEquals(RiskLevel.LOW, level(""))
    }
    @Test fun wordBoundariesAvoidFalsePositives() {
        assertEquals(RiskLevel.LOW, level("the spinning pinwheel and pinterest"))
        assertEquals(RiskLevel.LOW, level("my postal pin code is 560001"))
        assertEquals(RiskLevel.LOW, level("potpourri and hotpot"))
    }
    @Test fun tacticsAreTagged() {
        val r = engine.evaluate("install anydesk and tell me the otp")
        assertEquals(setOf(Tactic.REMOTE_ACCESS, Tactic.CREDENTIAL_REQUEST), r.signals.map { it.tactic }.toSet())
    }
    @Test fun highWinsOverMedium() = assertEquals(RiskLevel.HIGH, level("your kyc has expired, share the otp"))
}

class AlertDebouncerTest {
    private val engine = RiskEngine()

    @Test fun lowNeverAlerts() {
        val d = AlertDebouncer()
        assertTrue(d.onDetection(engine.evaluate("how was your day"), 0).isEmpty())
        assertTrue(d.onDetection(engine.evaluate("you won a prize"), 0).isEmpty())
    }
    @Test fun samePhraseAlertsOnceWithinCooldown() {
        val d = AlertDebouncer(30_000)
        val r = engine.evaluate("tell me your otp")
        assertEquals(1, d.onDetection(r, 0).size)
        assertTrue(d.onDetection(r, 1_000).isEmpty())
        assertTrue(d.onDetection(engine.evaluate("tell me your otp please now"), 20_000).isEmpty())
    }
    @Test fun alertsAgainAfterCooldown() {
        val d = AlertDebouncer(30_000)
        val r = engine.evaluate("tell me your otp")
        d.onDetection(r, 0)
        assertEquals(1, d.onDetection(r, 30_000).size)
    }
    @Test fun newPhraseAlertsImmediately() {
        val d = AlertDebouncer(30_000)
        d.onDetection(engine.evaluate("your kyc has expired"), 0)
        val fresh = d.onDetection(engine.evaluate("your kyc has expired install anydesk"), 500)
        assertEquals(listOf(Tactic.REMOTE_ACCESS), fresh.map { it.tactic })
    }
    @Test fun resetClearsHistory() {
        val d = AlertDebouncer()
        val r = engine.evaluate("tell me your otp")
        d.onDetection(r, 0); d.reset()
        assertEquals(1, d.onDetection(r, 1).size)
    }
}

class RollingTranscriptTest {
    @Test fun partialThenCommit() {
        val t = RollingTranscript()
        t.commit("hello there"); t.setPartial("tell me")
        assertEquals("hello there tell me", t.text())
        t.commit("tell me your otp")
        assertEquals("hello there tell me your otp", t.text())
    }
    @Test fun trimsOldest() {
        val t = RollingTranscript(maxChars = 30)
        repeat(5) { t.commit("segment number $it") }
        assertFalse(t.text().contains("number 0"))
        assertTrue(t.text().contains("number 4"))
    }
}

class PcmRingBufferTest {
    @Test fun keepsMostRecentSamples() {
        val b = PcmRingBuffer(4)
        b.write(shortArrayOf(1, 2, 3)); b.write(shortArrayOf(4, 5, 6))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), b.snapshot())
        assertEquals(6L, b.totalWritten)
    }
}

class AdvisoryAndPressureTacticsTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level
    private fun tactics(s: String) = engine.evaluate(s).signals.map { it.tactic }.toSet()

    // --- legitimate warnings must not reach HIGH (but detection is not removed) ---
    @Test fun neverShareOtpIsAdvisoryNotHigh() {
        assertEquals(RiskLevel.LOW, level("Never share your OTP with anyone."))
        assertEquals(RiskLevel.LOW, level("Do not share your OTP or PIN with anyone"))
        assertEquals(RiskLevel.LOW, level("Your bank will never ask for your OTP"))
        assertEquals(RiskLevel.LOW, level("Never install AnyDesk or any remote access app"))
    }
    @Test fun advisoryStillTaggedAsSignalButLow() {
        val r = engine.evaluate("Never share your OTP with anyone.")
        assertEquals(listOf(RiskLevel.LOW), r.signals.filter { it.tactic == Tactic.CREDENTIAL_REQUEST }.map { it.level })
    }
    @Test fun otpRequestsStillHigh() {
        assertEquals(RiskLevel.HIGH, level("Please tell me your OTP"))
        assertEquals(RiskLevel.HIGH, level("Don't worry, tell me the OTP"))
        assertEquals(RiskLevel.HIGH, level("Don't tell anyone, just share the OTP"))
        assertEquals(RiskLevel.HIGH, level("Please don't ask why, just tell me the PIN"))
    }
    @Test fun advisoryFollowedByRealRequestIsHigh() {
        assertEquals(RiskLevel.HIGH, level("Never share your OTP. Now tell me your OTP"))
        assertEquals(RiskLevel.HIGH, level("Never share your OTP with anyone. Anyway, read me the OTP."))
    }
    @Test fun advisoryDoesNotMaskOtherTactics() =
        assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired. Never share your OTP."))

    // --- secrecy ---
    @Test fun secrecyPhrasesAreMedium() {
        for (p in listOf("Don't tell anyone about this call", "Please keep this secret", "Do not inform anyone", "Do not share this call"))
            assertEquals(p, RiskLevel.MEDIUM, level(p))
        for (p in listOf("Don't tell anyone", "keep this a secret", "do not inform anyone", "do not share this"))
            assertTrue(p, Tactic.SECRECY in tactics(p))
    }
    @Test fun doNotShareThisOtpIsAdvisoryNotSecrecy() {
        val r = engine.evaluate("Do not share this OTP with anyone")
        assertFalse(r.signals.any { it.tactic == Tactic.SECRECY })
        assertEquals(RiskLevel.LOW, r.level)
    }

    // --- urgency ---
    @Test fun urgencyAloneStaysLow() {
        for (p in listOf("Do it immediately", "This is urgent", "Act now", "This is your final warning", "Your plan expires today", "call me now"))
            assertEquals(p, RiskLevel.LOW, level(p))
        for (p in listOf("Do it immediately", "This is urgent", "Act now", "final warning", "it expires soon"))
            assertTrue(p, Tactic.URGENCY in tactics(p))
    }
    @Test fun urgencyDoesNotDowngradeOrRaiseOthers() {
        assertEquals(RiskLevel.HIGH, level("Install AnyDesk immediately"))
        assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired, act now"))
        assertEquals(RiskLevel.LOW, level("I know it is windy right now"))
    }
    @Test fun urgencyNeverTriggersAlerts() {
        val d = AlertDebouncer()
        assertTrue(d.onDetection(engine.evaluate("act now, this is urgent, final warning"), 0).isEmpty())
    }
    @Test fun secrecyAlertsAsMedium() {
        val d = AlertDebouncer()
        val fresh = d.onDetection(engine.evaluate("don't tell anyone about this"), 0)
        assertEquals(listOf(Tactic.SECRECY), fresh.map { it.tactic })
    }
}

class SentenceBoundaryAndScopedUrgencyTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level
    private fun tactics(s: String) = engine.evaluate(s).signals.map { it.tactic }.toSet()

    // --- normalizer keeps sentence boundaries ---
    @Test fun normalizerKeepsSentenceBoundaries() {
        assertEquals("never share your otp . tell me the pin", TextNormalizer.normalize("Never share your OTP. Tell me the PIN!"))
        assertEquals("a . b", TextNormalizer.normalize("a... b?!"))
    }
    @Test fun normalizerTrimsEdgeBoundariesAndKeepsDottedAcronyms() {
        assertEquals("please tell me your otp", TextNormalizer.normalize("Please, tell me your OTP!"))
        assertEquals("your kyc has expired", TextNormalizer.normalize("Your K.Y.C. has expired."))
        assertEquals("", TextNormalizer.normalize("..."))
    }

    // --- advisory no longer leaks across sentences ---
    @Test fun advisoryDoesNotLeakIntoNextSentence() {
        // Old behaviour: "do not share ... your otp" looked advisory and gave LOW.
        assertEquals(RiskLevel.HIGH, level("Do not share anything else. Your OTP please"))
        assertEquals(RiskLevel.HIGH, level("Never share it with anyone. Tell me your OTP."))
        assertEquals(RiskLevel.HIGH, level("Never share your OTP. Tell me the PIN"))
    }
    @Test fun advisoryStillWorksWithinItsSentenceAcrossPunctuation() {
        assertEquals(RiskLevel.LOW, level("Never, ever share your OTP with anyone!"))
        assertEquals(RiskLevel.LOW, level("Hello sir. Never share your OTP or CVV. Thank you."))
    }
    @Test fun everyCueInTheSentenceIsTried() {
        // The first cue ("do not send") is too far away; the second ("never share") applies.
        assertEquals(RiskLevel.LOW, level("Do not send money to strangers and never share your OTP"))
        // Leftmost cue has a request word in its gap, but the later cue is still a clean warning.
        assertEquals(RiskLevel.LOW, level("Do not give up now never share your OTP"))
    }
    @Test fun warningAfterTheTermIsAdvisory() {
        assertEquals(RiskLevel.LOW, level("Your OTP should never be shared with anyone"))
        assertEquals(RiskLevel.LOW, level("The PIN must not be shared"))
        assertEquals(RiskLevel.LOW, level("Your CVV is confidential"))
    }
    @Test fun trailingWordsInAnotherSentenceDoNotMakeItAdvisory() {
        assertEquals(RiskLevel.HIGH, level("Tell me your OTP. It should never take long"))
        assertEquals(RiskLevel.HIGH, level("Tell me your OTP, it should never fail"))
    }
    @Test fun transcriptSegmentsAreSeparateSentencesForAnalysis() {
        val t = RollingTranscript()
        t.commit("Do not share anything else")
        t.commit("Your OTP please")
        assertEquals("Do not share anything else Your OTP please", t.text())
        assertEquals(RiskLevel.HIGH, engine.evaluate(t.analysisText()).level)
        assertEquals(RiskLevel.LOW, engine.evaluate(t.text()).level) // proves the boundary matters
    }

    // --- "now" only counts in urgent, imperative contexts ---
    @Test fun bareNowIsNotUrgency() {
        for (p in listOf("How are you now", "Now I understand", "It is now five pm", "I know it is windy right now", "Now let me explain"))
            assertFalse(p, Tactic.URGENCY in tactics(p))
    }
    @Test fun imperativeNowIsUrgency() {
        for (p in listOf("Act now", "Do it now", "Pay now", "Call me now", "Verify your account now", "Transfer the money right now"))
            assertTrue(p, Tactic.URGENCY in tactics(p))
    }
    @Test fun urgencyStillNeverChangesTheLevel() {
        assertEquals(RiskLevel.LOW, level("Act now, this is urgent, final warning"))
        assertEquals(RiskLevel.HIGH, level("Share the OTP now"))
        assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired, verify it now"))
    }
}
