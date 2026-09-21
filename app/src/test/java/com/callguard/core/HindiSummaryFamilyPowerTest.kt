package com.callguard.core

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

private fun devanagari(s: String) = s.any { it.code in 0x0900..0x097F }

class HindiSummaryTest {
    private val engine = RiskEngine()
    private val utc = ZoneId.of("UTC")
    private fun scam(): CallSummary {
        val t = CallTimeline { 1_790_000_000_000L }; t.start(0)
        t.record(42_000, engine.evaluate("Please tell me your OTP. Install AnyDesk. Don't tell anyone"), 2)
        val a = NumberReputation().assess(CallerContext("+14155550123", inContacts = false, firstTime = true))
        return t.finish(192_000, a)!!
    }

    @Test fun hindiSummaryIsFullyInHindi() {
        val txt = scam().plainText(utc, Lang.HI)
        for (part in listOf("बहुत ख़तरा", "21 सितंबर 2026", "अवधि 3:12", "कॉलर: +14155550123", "इस कॉल में ये तरीके अपनाए गए:", "ओटीपी/गोपनीय कोड माँगना",
            "अधिक ख़तरा", "0:42 पर", "रिमोट-एक्सेस ऐप", "सुना गया", "CallGuard ने आपको 2 बार चेताया।", "क्या करें:", "1930"))
            assertTrue("missing: $part\n$txt", part in txt)
        for (english in listOf("What to do:", "This call used these tactics", "Call at", "lasted", "warned you", "High risk"))
            assertFalse("English left over: $english\n$txt", english in txt)
    }
    @Test fun englishSummaryIsUnchanged() {
        val txt = scam().plainText(utc)
        assertTrue("High risk: this call looked like a scam" in txt && "Call at 21 Sep 2026" in txt && "What to do:" in txt && "CallGuard warned you 2 times." in txt)
        assertFalse(devanagari(txt))
        assertEquals(txt, scam().plainText(utc, Lang.EN))
    }
    @Test fun everyTacticAndLevelHasAHindiName() {
        for (t in Tactic.values()) assertTrue(t.name, devanagari(Strings.tactic(t, Lang.HI)) && Strings.tactic(t, Lang.EN) == t.label)
        for (l in RiskLevel.values()) assertTrue(devanagari(Strings.headline(l, Lang.HI)) && devanagari(Strings.levelWord(l, Lang.HI)))
        assertEquals(12, Strings.months(Lang.HI).size); assertEquals(12, Strings.months(Lang.EN).size)
    }
    @Test fun hindiAdviceCoversEveryTacticAndAlwaysEndsWithTheHelpline() {
        for (t in Tactic.values()) {
            val a = Strings.adviceFor(setOf(t), RiskLevel.HIGH, Lang.HI)
            assertTrue(t.name, a.isNotEmpty() && a.last().contains("1930") && a.all { devanagari(it) })
            assertEquals(Strings.adviceFor(setOf(t), RiskLevel.HIGH, Lang.EN).size, a.size) // same structure in both languages
        }
        assertTrue(devanagari(Strings.adviceFor(emptySet(), RiskLevel.LOW, Lang.HI).single()))
    }
    @Test fun hindiSummaryOfAHarmlessCallAndOfMinorNotes() {
        val t = CallTimeline { 1_790_000_000_000L }; t.start(0); t.record(1_000, engine.evaluate("How was your day?"), 0)
        val s = t.finish(9_000, null)!!
        assertTrue(s.plainText(utc, Lang.HI).startsWith("कुछ भी संदिग्ध नहीं मिला"))
        val m = CallTimeline { 1L }; m.start(0); m.record(1, engine.evaluate("Act now, this is urgent. You won a lottery prize"), 0)
        assertTrue(m.finish(5, null)!!.plainText(utc, Lang.HI).contains("यह भी सुना गया: "))
    }
    @Test fun theModelsEnglishReasonIsNotMixedIntoTheHindiSummary() {
        val g = GemmaSignalSource(clock = { 0L }).also { it.update(GemmaVerdict(RiskLevel.HIGH, "credential request", "Asks for OTP")) }
        val t = CallTimeline { 1L }; t.start(0); t.record(4_000, RiskEngine(models = listOf(g)).evaluate("Please tell me your OTP"), 1)
        val s = t.finish(9_000, null)!!
        assertTrue(s.plainText(utc, Lang.EN).contains("Asks for OTP")); assertFalse(s.plainText(utc, Lang.HI).contains("Asks for OTP"))
    }
    @Test fun reportForAuthoritiesStaysEnglish() = assertFalse(devanagari(scam().reportText(utc)))
}

