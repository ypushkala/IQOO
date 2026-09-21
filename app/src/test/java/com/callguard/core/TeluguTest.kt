package com.callguard.core

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

private fun telugu(s: String) = s.any { it.code in 0x0C00..0x0C7F }
private fun dev(s: String) = s.any { it.code in 0x0900..0x097F }

/** The exact strings the Omnilingual model wrote for Geeta's Telugu clips (run on the Mac). */
class TeluguDetectionTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level
    private fun tactics(s: String) = engine.evaluate(s).signals.map { it.tactic }.toSet()

    @Test fun scamsInTeluguAreDetected() {
        assertEquals(RiskLevel.HIGH, level("మీ otp చెప్పండి ఇది చాలా అధ్యవసరం"))
        assertEquals(RiskLevel.MEDIUM, level("మీ కేవైసి గడువు మొగిసింది మీ కాత ఈ రోజు బ్లోక్ అవుతుంది"))
        assertEquals(RiskLevel.HIGH, level("నేను సbi నుండి మాట్లాడుతున్నాను మీరు డిజిటల్ అరెస్ట్లో ఉన్నారు ఎవరికీ చెప్పకండి"))
        assertEquals(RiskLevel.HIGH, level("ఎ్నిడెస్క్ దావున్లోడ్ చేసుకోండి నేను మీకు సహాయం చేస్తాను"))
        assertEquals(RiskLevel.MEDIUM, level("మీ సింకార్డ్ రెండు గంటలో బ్లోక్ అవుతుంది"))
    }
    @Test fun ordinaryTeluguAndAdvisoriesStayLow() {
        assertEquals(RiskLevel.LOW, level("ఈ రోజు సాయంత్రం ఇంటికి రండి భోజనం చేర్దాం"))
        assertEquals(RiskLevel.LOW, level("మీ otp ఎవరికి ఎప్పుడూ చెప్పకండి"))          // "never tell anyone your OTP"
        assertEquals(RiskLevel.LOW, level("మీరు లాటరీ గెలిచారు ప్రాసె్సింగ్ ఫీజీ చెళ్లించండి")) // a lure alone is LOW; Gemma/other signals add more
    }
    @Test fun teluguTacticsAreTagged() {
        assertTrue(Tactic.AUTHORITY_THREAT in tactics("నేను సbi నుండి మాట్లాడుతున్నాను మీరు డిజిటల్ అరెస్ట్లో ఉన్నారు ఎవరికీ చెప్పకండి"))
        assertTrue(Tactic.SECRECY in tactics("నేను సbi నుండి మాట్లాడుతున్నాను మీరు డిజిటల్ అరెస్ట్లో ఉన్నారు ఎవరికీ చెప్పకండి"))
        assertTrue(Tactic.PAYMENT_LURE in tactics("మీరు లాటరీ గెలిచారు"))
        assertTrue(Tactic.ACCOUNT_THREAT in tactics("మీ సింకార్డ్ రెండు గంటలో బ్లోక్ అవుతుంది"))
    }
    @Test fun aRequestIsNotMistakenForAnAdvisory() {
        assertEquals(RiskLevel.HIGH, level("మీ otp ఎవరికీ చెప్పకండి కానీ నాకు otp చెప్పండి"))
        assertEquals(RiskLevel.HIGH, level("ఎవరికీ చెప్పకండి మీ otp చెప్పండి"))
    }
    @Test fun secrecyAloneIsMediumInTelugu() = assertEquals(RiskLevel.MEDIUM, level("ఎవరికీ చెప్పకండి"))
    @Test fun romanisedTeluguFromWhisperIsUnderstood() {
        assertEquals(RiskLevel.LOW, level("Mi OTP Yavari ki yeppadu cheppak"))            // Whisper's romanised Telugu for the advisory
        assertEquals(RiskLevel.HIGH, level("Mi OTP cheppandi"))
        assertEquals(RiskLevel.MEDIUM, level("evariki cheppakandi"))
    }
    @Test fun normalizationOutputIsReadableCanonicalText() {
        assertEquals("otp cheppandi", TextNormalizer.normalize("మీ otp చెప్పండి"))
        assertTrue(TextNormalizer.normalize("మీ otp ఎవరికి ఎప్పుడూ చెప్పకండి").contains("never share otp"))
        assertTrue(TextNormalizer.normalize("డిజిటల్ అరెస్ట్లో").startsWith("digital arrest"))
    }
    @Test fun teluguDoesNotDisturbEnglishOrHindi() {
        assertEquals(RiskLevel.HIGH, level("Please tell me your OTP")); assertEquals(RiskLevel.LOW, level("How was your day?"))
        assertEquals(RiskLevel.HIGH, level("आपका ओटीपी बताइए")); assertEquals(RiskLevel.LOW, level("किसी को अपना ओटीपी कभी मत बताइए।"))
    }
    @Test fun zeroWidthJoinersInTeluguAreIgnored() =
        assertEquals(RiskLevel.HIGH, level("ఎనీడెస్క్ డౌన్‌లోడ్ చేసుకోండి"))
}

