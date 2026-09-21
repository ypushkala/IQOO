package com.callguard.core

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class CallTimelineTest {
    private val engine = RiskEngine()
    private val epoch = 1_790_000_000_000L // 2026-09-21 ~14:13 UTC
    private fun timeline() = CallTimeline { epoch }.also { it.start(0) }
    private fun CallTimeline.say(atMs: Long, text: String, alerts: Int = 0) = record(atMs, engine.evaluate(text), alerts)

    @Test fun scamCallBecomesAFindingPerTacticWithFirstSeenTimes() {
        val t = timeline()
        t.say(5_000, "Please tell me your OTP", alerts = 1)
        t.say(9_000, "Please tell me your OTP now")
        t.say(65_000, "Please tell me your OTP. Install AnyDesk", alerts = 1)
        val s = t.finish(120_000, null)!!
        assertEquals(RiskLevel.HIGH, s.level); assertEquals(120_000, s.durationMs); assertEquals(2, s.alertCount)
        assertEquals(listOf(Tactic.CREDENTIAL_REQUEST, Tactic.REMOTE_ACCESS), s.findings.map { it.tactic })
        assertEquals(listOf(5_000L, 65_000L), s.findings.map { it.firstAtMs }) // later mentions do not move the first time
        assertTrue(s.advice.any { "OTP" in it && "1930" in it }); assertTrue(s.advice.any { "uninstall" in it })
    }
    @Test fun aHarmlessCallIsReassuring() {
        val t = timeline(); t.say(3_000, "How was your day?"); t.say(9_000, "Shall we have dinner this weekend?")
        val s = t.finish(30_000, null)!!
        assertEquals(RiskLevel.LOW, s.level); assertTrue(s.findings.isEmpty()); assertEquals("Nothing suspicious was detected", s.headline)
        assertEquals(1, s.advice.size); assertTrue(s.advice[0].startsWith("No action needed"))
    }
    @Test fun aSafetyWarningIsGoodNewsNotAFinding() {
        val t = timeline(); t.say(4_000, "Never share your OTP with anyone.")
        val s = t.finish(10_000, null)!!
        assertEquals(RiskLevel.LOW, s.level); assertTrue(s.findings.isEmpty()); assertTrue(s.heardSafetyWarning)
        assertTrue(s.plainText().contains("safety warning"))
    }
    @Test fun weakSignalsAreListedAsMinorNotesOnly() {
        val t = timeline(); t.say(2_000, "Act now, this is urgent. You won a lottery prize")
        val s = t.finish(9_000, null)!!
        assertEquals(RiskLevel.LOW, s.level); assertTrue(s.findings.isEmpty())
        assertTrue(s.minorNotes.containsAll(listOf(Tactic.URGENCY, Tactic.PAYMENT_LURE)))
        assertTrue(s.plainText().contains("Also heard: "))
    }
    @Test fun aTacticThatEscalatesKeepsItsHighestLevel() {
        val t = timeline(); t.say(2_000, "I am calling from the police"); t.say(8_000, "I am calling from the police, there is a case and you will be arrested")
        val f = t.finish(20_000, null)!!.findings.single()
        assertEquals(Tactic.AUTHORITY_THREAT, f.tactic); assertEquals(RiskLevel.HIGH, f.level); assertEquals(2_000, f.firstAtMs)
    }
    @Test fun nothingAnalysedMeansNoSummary() {
        val t = timeline(); assertNull(t.finish(5_000, null)); assertFalse(t.active)
        assertNull(CallTimeline().finish(1, null)) // never started
    }
    @Test fun recordingAfterFinishOrBeforeStartIsIgnored() {
        val t = CallTimeline { epoch }; t.record(1, engine.evaluate("tell me your otp"), 1); assertFalse(t.hasData())
        t.start(0); t.say(1, "tell me your otp"); t.finish(2, null); t.say(3, "install anydesk"); assertNull(t.finish(4, null))
    }
    @Test fun startingAgainClearsThePreviousCall() {
        val t = timeline(); t.say(1, "tell me your otp"); t.start(100)
        t.say(200, "How was your day"); assertTrue(t.finish(300, null)!!.findings.isEmpty())
    }
    @Test fun keepsShortPhrasesNotTheTranscript() {
        val t = timeline(); val said = "Good evening sir this is a long sentence where please tell me your OTP and also your CVV"
        t.say(1_000, said)
        val s = t.finish(5_000, null)!!
        val all = s.findings.flatMap { it.examples + it.reasons } + s.plainText() + s.reportText()
        assertTrue(all.none { "good evening sir" in it.lowercase() })
        assertTrue(s.findings.all { f -> f.examples.size <= 3 && f.examples.all { it.length <= 40 } })
    }
    @Test fun carriesTheCallerAssessmentButOnlyWhenKnown() {
        val a = NumberReputation().assess(CallerContext("+14155550123", inContacts = false, firstTime = true))
        val t = timeline(); t.say(1, "tell me your otp")
        assertTrue(t.finish(2, a)!!.callerSummary!!.startsWith("+14155550123"))
        val u = timeline(); u.say(1, "tell me your otp"); assertNull(u.finish(2, null)!!.callerSummary)
    }
}

