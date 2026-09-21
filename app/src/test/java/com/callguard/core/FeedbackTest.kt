package com.callguard.core

import org.junit.Assert.*
import org.junit.Test

class FeedbackCodecTest {
    private val e = FeedbackEntry(1_790_000_000_000L, RiskLevel.HIGH, listOf("CREDENTIAL_REQUEST", "REMOTE_ACCESS"), "ab12cd34ef567890", Verdict.SCAM)

    @Test fun roundTrips() = assertEquals(e, FeedbackCodec.decode(FeedbackCodec.encode(e)))
    @Test fun roundTripsWithoutANumberOrTactics() {
        val n = FeedbackEntry(5, RiskLevel.LOW, emptyList(), null, Verdict.UNSURE)
        assertEquals(n, FeedbackCodec.decode(FeedbackCodec.encode(n)))
    }
    @Test fun manyEntries() {
        val list = listOf(e, e.copy(atEpochMs = 2, verdict = Verdict.FINE), e.copy(atEpochMs = 3, numberHash = null))
        assertEquals(list, FeedbackCodec.decodeAll(FeedbackCodec.encodeAll(list)))
    }
    @Test fun malformedLinesAreSkippedNotFatal() {
        val text = listOf("", "garbage", "1|HIGH|A|h|SCAM", "x|HIGH||h|SCAM", "1|NOPE||h|SCAM", "1|HIGH||h|MAYBE", FeedbackCodec.encode(e), "1|2|3").joinToString("\n")
        assertEquals(listOf(FeedbackCodec.decode("1|HIGH|A|h|SCAM")!!, e), FeedbackCodec.decodeAll(text))
    }
    @Test fun aLineContainsNoTranscriptOrPhoneNumber() {
        val line = FeedbackCodec.encode(e)
        assertFalse(line.contains("+")); assertEquals(5, line.split('|').size)
    }
}

class FeedbackLogTest {
    private fun fb(t: Long, h: String?, v: Verdict) = FeedbackEntry(t, RiskLevel.MEDIUM, emptyList(), h, v)

    @Test fun onlyTheLatestAnswerPerNumberDecidesTrust() {
        val log = listOf(fb(1, "A", Verdict.FINE), fb(2, "B", Verdict.SCAM), fb(3, "A", Verdict.SCAM), fb(4, "C", Verdict.FINE), fb(5, "C", Verdict.UNSURE))
        assertEquals(emptySet<String>(), FeedbackLog.trustedHashes(log))
        assertEquals(setOf("A"), FeedbackLog.trustedHashes(log + fb(6, "A", Verdict.FINE)))
    }
    @Test fun answersWithoutANumberNeverTrustAnyone() = assertTrue(FeedbackLog.trustedHashes(listOf(fb(1, null, Verdict.FINE))).isEmpty())
    @Test fun theLogIsBounded() {
        var l = emptyList<FeedbackEntry>(); repeat(FeedbackLog.MAX_ENTRIES + 25) { l = FeedbackLog.append(l, fb(it.toLong(), "h", Verdict.UNSURE)) }
        assertEquals(FeedbackLog.MAX_ENTRIES, l.size); assertEquals(25L, l.first().atEpochMs)
    }
    @Test fun countsPerVerdict() =
        assertEquals(mapOf(Verdict.SCAM to 2, Verdict.FINE to 1, Verdict.UNSURE to 0), FeedbackLog.counts(listOf(fb(1, "a", Verdict.SCAM), fb(2, "b", Verdict.SCAM), fb(3, "c", Verdict.FINE))))
}

class FeedbackEffectTest {
    private val rep = NumberReputation()
    private val engineWith = { a: NumberAssessment? -> RiskEngine(number = NumberSignalSource { a }) }

    @Test fun summaryCarriesOnlyTheHashAndTacticNames() {
        val a = rep.assess(CallerContext("+14155550123", inContacts = false, firstTime = true)).copy(numberHash = "deadbeefdeadbeef")
        val t = CallTimeline { 1L }; t.start(0); t.record(1_000, RiskEngine().evaluate("Please tell me your OTP"), 1)
        val s = t.finish(5_000, a)!!
        assertEquals("deadbeefdeadbeef", s.numberHash)
        val entry = FeedbackEntry.from(s, Verdict.SCAM, 99)
        assertEquals(listOf("CREDENTIAL_REQUEST"), entry.tactics); assertEquals("deadbeefdeadbeef", entry.numberHash); assertFalse(FeedbackCodec.encode(entry).contains("4155550123"))
    }
    @Test fun aNumberMarkedSafeGetsAQuieterNumberAssessment() {
        val before = rep.assess(CallerContext("+14155550123", inContacts = false, firstTime = false))
        val after = rep.assess(CallerContext("+14155550123", inContacts = false, firstTime = false, userTrusted = true))
        assertEquals(before.score - 3, after.score); assertTrue("you marked this number as safe" in after.reasons)
    }
    @Test fun trustQuietensTheNumberSignalButNeverAContentAlert() {
        val trusted = rep.assess(CallerContext("+923001234567", inContacts = false, firstTime = false, userTrusted = true))
        val engine = engineWith(trusted)
        assertEquals(RiskLevel.HIGH, engine.evaluate("Please tell me your OTP").level)       // content rule unaffected
        assertEquals(RiskLevel.HIGH, engine.evaluate("Install AnyDesk").level)
        assertEquals(RiskLevel.MEDIUM, engine.evaluate("Your KYC has expired").level)        // and not amplified by a number we trust less about
    }
    @Test fun trustDoesNotHideARealBankImpostorFromANumberWeTrust() {
        // An identity claim from a non-institutional number is still flagged: trust lowers the score but does not remove the claim check.
        val trusted = rep.assess(CallerContext("+919876543210", inContacts = false, firstTime = false, userTrusted = true))
        assertEquals(RiskLevel.MEDIUM, engineWith(trusted).evaluate("This is HDFC Bank calling").level)
    }
    @Test fun feedbackStringsExist() {
        for (l in Lang.values()) for (k in listOf(Ui.FEEDBACK_Q, Ui.FEEDBACK_YES, Ui.FEEDBACK_NO, Ui.FEEDBACK_UNSURE, Ui.FEEDBACK_SAVED, Ui.FEEDBACK_SAFE_NOTE)) assertTrue("$k/$l", UiStrings.get(k, l).isNotBlank())
    }
}
