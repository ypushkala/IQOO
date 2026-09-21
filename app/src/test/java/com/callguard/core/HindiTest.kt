package com.callguard.core

import org.junit.Assert.*
import org.junit.Test

class HindiNormalizerTest {
    private fun n(s: String) = TextNormalizer.normalize(s)

    @Test fun devanagariScamVocabularyBecomesCanonicalTokens() {
        assertEquals("aapka otp batana urgent", n("आपका ओटीपी बताइए, ज़रूरी"))
        assertEquals("kyc", n("केवाईसी"))
        assertEquals("kyc", n("के वाई सी"))
        assertEquals("digital arrest", n("डिजिटल अरेस्ट"))
        assertEquals("install anydesk", n("install एनीडेस्क"))
    }
    @Test fun nuktaVariantsNormalize() {
        assertEquals("urgent", n("ज़रूरी")) // ज + nukta
        assertEquals("urgent", n("ज़रूरी"))       // precomposed ज़
    }
    @Test fun dandaEndsASentence() =
        assertEquals("aapka otp batana . do not tell anyone", n("आपका ओटीपी बताइए। किसी को मत बताना।"))
    @Test fun unknownDevanagariIsDroppedNotGlued() =
        assertEquals("otp", n("नमस्ते ओटीपी"))
    @Test fun devanagariNeedsWordBoundaries() {
        // "न" (nahi) must not be picked out of the middle of another word
        assertEquals("", n("नमस्ते"))
    }
    @Test fun hinglishNegationBecomesEnglishAdvisory() {
        assertEquals("never share otp", n("Kisi ko apna OTP kabhi mat batana"))
        assertEquals("never share pin", n("apna PIN kisi ko nahi dena"))
        assertEquals("bank never ask for otp", n("bank kabhi OTP nahi mangta"))
    }
    @Test fun hinglishStateChangesBecomeEnglish() {
        assertEquals("your kyc expired", n("your KYC expire ho gaya"))
        assertEquals("account aaj blocked", n("account aaj block ho jayega"))
        assertEquals("sim card blocked", n("sim card band kar diya jayega"))
    }
    @Test fun observedWhisperSpellingVariantsAreRepaired() {
        assertEquals("do not tell anyone", n("kisi komat batana"))
        assertEquals("never share otp", n("khisi ko apnar oTP kabhimat batana"))
    }
    @Test fun englishIsUnchangedByTheHindiLayer() {
        for (s in listOf("Please tell me your OTP", "Install AnyDesk", "Your KYC has expired", "Never share your OTP with anyone",
            "How was your day? Shall we have dinner"))
            assertEquals(s, s.let { HindiNormalizer.hinglishToEnglish(HindiNormalizer.scriptsToLatin(TextNormalizer.normalize(it))) }, n(s))
    }
}

class HindiDetectionTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level
    private fun tactics(s: String) = engine.evaluate(s).signals.map { it.tactic }.toSet()

    // ---- Devanagari (what Whisper writes for Hindi speech) ----
    @Test fun devanagariScamsAreDetected() {
        assertEquals(RiskLevel.HIGH, level("आपका ओटीपी बताइए, यह बहुत ज़रूरी है।"))
        assertEquals(RiskLevel.MEDIUM, level("आपका केवाईसी खत्म हो गया है, आपका खाता आज बंद हो जाएगा।"))
        assertEquals(RiskLevel.HIGH, level("मैं सीबीआई से बोल रहा हूँ, आप डिजिटल अरेस्ट में हैं, किसी को मत बताना।"))
        assertEquals(RiskLevel.HIGH, level("एनीडेस्क डाउनलोड कीजिए ताकि मैं आपकी मदद कर सकूँ।"))
        assertEquals(RiskLevel.MEDIUM, level("आपका सिम कार्ड दो घंटे में बंद हो जाएगा।"))
    }
    @Test fun devanagariNormalAndAdvisoryStayLow() {
        assertEquals(RiskLevel.LOW, level("आज खाना क्या बनाया है? शाम को घर आ जाना।"))
        assertEquals(RiskLevel.LOW, level("किसी को अपना ओटीपी कभी मत बताइए।"))
        assertEquals(RiskLevel.LOW, level("आपने लॉटरी जीती है, प्रोसेसिंग फीस जमा कीजिए।")) // lure alone is LOW; Gemma may add MEDIUM
    }
    @Test fun devanagariTacticsAreTagged() {
        val t = tactics("मैं सीबीआई से बोल रहा हूँ, आप डिजिटल अरेस्ट में हैं, किसी को मत बताना।")
        assertTrue(Tactic.AUTHORITY_THREAT in t); assertTrue(Tactic.SECRECY in t)
        assertTrue(Tactic.PAYMENT_LURE in tactics("आपने लॉटरी जीती है"))
        assertTrue(Tactic.URGENCY in tactics("तुरंत बताइए"))
    }

    // ---- Hinglish (Hindi in Latin letters) ----
    @Test fun hinglishScamsAreDetected() {
        assertEquals(RiskLevel.HIGH, level("Aapka OTP bataiye, yeh bahut urgent hai."))
        assertEquals(RiskLevel.MEDIUM, level("Aapka KYC expire ho gaya hai, account aaj block ho jayega."))
        assertEquals(RiskLevel.HIGH, level("Main CBI se bol raha hoon, aap digital arrest mein hain, kisi ko mat batana."))
        assertEquals(RiskLevel.HIGH, level("AnyDesk download kijiye taaki main aapki help kar sakoon."))
        assertEquals(RiskLevel.MEDIUM, level("Aapka SIM card band ho jayega, turant update kijiye."))
    }
    @Test fun hinglishNormalAndAdvisoryStayLow() {
        assertEquals(RiskLevel.LOW, level("Aaj shaam ko ghar aa jana, khana bahar khayenge."))
        assertEquals(RiskLevel.LOW, level("Kisi ko apna OTP kabhi mat batana."))
        assertEquals(RiskLevel.LOW, level("Bank kabhi OTP nahi mangta hai."))
        assertEquals(RiskLevel.LOW, level("Apna PIN kisi ko nahi dena."))
    }
    @Test fun aRequestIsNotMistakenForAnAdvisory() {
        assertEquals(RiskLevel.HIGH, level("OTP batao abhi"))
        assertEquals(RiskLevel.HIGH, level("Aap OTP nahi batayenge to account block ho jayega"))
        assertEquals(RiskLevel.HIGH, level("Kisi ko mat batana, bas OTP bata do"))
    }
    @Test fun secrecyInHinglishIsMedium() {
        assertEquals(RiskLevel.MEDIUM, level("Yeh baat kisi ko mat batana"))
        assertEquals(RiskLevel.MEDIUM, level("Yeh baat secret rakhna"))
    }

    // ---- exact strings the phone's Whisper produced from Hindi/Hinglish test speech ----
    @Test fun realWhisperOutputsFromTheDevice() {
        assertEquals(RiskLevel.HIGH, level("I am speaking with the FBI, you are in digital arrest, don't tell anyone."))
        assertEquals(RiskLevel.HIGH, level("main cbi saybol raha hun, ab digital arrest me han, kisi komat batana"))
        assertEquals(RiskLevel.MEDIUM, level("your SIM card will be closed in 2 hours"))
        assertEquals(RiskLevel.HIGH, level("your OTP Bata, this is a very urgent matter"))
        assertEquals(RiskLevel.LOW, level("khisi ko apnar oTP kabhimat batana"))
        assertEquals(RiskLevel.LOW, level("Today evening, every day, food is ready."))
        assertEquals(RiskLevel.LOW, level("Today, what is the food made today? Come home in the evening."))
        assertTrue(Tactic.PAYMENT_LURE in tactics("Aap Ne Lotri Jiti Hai Processing Feast Jama Ki Jee"))
    }

    // ---- unrelated Hindi must not alert ----
    @Test fun ordinaryHindiIsLow() {
        for (s in listOf("आप कैसे हैं? मैं ठीक हूँ।", "कल हम बाज़ार जाएँगे", "Aap kaise hain? Main theek hoon.", "Kal hum bazaar jayenge, tum aa jana"))
            assertEquals(s, RiskLevel.LOW, level(s))
    }
    @Test fun fusionWithGemmaStillCapsAdvisoryInHindi() {
        val src = GemmaSignalSource(clock = { 0L })
        src.update(GemmaVerdict(RiskLevel.HIGH, "credential request", "r"))
        val fused = RiskEngine(models = listOf(src))
        assertEquals(RiskLevel.LOW, fused.evaluate("Kisi ko apna OTP kabhi mat batana").level)
        assertEquals(RiskLevel.LOW, fused.evaluate("किसी को अपना ओटीपी कभी मत बताइए।").level)
    }
}