/** Omnilingual's Hindi/Hinglish output (Devanagari), from the Mac run over the replay clips. */
class OmnilingualHindiTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level
    @Test fun scamsAreDetectedFromOmnilingualText() {
        assertEquals(RiskLevel.HIGH, level("आपका और tीpी बताए यह बहुत जरूरी है"))
        assertEquals(RiskLevel.MEDIUM, level("आपका केवायसी खत्म हो गया है आपका खाता आज बंद हो जाएगा"))
        assertEquals(RiskLevel.HIGH, level("मैं सीबीआई से बोल रहा हूँ आप दिजितल अरिस्ट में हैं किसी को मत बताना"))
        assertEquals(RiskLevel.HIGH, level("एनी देस डाउन लोड कीजिए ताकि मैं आपकी मदद कर सकू"))
        assertEquals(RiskLevel.MEDIUM, level("आपका सिमकार् दो घंते में बंद हो जाएगा"))
        assertEquals(RiskLevel.HIGH, level("पुलिस स्टेशन से बोल रहा हू आपके नाम पर वॉरंट जारी हुआ है तुरंट पैसे भेजिए"))
        assertEquals(RiskLevel.HIGH, level("आपना एtीएmपिन और सीवीवी नंबर बताइए"))
        assertEquals(RiskLevel.HIGH, level("तीमव्यूर दाउनलोड कीजे मैं आपकी स्क्रीन देखूंगा"))
        assertEquals(RiskLevel.MEDIUM, level("आपका बैंक खाता बंद होने वाला है अभी केवायसी अपीडट कीजिए"))
        assertEquals(RiskLevel.MEDIUM, level("आपका काइक एक्सपायर हो गया है अकाउंट आरज ब्लॉक हो जायएगा"))
    }
    @Test fun advisoriesAndNormalTalkStayLow() {
        assertEquals(RiskLevel.LOW, level("किसी को अपना औवतीपी कभी मत बताइए"))
        assertEquals(RiskLevel.LOW, level("किसी को अपना रoटीपी का भी मैट बताना"))
        assertEquals(RiskLevel.LOW, level("किसी को भी अपना पिन मैट बताना यह बैंक की सैल है"))
        assertEquals(RiskLevel.LOW, level("बैंक कभी भी आपसे और तीपी नहीं मालता"))
        assertEquals(RiskLevel.LOW, level("आज खाना क्या बनाया है शाम को घर आ जाना"))
    }
}