class HindiWarningTest {
    private val engine = RiskEngine()
    @Test fun hindiWarningsAreHindiAndCannotTriggerTheDetector() {
        for (l in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH)) for (acc in listOf(false, true)) {
            val p = WarningPlans.plan(l, acc, Lang.HI)
            assertEquals(Lang.HI, p.lang); assertTrue(p.messages.all { devanagari(it) })
            for (m in p.messages) {
                val r = engine.evaluate(m)
                assertEquals(m, RiskLevel.LOW, r.level); assertTrue(m, r.signals.none { it.level >= RiskLevel.MEDIUM })
            }
        }
        for (m in WarningPlans.allMessages) assertEquals(m, RiskLevel.LOW, engine.evaluate(m).level)
    }
    @Test fun hindiPlansKeepThePacingAndLoudnessOfTheirEnglishTwins() {
        for (l in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH)) for (acc in listOf(false, true)) {
            val en = WarningPlans.plan(l, acc, Lang.EN); val hi = WarningPlans.plan(l, acc, Lang.HI)
            assertEquals(en.speechRate, hi.speechRate); assertEquals(en.boostVolume, hi.boostVolume); assertArrayEquals(en.vibration, hi.vibration)
            assertEquals(en.messages.size, hi.messages.size)
        }
        assertEquals(Lang.EN, WarningPlans.plan(RiskLevel.HIGH, false).lang) // default unchanged
    }
}

class CallLanguageTrackerTest {
    @Test fun englishCallsStayEnglish() {
        val t = CallLanguageTracker()
        for (s in listOf("Please tell me your OTP", "How was your day?", "Your KYC has expired and the main account is blocked")) t.onSegment(s, "en")
        assertFalse(t.callLooksHindi()); assertEquals(Lang.EN, t.resolve(LanguageSetting.AUTO))
    }
    @Test fun devanagariUrduAndDetectedHindiCount() {
        for ((text, lang) in listOf("आपका ओटीपी बताइए" to "hi", "آپ کا او ٹی پی" to "ur", "anything" to "hi")) {
            val t = CallLanguageTracker(); t.onSegment(text, lang); assertTrue("$text/$lang", t.callLooksHindi())
        }
    }
    @Test fun hinglishInLatinLettersCountsEvenIfWhisperSaidEnglish() {
        for (s in listOf("Aapka OTP bataiye", "kisi ko mat batana", "account block ho jayega", "Main CBI se bol raha hoon", "yeh kya hai"))
            assertTrue(s, CallLanguageTracker.isHindiLike(s, "en"))
    }
    @Test fun ordinaryEnglishIsNeverHindiLike() {
        for (s in listOf("How was your day?", "Main street is closed", "The doctor will see you now", "Install AnyDesk", "I will call you tomorrow"))
            assertFalse(s, CallLanguageTracker.isHindiLike(s, "en"))
    }
    @Test fun onlyTheMostRecentSegmentsCount() {
        val t = CallLanguageTracker(window = 3)
        t.onSegment("आपका ओटीपी बताइए", "hi"); assertTrue(t.callLooksHindi())
        repeat(3) { t.onSegment("Please tell me your OTP", "en") }
        assertFalse(t.callLooksHindi())
    }
    @Test fun theSettingOverridesTheCall() {
        val t = CallLanguageTracker(); t.onSegment("आपका ओटीपी बताइए", "hi")
        assertEquals(Lang.EN, t.resolve(LanguageSetting.ENGLISH)); assertEquals(Lang.HI, t.resolve(LanguageSetting.HINDI)); assertEquals(Lang.HI, t.resolve(LanguageSetting.AUTO))
        val e = CallLanguageTracker(); e.onSegment("hello", "en"); assertEquals(Lang.HI, e.resolve(LanguageSetting.HINDI)); assertEquals(Lang.EN, e.resolve(LanguageSetting.AUTO))
    }
    @Test fun resetForgetsTheLastCall() {
        val t = CallLanguageTracker(); t.onSegment("आपका", "hi"); t.reset(); assertFalse(t.callLooksHindi())
    }
}