class ModelReasonInSummaryTest {
    @Test fun modelLabelIsNotShownAsHeardButItsReasonIs() {
        val g = GemmaSignalSource(clock = { 0L }).also { it.update(GemmaVerdict(RiskLevel.HIGH, "credential request", "Asks for OTP")) }
        val engine = RiskEngine(models = listOf(g))
        val t = CallTimeline { 1L }; t.start(0); t.record(4_000, engine.evaluate("Please tell me your OTP"), 1)
        val s = t.finish(9_000, null)!!
        val f = s.findings.single()
        assertEquals(listOf("otp"), f.examples); assertEquals(listOf("Asks for OTP"), f.reasons)
        val txt = s.plainText(java.time.ZoneId.of("UTC"))
        assertTrue(txt.contains("heard: “otp”. Asks for OTP.")); assertFalse(txt.contains("“credential request”"))
    }
}

class CallSummaryTextTest {
    private val engine = RiskEngine()
    private val utc = ZoneId.of("UTC")
    private fun scam(): CallSummary {
        val t = CallTimeline { 1_790_000_000_000L }; t.start(0)
        t.record(42_000, engine.evaluate("Please tell me your OTP. Install AnyDesk"), 1)
        val a = NumberReputation().assess(CallerContext("+14155550123", inContacts = false, firstTime = true))
        return t.finish(192_000, a)!!
    }