class ScriptAndLanguageTest {
    @Test fun scriptDetection() {
        assertEquals(Script.TELUGU, IndicScript.detect("మీ otp చెప్పండి")); assertEquals(Script.DEVANAGARI, IndicScript.detect("आपका ओटीपी"))
        assertEquals(Script.ARABIC, IndicScript.detect("آپ کا")); assertEquals(Script.TAMIL, IndicScript.detect("உங்கள் ஓடிபி"))
        assertNull(IndicScript.detect("Please tell me your OTP")); assertNull(IndicScript.detect("")); assertNull(IndicScript.detect("a ఒ"))
    }
    @Test fun scriptToLanguage() {
        assertEquals(Lang.TE, IndicScript.langOf(Script.TELUGU)); assertEquals(Lang.HI, IndicScript.langOf(Script.DEVANAGARI))
        assertEquals(Lang.HI, IndicScript.langOf(Script.ARABIC)); assertNull(IndicScript.langOf(Script.TAMIL)); assertNull(IndicScript.langOf(null))
        assertEquals("te", IndicScript.code(Script.TELUGU)); assertEquals("en", IndicScript.code(null))
    }
    @Test fun trackerFollowsTheLatestLanguage() {
        val t = CallLanguageTracker()
        t.onSegment("Please tell me your OTP", "en"); assertEquals(Lang.EN, t.callLanguage())
        t.onSegment("మీ otp చెప్పండి", "te"); assertEquals(Lang.TE, t.callLanguage()); assertEquals(Lang.TE, t.resolve(LanguageSetting.AUTO))
        t.onSegment("आपका ओटीपी बताइए", "hi"); assertEquals(Lang.HI, t.callLanguage()) // the most recent non-English language wins
        assertTrue(t.callLooksHindi())
    }
    @Test fun romanisedTeluguAndTheDetectedCodeAreEnough() {
        assertEquals(Lang.TE, CallLanguageTracker.spokenLang("Mi OTP Yavari ki yeppadu cheppak", "te"))
        assertEquals(Lang.TE, CallLanguageTracker.spokenLang("meeru otp cheppandi", "en"))
        assertEquals(Lang.TE, CallLanguageTracker.spokenLang("anything", "te"))
        assertNull(CallLanguageTracker.spokenLang("How was your day", "en"))
    }
    @Test fun settingsOverrideTheCall() {
        val t = CallLanguageTracker(); t.onSegment("మీ otp చెప్పండి", "te")
        assertEquals(Lang.EN, t.resolve(LanguageSetting.ENGLISH)); assertEquals(Lang.HI, t.resolve(LanguageSetting.HINDI)); assertEquals(Lang.TE, t.resolve(LanguageSetting.TELUGU))
    }
    @Test fun otherIndianScriptsAreNotMistakenForHindiOrTelugu() =
        assertNull(CallLanguageTracker.spokenLang("உங்கள் ஓடிபி சொல்லுங்கள்", "ta"))
}

class TeluguTextTest {
    private val engine = RiskEngine()
    private val utc = ZoneId.of("UTC")
    private fun summary(): CallSummary {
        val t = CallTimeline { 1_790_000_000_000L }; t.start(0)
        t.record(42_000, engine.evaluate("Please tell me your OTP. Install AnyDesk. Don't tell anyone"), 2)
        return t.finish(192_000, NumberReputation().assess(CallerContext("+14155550123", inContacts = false, firstTime = true)))!!
    }