class FamilyAlertTest {
    @Test fun englishMessage() {
        val m = FamilyAlert.message(RiskLevel.HIGH, listOf(Tactic.CREDENTIAL_REQUEST, Tactic.REMOTE_ACCESS), "+14155550123 · international number", Lang.EN)
        for (part in listOf("CallGuard alert", "high risk", "OTP/credential request", "remote-access app", "Caller: +14155550123", "Please call me", "No audio or call text is shared"))
            assertTrue("missing: $part\n$m", part in m)
        assertTrue(m.length < 320)
    }
    @Test fun hindiMessage() {
        val m = FamilyAlert.message(RiskLevel.MEDIUM, listOf(Tactic.ACCOUNT_THREAT), "+91 98765 43210", Lang.HI)
        assertTrue(devanagari(m)); for (part in listOf("CallGuard चेतावनी", "संदिग्ध", "केवाईसी/खाता/सिम", "कॉलर: +91 98765 43210", "कृपया मुझे फ़ोन करें")) assertTrue("missing: $part\n$m", part in m)
    }
    @Test fun optionalPartsAreLeftOutCleanly() {
        val m = FamilyAlert.message(RiskLevel.LOW, emptyList(), null, Lang.EN)
        assertFalse("Caller" in m); assertTrue("(low risk)." in m)
        assertFalse("Caller" in FamilyAlert.message(RiskLevel.HIGH, listOf(Tactic.SECRECY), "  ", Lang.EN))
    }
    @Test fun onlyTheFirstThreeTacticsAndNeverAnyTranscript() {
        val m = FamilyAlert.message(RiskLevel.HIGH, Tactic.values().toList(), null, Lang.EN)
        assertEquals(3, m.split(",").size); assertFalse(m.contains("“"))
    }
}

class ResourcePolicyTest {
    private fun d(s: PowerSnapshot) = ResourcePolicy.decide(s)