    @Test fun headlinesMatchTheRiskLevel() {
        val s = scam()
        assertEquals("High risk: this call looked like a scam", s.headline)
        assertEquals("Suspicious call: be careful", s.copy(level = RiskLevel.MEDIUM).headline)
        assertEquals("Nothing suspicious was detected", s.copy(level = RiskLevel.LOW).headline)
    }
    @Test fun plainTextIsReadableAndComplete() {
        val txt = scam().plainText(utc)
        for (part in listOf("High risk", "21 Sep 2026", "lasted 3:12", "Caller: +14155550123", "This call used these tactics:",
            "OTP/credential request (high, at 0:42)", "remote-access app", "“otp”", "CallGuard warned you 1 time.", "What to do:", "1930"))
            assertTrue("missing: $part\n$txt", part in txt)
    }
    @Test fun reportHasWhatAnAuthorityNeedsAndNoContent() {
        val r = scam().reportText(utc)
        for (part in listOf("Suspected scam call report", "21 Sep 2026", "3:12", "+14155550123", "Risk assessed: HIGH", "OTP/credential request", "1930", "cybercrime.gov.in", "No audio or transcript"))
            assertTrue("missing: $part\n$r", part in r)
        assertFalse(r.contains("“")); assertFalse(r.lowercase().contains("please tell me"))
    }
    @Test fun durationFormatting() {
        assertEquals("0:00", CallSummary.formatDuration(0)); assertEquals("0:42", CallSummary.formatDuration(42_500))
        assertEquals("3:12", CallSummary.formatDuration(192_000)); assertEquals("61:01", CallSummary.formatDuration(3_661_000)); assertEquals("0:00", CallSummary.formatDuration(-5))
    }
    @Test fun aloneOnePluralisesCorrectly() {
        assertTrue(scam().plainText(utc).contains("1 time.")); assertTrue(scam().copy(alertCount = 3).plainText(utc).contains("3 times."))
    }
    @Test fun adviceIsSpecificToTheTacticsSeen() {
        assertTrue(adviceFor(setOf(Tactic.AUTHORITY_THREAT), RiskLevel.HIGH).any { "digital arrest" in it.lowercase() })
        assertTrue(adviceFor(setOf(Tactic.ACCOUNT_THREAT), RiskLevel.MEDIUM).any { "official app" in it })
        assertTrue(adviceFor(setOf(Tactic.PAYMENT_LURE), RiskLevel.MEDIUM).any { "fee" in it })
        assertTrue(adviceFor(setOf(Tactic.SECRECY), RiskLevel.MEDIUM).any { "family" in it })
        assertTrue(adviceFor(setOf(Tactic.IDENTITY_MISMATCH), RiskLevel.MEDIUM).any { "call this number back" in it })
        assertTrue(adviceFor(setOf(Tactic.OTHER), RiskLevel.MEDIUM).any { "Be careful" in it })
        assertTrue(adviceFor(setOf(Tactic.CREDENTIAL_REQUEST), RiskLevel.HIGH).last().contains("cybercrime.gov.in"))
    }
}

class WarningPlanTest {
    private val engine = RiskEngine()

    @Test fun noSpokenWarningCanTriggerTheDetector() { // with the phone on speaker the mic can hear the warning
        for (m in WarningPlans.allMessages) {
            val r = engine.evaluate(m)
            assertEquals(m, RiskLevel.LOW, r.level)
            assertTrue(m, r.signals.none { it.level >= RiskLevel.MEDIUM })
        }
        for (acc in listOf(false, true)) for (l in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH))
            for (m in WarningPlans.plan(l, acc).messages) assertEquals(RiskLevel.LOW, engine.evaluate(m).level)
    }
    @Test fun standardWarningsKeepTheOriginalBehaviour() {
        val h = WarningPlans.plan(RiskLevel.HIGH, accessible = false)
        assertEquals(1, h.messages.size); assertEquals(1.0f, h.speechRate); assertFalse(h.boostVolume)
        assertArrayEquals(longArrayOf(0, 500, 150, 500, 150, 500), h.vibration)
        assertArrayEquals(longArrayOf(0, 400), WarningPlans.plan(RiskLevel.MEDIUM, false).vibration)
    }
    @Test fun accessibleWarningsAreSlowerRepeatedLouderAndBuzzLonger() {
        for (l in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH)) {
            val s = WarningPlans.plan(l, false); val a = WarningPlans.plan(l, true)
            assertTrue(a.speechRate < s.speechRate); assertTrue(a.boostVolume); assertFalse(s.boostVolume)
            assertEquals(2, a.messages.size); assertEquals(a.messages[0], a.messages[1])
            assertTrue(a.vibration.sum() > s.vibration.sum())
        }
    }
    @Test fun highAndMediumWarningsDiffer() {
        assertNotEquals(WarningPlans.plan(RiskLevel.HIGH, true).messages, WarningPlans.plan(RiskLevel.MEDIUM, true).messages)
        assertTrue(WarningPlans.plan(RiskLevel.HIGH, true).messages[0].contains("Hang up"))
    }
    @Test fun planEqualityIsByValue() = assertEquals(WarningPlans.plan(RiskLevel.HIGH, true), WarningPlans.plan(RiskLevel.HIGH, true))
}