class BilingualTranscriptTest {
    @Test fun englishOnlySegmentsBehaveAsBefore() {
        val t = RollingTranscript()
        t.commit("Please tell me your OTP")
        assertEquals("Please tell me your OTP", t.text())
        assertEquals("Please tell me your OTP", t.analysisText())
        assertEquals("Please tell me your OTP", t.modelText())
    }
    @Test fun translationIsShownAndAnalysedAsItsOwnSentence() {
        val t = RollingTranscript()
        t.commit("आपका ओटीपी बताइए", "Tell me your OTP")
        assertEquals("आपका ओटीपी बताइए (Tell me your OTP)", t.text())
        assertEquals("आपका ओटीपी बताइए . Tell me your OTP", t.analysisText())
        assertEquals("Tell me your OTP", t.modelText()) // Devanagari is not sent to Gemma
    }
    @Test fun romanizedHinglishIsKeptForGemmaAlongsideTheTranslation() {
        val t = RollingTranscript()
        t.commit("kisi ko mat batana", "Don't tell anyone")
        assertEquals("kisi ko mat batana. Don't tell anyone", t.modelText())
    }
    @Test fun identicalOrEmptyTranslationIsDropped() {
        val t = RollingTranscript()
        t.commit("hello there", "Hello there"); t.commit("namaste", "  ")
        assertEquals("hello there namaste", t.text())
    }
    @Test fun trimsOldestBilingualSegments() {
        val t = RollingTranscript(maxChars = 60)
        repeat(6) { t.commit("segment number $it", "english $it") }
        assertFalse(t.text().contains("number 0")); assertTrue(t.text().contains("number 5"))
    }
    @Test fun scriptDetection() {
        assertTrue(RollingTranscript.isLatin("kisi ko mat batana"))
        assertFalse(RollingTranscript.isLatin("किसी को"))
        assertFalse(RollingTranscript.isLatin("آپ کا"))
    }
}

class LanguagePolicyTest {
    private val r = { d: String -> LanguagePolicy.route(d) }

    @Test fun englishAndUndetectedAreUsedAsIs() {
        assertEquals(LanguagePolicy.Route.Native, r("en")); assertEquals(LanguagePolicy.Route.Native, r(""))
    }
    @Test fun hindiAndUrduTranslateAsHindi() {
        assertEquals(LanguagePolicy.Route.Translate("hi"), r("hi"))
        assertEquals(LanguagePolicy.Route.Translate("hi"), r("ur")) // Whisper often labels Hindi as Urdu
    }
    @Test fun regionalIndianLanguagesTranslateAsThemselves() {
        for (l in listOf("bn", "ta", "te", "mr", "gu", "kn", "ml", "pa")) assertEquals(LanguagePolicy.Route.Translate(l), r(l))
    }
    @Test fun noiseLanguagesSeenOnTheDeviceAreRetriedAsEnglishNotTranslated() {
        for (l in listOf("es", "ru", "ja", "tl", "de", "fr", "zh", "ko", "ar", "pt", "id", "vi"))
            assertEquals(l, LanguagePolicy.Route.RetryAsEnglish, r(l))
    }
}