    @Test fun teluguSummaryIsFullyInTelugu() {
        val txt = summary().plainText(utc, Lang.TE)
        for (part in listOf("చాలా ప్రమాదం", "21 సెప్టెంబర్ 2026", "వ్యవధి 3:12", "కాలర్: +14155550123", "ఈ కాల్‌లో ఉపయోగించిన పద్ధతులు:", "ఓటీపీ/రహస్య కోడ్ అడగడం",
            "అధిక ప్రమాదం", "0:42 వద్ద", "రిమోట్-యాక్సెస్ యాప్", "వినిపించింది", "CallGuard మిమ్మల్ని 2 సార్లు హెచ్చరించింది.", "ఏమి చేయాలి:", "1930"))
            assertTrue("missing: $part\n$txt", part in txt)
        for (english in listOf("What to do:", "This call used these tactics", "Call at", "lasted", "warned you", "High risk"))
            assertFalse("English left over: $english\n$txt", english in txt)
    }
    @Test fun everyTacticLevelAndAdviceHasTelugu() {
        for (t in Tactic.values()) { assertTrue(t.name, telugu(Strings.tactic(t, Lang.TE))); assertTrue(t.name, Strings.adviceFor(setOf(t), RiskLevel.HIGH, Lang.TE).all { telugu(it) }) }
        for (l in RiskLevel.values()) assertTrue(telugu(Strings.headline(l, Lang.TE)) && telugu(Strings.levelWord(l, Lang.TE)))
        assertEquals(12, Strings.months(Lang.TE).size); assertTrue(telugu(Strings.adviceFor(emptySet(), RiskLevel.LOW, Lang.TE).single()))
        assertEquals(Strings.adviceFor(setOf(Tactic.CREDENTIAL_REQUEST), RiskLevel.HIGH, Lang.EN).size, Strings.adviceFor(setOf(Tactic.CREDENTIAL_REQUEST), RiskLevel.HIGH, Lang.TE).size)
    }
    @Test fun englishAndHindiSummariesAreUnchanged() {
        val s = summary()
        assertTrue("High risk: this call looked like a scam" in s.plainText(utc)); assertFalse(telugu(s.plainText(utc)))
        assertTrue("बहुत ख़तरा" in s.plainText(utc, Lang.HI)); assertFalse(telugu(s.plainText(utc, Lang.HI)))
    }
    @Test fun teluguWarningsAreTeluguPacedLikeTheirTwinsAndCannotTriggerTheDetector() {
        for (l in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH)) for (acc in listOf(false, true)) {
            val p = WarningPlans.plan(l, acc, Lang.TE); val en = WarningPlans.plan(l, acc, Lang.EN)
            assertEquals(Lang.TE, p.lang); assertTrue(p.messages.all { telugu(it) })
            assertEquals(en.speechRate, p.speechRate); assertEquals(en.boostVolume, p.boostVolume); assertArrayEquals(en.vibration, p.vibration)
            for (m in p.messages) { val r = engine.evaluate(m); assertEquals(m, RiskLevel.LOW, r.level); assertTrue(m, r.signals.none { it.level >= RiskLevel.MEDIUM }) }
        }
    }
    @Test fun teluguFamilyMessage() {
        val m = FamilyAlert.message(RiskLevel.HIGH, listOf(Tactic.CREDENTIAL_REQUEST), "+14155550123", Lang.TE)
        for (part in listOf("CallGuard హెచ్చరిక", "అధిక ప్రమాదం", "ఓటీపీ/రహస్య కోడ్ అడగడం", "కాలర్: +14155550123", "దయచేసి నాకు ఫోన్ చేయండి")) assertTrue("missing: $part\n$m", part in m)
        assertFalse("Caller" in FamilyAlert.message(RiskLevel.LOW, emptyList(), null, Lang.TE))
    }
    @Test fun noOtherLanguageBleedsIntoTelugu() {
        val m = FamilyAlert.message(RiskLevel.HIGH, listOf(Tactic.SECRECY), "x", Lang.TE); assertFalse(dev(m))
    }
}

class IndicRoutingTest {
    private fun r(d: String, ms: Long = 3_000, chars: Int = 30, indic: Boolean = true) = LanguagePolicy.route(d, ms, chars, indic)