    @Test fun aCoolPhoneRunsEverythingAtFullRate() {
        val t = d(PowerSnapshot(thermalStatus = 0, batteryTempC = 33f, batteryPercent = 60, charging = false))
        assertEquals(PowerBand.NORMAL, t.band); assertEquals(ResourcePolicy.NORMAL_INTERVAL_MS, t.gemmaIntervalMs); assertFalse(t.gemmaPaused); assertFalse(t.skipTranslation)
        assertEquals("Power: normal", t.label)
    }
    @Test fun unknownReadingsAreTreatedAsNormal() = assertEquals(PowerBand.NORMAL, d(PowerSnapshot(thermalStatus = -1)).band)
    @Test fun moderateThermalStatusSlowsGemmaButKeepsEverythingElse() {
        val t = d(PowerSnapshot(thermalStatus = 2))
        assertEquals(PowerBand.WARM, t.band); assertEquals(ResourcePolicy.WARM_INTERVAL_MS, t.gemmaIntervalMs); assertFalse(t.gemmaPaused); assertFalse(t.skipTranslation)
        assertTrue(t.label.contains("Gemma slowed"))
    }
    @Test fun severeThermalStatusPausesGemmaButKeepsTranslation() {
        val t = d(PowerSnapshot(thermalStatus = 3))
        assertEquals(PowerBand.HOT, t.band); assertTrue(t.gemmaPaused); assertFalse(t.skipTranslation)
        assertTrue(t.label.contains("Gemma paused") && !t.label.contains("extra language pass off"))
    }
    @Test fun criticalThermalStatusAlsoSkipsTranslation() {
        for (s in 4..6) { val t = d(PowerSnapshot(thermalStatus = s)); assertEquals(PowerBand.CRITICAL, t.band); assertTrue(t.gemmaPaused); assertTrue(t.skipTranslation) }
        assertTrue(d(PowerSnapshot(thermalStatus = 4)).label.contains("extra language pass off"))
    }
    @Test fun batteryTemperatureThresholds() {
        assertEquals(PowerBand.NORMAL, d(PowerSnapshot(batteryTempC = 39.9f)).band)
        assertEquals(PowerBand.WARM, d(PowerSnapshot(batteryTempC = 40f)).band)
        assertEquals(PowerBand.WARM, d(PowerSnapshot(batteryTempC = 42.9f)).band)
        assertEquals(PowerBand.HOT, d(PowerSnapshot(batteryTempC = 43f)).band)
        assertEquals(PowerBand.HOT, d(PowerSnapshot(batteryTempC = 45.9f)).band)
        assertEquals(PowerBand.CRITICAL, d(PowerSnapshot(batteryTempC = 46f)).band)
    }
    @Test fun lowBatteryOnlyMattersWhenNotCharging() {
        assertEquals(PowerBand.WARM, d(PowerSnapshot(batteryPercent = 15, charging = false)).band)
        assertEquals(PowerBand.HOT, d(PowerSnapshot(batteryPercent = 7, charging = false)).band)
        assertEquals(PowerBand.NORMAL, d(PowerSnapshot(batteryPercent = 5, charging = true)).band)
        assertEquals(PowerBand.NORMAL, d(PowerSnapshot(batteryPercent = 5, charging = null)).band)
        assertEquals(PowerBand.NORMAL, d(PowerSnapshot(batteryPercent = 16, charging = false)).band)
    }
    @Test fun batterySaverSlowsGemma() = assertEquals(PowerBand.WARM, d(PowerSnapshot(powerSave = true)).band)
    @Test fun theWorstFactorWinsAndIsNamed() {
        val t = d(PowerSnapshot(thermalStatus = 3, batteryTempC = 41f, powerSave = true))
        assertEquals(PowerBand.HOT, t.band); assertTrue(t.reason.contains("severe"))
        assertEquals(PowerBand.HOT, d(PowerSnapshot(thermalStatus = 0, batteryTempC = 44.5f, powerSave = true)).band)
        assertEquals(PowerBand.CRITICAL, d(PowerSnapshot(thermalStatus = 4, batteryTempC = 41f)).band)
    }
    @Test fun coreProtectionIsNeverSwitchedOffByThePolicy() {
        // The policy only has knobs for Gemma and translation. There is no way to stop Whisper or the rules from here.
        for (s in listOf(PowerSnapshot(), PowerSnapshot(thermalStatus = 6, batteryTempC = 60f, batteryPercent = 1, charging = false, powerSave = true)))
            assertTrue(d(s).gemmaIntervalMs > 0)
    }
}

class GemmaThrottleTest {
    @Test fun pausedGemmaKeepsTheNewestWindowAndResumes() {
        val s = GemmaScheduler(minIntervalMs = 3_500, minGapAfterFinishMs = 1_000, minWords = 3)
        s.setThrottle(9_000, paused = true)
        s.onTranscript("one two three four")
        assertNull(s.nextWindow(60_000)); assertTrue(s.hasPending())
        s.onTranscript("one two three four five")
        s.setThrottle(3_500, paused = false)
        assertEquals("one two three four five", s.nextWindow(60_000))
    }
    @Test fun aLongerIntervalIsRespected() {
        val s = GemmaScheduler(minIntervalMs = 3_500, minGapAfterFinishMs = 1_000, minWords = 3)
        s.setThrottle(9_000, paused = false)
        s.onTranscript("one two three"); assertNotNull(s.nextWindow(0)); s.finish(1_000)
        s.onTranscript("one two three four")
        assertNull(s.nextWindow(8_999)); assertNotNull(s.nextWindow(9_000))
    }
}