/** Exact transcripts the phone's Whisper produced (deterministic WAV replay of synthetic Hindi/Hinglish clips). */
class ReplayObservedOutputsTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level
    private fun credLevels(s: String) = engine.evaluate(s).signals.filter { it.tactic == Tactic.CREDENTIAL_REQUEST }.map { it.level }

    @Test fun runTogetherAapkaOtpIsRepaired() = assertEquals(RiskLevel.HIGH, level("KOTP Bataiee, ye bohote urgent hai."))
    @Test fun garbledButRecognisableAccountBlock() =
        assertEquals(RiskLevel.MEDIUM, level("Kakaik Expai Hoga ya hai, Accountage Block Hujayiga"))
    @Test fun anyDiskDownloadIsAnyDesk() {
        assertEquals(RiskLevel.HIGH, level("any disk download ki jee, so that i can help you"))
        assertEquals(RiskLevel.HIGH, level("please download any disk on your phone"))
        assertEquals(RiskLevel.LOW, level("do you have any disk space left"))
    }
    @Test fun urduScriptAnyDeskIsDetected() = assertEquals(RiskLevel.HIGH, level("ایندسک دیوند کجی ایتاکی میں این اپ کی"))
    @Test fun garbledAdvisoryWithATrailingNegationIsStillAnAdvisory() {
        assertEquals(listOf(RiskLevel.LOW), credLevels("Kisi ko Aapna OTP Kabhi Mata Aayu"))
        assertEquals(RiskLevel.LOW, level("خسی کو اپنار OTP کا بھی میت بط"))
        assertEquals(RiskLevel.LOW, level("kisi ko otp nahi dena"))
    }
    @Test fun aRequestStillWinsOverTheLooseAdvisory() {
        assertEquals(RiskLevel.HIGH, level("kisi ko batana mat, mujhe OTP do"))
        assertEquals(RiskLevel.HIGH, level("kisi ko OTP de do na"))
        assertEquals(RiskLevel.HIGH, level("OTP batao, kisi ko mat batana"))
    }
    @Test fun urduScriptNormalizationBasics() {
        assertEquals("anydesk", TextNormalizer.normalize("ایندسک"))
        assertEquals("never share otp", TextNormalizer.normalize("کسی کو اپنا او ٹی پی کبھی مت"))
        assertEquals("", TextNormalizer.normalize("یہ بہت اچھا ہے")) // unknown Urdu is dropped, never glued to Latin
        assertEquals("otp", TextNormalizer.normalize("یہ OTP")) // Arabic-letter variants (ي/ك) fold to the same word
    }
    @Test fun englishAndOtherReplayResultsUnchanged() {
        assertEquals(RiskLevel.HIGH, level("Please tell me your OTP immediately."))
        assertEquals(RiskLevel.MEDIUM, level("Your KYC has expired and your account will be blocked today."))
        assertEquals(RiskLevel.HIGH, level("Install any desk so I can help you."))
        assertEquals(RiskLevel.LOW, level("How was your day? Shall we have dinner this weekend?"))
        assertEquals(RiskLevel.LOW, level("Never share your OTP with anyone."))
        assertEquals(RiskLevel.HIGH, level("Main CBI say Bol Rahun, a digital arrest may hand, Kisi Komat Bhatana."))
        assertEquals(RiskLevel.MEDIUM, level("Sim card will be closed in 2 hours"))
    }
}

class AdvisoryTranslationTest {
    private val src = GemmaSignalSource(clock = { 0L }).also { it.update(GemmaVerdict(RiskLevel.HIGH, "credential request", "Asks for OTP")) }
    private val rules = RiskEngine()
    private val fused = RiskEngine(models = listOf(src))
    private fun seg(native: String, english: String?) = RollingTranscript().also { it.commit(native, english) }