    @Test fun englishNeverLeavesWhisper() {
        assertEquals(LanguagePolicy.Route.Native, r("en")); assertEquals(LanguagePolicy.Route.Native, r("", indic = true))
    }
    @Test fun anyNonEnglishLabelIsReReadByTheIndicModel() { // Whisper mislabelled Telugu as Punjabi (pa) on the Mac test
        for (l in listOf("te", "hi", "ur", "pa", "ta", "kn", "ml", "bn", "mr", "gu")) assertEquals(l, LanguagePolicy.Route.Indic, r(l))
        for (l in listOf("ja", "es", "ru", "tl")) assertEquals(l, LanguagePolicy.Route.Indic, r(l)) // also catches Indian speech Whisper mislabelled as anything
    }
    @Test fun shortBlipsLabelledForeignAreStillNoiseButShortIndianSpeechIsKept() {
        assertEquals(LanguagePolicy.Route.Drop, r("ja", ms = 400, chars = 5))
        assertEquals(LanguagePolicy.Route.Indic, r("te", ms = 700, chars = 5)) // "OTP cheppandi" can be under a second
    }
    @Test fun withoutTheIndicModelBehaviourIsExactlyAsBefore() {
        assertEquals(LanguagePolicy.Route.Translate("hi"), r("ur", indic = false)); assertEquals(LanguagePolicy.Route.Translate("te"), r("te", indic = false))
        assertEquals(LanguagePolicy.Route.Native, r("hi", chars = 5, indic = false)); assertEquals(LanguagePolicy.Route.RetryAsEnglish, r("ja", indic = false))
        assertEquals(LanguagePolicy.Route.Drop, r("ja", ms = 450, chars = 6, indic = false))
    }
}

class IndicTranscriptForGemmaTest {
    @Test fun nativeScriptWithoutTranslationIsGivenToGemmaAsCanonicalLatin() {
        val t = RollingTranscript(); t.commit("आपका केवायसी खत्म हो गया है आपका खाता आज बंद हो जाएगा")
        assertFalse(t.modelText().any { it.code in 0x0900..0x097F })
        for (w in listOf("kyc", "account", "blocked")) assertTrue("$w in ${t.modelText()}", w in t.modelText())
        val te = RollingTranscript(); te.commit("మీ సింకార్డ్ రెండు గంటలో బ్లోక్ అవుతుంది")
        assertFalse(te.modelText().any { it.code in 0x0C00..0x0C7F }); assertTrue("sim card" in te.modelText() && "blocked" in te.modelText())
    }
    @Test fun latinAndTranslatedSegmentsAreUnchanged() {
        val a = RollingTranscript(); a.commit("Please tell me your OTP"); assertEquals("Please tell me your OTP", a.modelText())
        val b = RollingTranscript(); b.commit("आपका ओटीपी बताइए", "Tell me your OTP"); assertEquals("Tell me your OTP", b.modelText())
    }
}

class AdvisoryVerbAfterTest {
    private val engine = RiskEngine()
    @Test fun aRequestVerbAfterTheTermCancelsAnEarlierWarning() {
        assertEquals(RiskLevel.HIGH, engine.evaluate("kisi ko otp mat batana, otp bhejo").level)
        assertEquals(RiskLevel.HIGH, engine.evaluate("ఎవరికీ చెప్పకండి మీ otp చెప్పండి").level)
    }
    @Test fun englishAndOrdinaryAdvisoriesAreUnaffected() {
        for (s in listOf("Never share your OTP or PIN with anyone", "Your OTP should never be shared", "Never share your OTP. Tell me the PIN".let { "Never share your OTP with anyone" }))
            assertEquals(s, RiskLevel.LOW, engine.evaluate(s).level)
        assertEquals(RiskLevel.HIGH, engine.evaluate("Never share your OTP. Tell me the PIN").level)
    }
}

class CallerAlertTextTest {
    private val rep = NumberReputation(listOf(ScamPrefix("92", 2, "reported prefix")))
    private fun a(number: String?, contacts: Boolean? = null, first: Boolean? = null) = rep.assess(CallerContext(number, contacts, first))