    @Test fun mangledTranslationOfAWarningIsNotAnalysed() {
        val t = seg("Kisi ko Aapna OTP Kabhi Mata Aayu", "Don't tell anyone about your own TV.")
        assertEquals("Kisi ko Aapna OTP Kabhi Mata Aayu", t.analysisText())
        assertEquals("Kisi ko Aapna OTP Kabhi Mata Aayu (Don't tell anyone about your own TV.)", t.text()) // still shown
        assertEquals(RiskLevel.LOW, rules.evaluate(t.analysisText()).level)
        assertEquals(RiskLevel.LOW, fused.evaluate(t.analysisText()).level) // Gemma HIGH is capped by the advisory
    }
    @Test fun warningWhoseTranslationLosesTheNegationStaysLow() {
        val t = seg("خسی کو اپنار OTP کا بھی میت بط", "Someone should tell their OTP sometimes")
        assertEquals(RiskLevel.LOW, fused.evaluate(t.analysisText()).level)
    }
    @Test fun realScamsKeepTheirTranslation() {
        val t = seg("Kisi ko mat batana, OTP bata do", "Don't tell anyone, give me the OTP")
        assertEquals("Kisi ko mat batana, OTP bata do . Don't tell anyone, give me the OTP", t.analysisText())
        assertEquals(RiskLevel.HIGH, rules.evaluate(t.analysisText()).level)
        val u = seg("आपका सिम कार्ड दो घंटे में बंद हो जाएगा", "Your SIM card will be blocked")
        assertEquals(RiskLevel.MEDIUM, rules.evaluate(u.analysisText()).level)
    }
    @Test fun onlyTheAdvisorySegmentIsAffected() {
        val t = RollingTranscript()
        t.commit("Kisi ko apna OTP kabhi mat batana", "Never tell anyone your OTP")
        t.commit("Aapka OTP bataiye", "Tell me your OTP")
        assertEquals("Kisi ko apna OTP kabhi mat batana . Aapka OTP bataiye . Tell me your OTP", t.analysisText())
        assertEquals(RiskLevel.HIGH, rules.evaluate(t.analysisText()).level)
    }
    @Test fun englishAdvisoryBehavesAsBefore() {
        val t = seg("Never share your OTP with anyone.", null)
        assertEquals("Never share your OTP with anyone.", t.analysisText())
        assertEquals(RiskLevel.LOW, fused.evaluate(t.analysisText()).level)
    }
}

class GarbleRepairAndPolicyRefinementTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level

    @Test fun garbledOtpBeforeAHindiVerbIsRepaired() {
        assertEquals(RiskLevel.HIGH, level("tp-bataie, یہ bhaaut zaururi hai."))
        assertEquals("otp bataie", TextNormalizer.normalize("tp bataie").replace(" batana", " bataie"))
    }
    @Test fun garbledKycAndAccountBlockAreRecovered() =
        assertEquals(RiskLevel.MEDIUM, level("ki vaisi krthmhogea hai, aapka khata aj banthhogea ge"))
    @Test fun repairsDoNotTouchOrdinaryEnglish() {
        for (s in listOf("Turn the tp on the shelf", "I ate a ki wi fruit", "The bank statement is on the table", "Katie has a big smile",
            "What is the top speed", "he likes to bathe and then eat"))
            assertEquals(s, RiskLevel.LOW, level(s))
    }
    @Test fun blipsLabelledAsAForeignLanguageAreDropped() {
        assertEquals(LanguagePolicy.Route.Drop, LanguagePolicy.route("ja", audioMs = 450, nativeChars = 6))
        assertEquals(LanguagePolicy.Route.RetryAsEnglish, LanguagePolicy.route("ja", audioMs = 2_500, nativeChars = 40))
        assertEquals(LanguagePolicy.Route.RetryAsEnglish, LanguagePolicy.route("tl", audioMs = 1_000, nativeChars = 40))
    }
    @Test fun veryShortIndianLanguageTextIsNotTranslated() {
        assertEquals(LanguagePolicy.Route.Native, LanguagePolicy.route("hi", audioMs = 3_000, nativeChars = 5))
        assertEquals(LanguagePolicy.Route.Translate("hi"), LanguagePolicy.route("hi", audioMs = 3_000, nativeChars = 8))
        assertEquals(LanguagePolicy.Route.Translate("hi"), LanguagePolicy.route("ur", audioMs = 3_000, nativeChars = 30))
    }
    @Test fun englishIsNeverDroppedOrTranslated() {
        assertEquals(LanguagePolicy.Route.Native, LanguagePolicy.route("en", audioMs = 300, nativeChars = 3))
        assertEquals(LanguagePolicy.Route.Native, LanguagePolicy.route("", audioMs = 300, nativeChars = 3))
    }
}

class HinglishSpellingTest {
    private val engine = RiskEngine()
    private fun level(s: String) = engine.evaluate(s).level
    @Test fun pulisAndWarantAreRecognised() {
        assertEquals(RiskLevel.HIGH, level("Pulis Station سے bhol raham, Aapke nam par warant jari hua hai, turant paise bheji."))
        assertEquals(RiskLevel.HIGH, level("main police station se bol raha hun, apke nam parra rast warant hai"))
        assertEquals(RiskLevel.MEDIUM, level("pulis se bol raha hoon"))
        assertEquals(RiskLevel.LOW, level("the warranty on my phone is over"))
    }
}