    @Test fun normalNumbersStaySilent() {
        assertNull(CallerAlertText.build(a("+919876543210", contacts = true, first = false), Lang.EN, monitoring = false))
        assertNull(CallerAlertText.build(a("1600123456"), Lang.HI, monitoring = true))
    }
    @Test fun suspiciousNumbersGetAHeadsUpWithTheirReasons() {
        val t = CallerAlertText.build(a("+14155550123", contacts = false, first = true), Lang.EN, monitoring = false)!!
        assertEquals("Likely scam number calling", t.title)
        assertTrue("International number" in t.body && "not in your contacts" in t.body && "Protect this call" in t.body)
        assertEquals("Unusual incoming call", CallerAlertText.build(a(null), Lang.EN, monitoring = false)!!.title) // withheld = elevated
    }
    @Test fun neverContainsThePhoneNumber() {
        for (l in Lang.values()) { val t = CallerAlertText.build(a("+14155550123", contacts = false, first = true), l, false)!!; assertFalse((t.title + t.body).contains("4155550123")) }
    }
    @Test fun alreadyMonitoringDropsTheTapInstruction() {
        val on = CallerAlertText.build(a("+14155550123", contacts = false, first = true), Lang.EN, monitoring = true)!!
        assertTrue("CallGuard is listening." in on.body && "Protect this call" !in on.body)
    }
    @Test fun hindiAndTeluguHeadsUp() {
        val hi = CallerAlertText.build(a("+14155550123", contacts = false, first = true), Lang.HI, false)!!
        val te = CallerAlertText.build(a("+14155550123", contacts = false, first = true), Lang.TE, false)!!
        assertTrue(dev(hi.title) && dev(hi.body)); assertTrue(telugu(te.title) && telugu(te.body))
        assertTrue(dev(CallerAlertText.actionLabel(Lang.HI)) && telugu(CallerAlertText.actionLabel(Lang.TE)))
    }
    @Test fun bootReminderIsLocalised() {
        assertEquals("CallGuard is off after the restart", CallerAlertText.bootTitle(Lang.EN))
        assertTrue(dev(CallerAlertText.bootTitle(Lang.HI)) && telugu(CallerAlertText.bootTitle(Lang.TE)) && telugu(CallerAlertText.bootBody(Lang.TE)))
    }
}

/** Strings the phone's pipeline produced (Whisper -> Omnilingual) on the real device replay. */
class TeluguOnDeviceOutputsTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level
    @Test fun outputsSeenOnThePhone() {
        assertEquals(RiskLevel.HIGH, level("ఈ otp చెప్పండి ఇది చాలా అత్యవసరం"))
        assertEquals(RiskLevel.MEDIUM, level("కాతా ఈ రోజు బ్లోక్ అబుతుంది"))
        assertEquals(RiskLevel.HIGH, level("నేను cbi నుండి మాట్లాడుతున్నాను మీరు డిజిటల్ అరెస్ట్ లో ఉన్నారు ఎవరికీ చెప్పకండి"))
        assertEquals(RiskLevel.MEDIUM, level("మీ సింకార్డ్ రెండు గంటలో బ్లోక అవుతుంది"))   // virama-less spelling that caused a miss
        assertEquals(RiskLevel.LOW, level("ఈ రోజు సాయంత్రం ఇంటికి రండి భోజనం చేద్దాం"))
        assertEquals(RiskLevel.LOW, level("మీ otp ఎవరికి ఎప్పుడూ చెప్పకండి"))
        assertEquals(RiskLevel.LOW, level("మీరు లాటరీ గెలిచారు ప్రాసెస్సింగ్ ఫీజీ చెల్లించండి"))
        assertEquals(RiskLevel.HIGH, level("मैं सीबी आई से बोल रहा हूँ आप दिजितल अरिस्ट में हैं किसी को मत बताना"))
        assertEquals(RiskLevel.MEDIUM, level("तम हो गया है आपका खाता आज बंद हो जाएगा"))
    }
}
