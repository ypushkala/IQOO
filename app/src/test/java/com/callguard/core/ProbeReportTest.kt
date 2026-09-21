package com.callguard.core

import org.junit.Assert.*
import org.junit.Test

class ProbeStatsTest {
    private fun stats(vararg blocks: Short): ProbeStats { val s = ProbeStats(blockSamples = 4); for (b in blocks) s.add(ShortArray(4) { b }, 4); return s }

    @Test fun exactZerosAreSilent() {
        val s = stats(0, 0, 0, 0, 0)
        assertEquals(ProbeVerdict.SILENT, s.verdict()); assertEquals(0, s.peak); assertEquals(0.0, s.rms, 1e-9); assertEquals(100.0, s.silentPercent, 1e-9)
    }
    @Test fun anyRealSoundIsAudio() {
        val s = stats(12, -40, 7, 300, 5)
        assertEquals(ProbeVerdict.AUDIO_PRESENT, s.verdict()); assertEquals(300, s.peak); assertTrue(s.rms > 10)
    }
    @Test fun quietNoiseFloorCountsAsAudioNotSilence() = assertEquals(ProbeVerdict.AUDIO_PRESENT, stats(1, -1, 2, 1, -2, 1).verdict())
    @Test fun halfSilentIsMixed() = assertEquals(ProbeVerdict.MIXED, stats(0, 0, 0, 50, 60, 70).verdict())
    @Test fun noDataAndPartialBlocks() {
        assertEquals(ProbeVerdict.NO_DATA, ProbeStats().verdict())
        val s = ProbeStats(blockSamples = 10); s.add(ShortArray(5) { 9 }, 5); assertEquals(ProbeVerdict.NO_DATA, s.verdict()) // less than one block
    }
    @Test fun accumulatesAcrossCallsAndBlockBoundaries() {
        val s = ProbeStats(blockSamples = 4); s.add(shortArrayOf(0, 0), 2); s.add(shortArrayOf(0, 0, 5, 5), 4); assertEquals(6L, s.samples)
        assertEquals(100.0, s.silentPercent, 1e-9) // only one full block has closed so far (0,0,0,0); the 5,5 tail is still open
        s.add(shortArrayOf(5, 5), 2); assertEquals(50.0, s.silentPercent, 1e-9) // second block (5,5,5,5) closes as non-silent
    }
}

class ProbeReportTest {
    private fun ok(name: String, v: ProbeVerdict, peak: Int = 100) = ProbeResult(name, true, null, 10.0, if (v == ProbeVerdict.SILENT) 0.0 else 40.0, if (v == ProbeVerdict.SILENT) 0 else peak, if (v == ProbeVerdict.SILENT) 100.0 else 0.0, v)
    private val bad = ProbeResult("VOICE_DOWNLINK", false, "system-only", 0.0, 0.0, 0, 100.0, ProbeVerdict.NO_DATA)
    private fun ctx(call: String) = ProbeContext("samsung SM-E176B", "16", if (call == "idle") "NORMAL" else "IN_CALL", call, speakerOn = true, micMuted = false, accessibilityProbeEnabled = false)

    @Test fun baselineWithoutACallSaysToRepeatDuringOne() {
        val t = ProbeReport.text(ctx("idle"), listOf(ok("MIC", ProbeVerdict.AUDIO_PRESENT), bad))
        assertTrue("Baseline (no call)" in t && "MIC" in t && "NOT AVAILABLE (system-only)" in t)
    }
    @Test fun silenceInACallIsTheBlockedVerdict() {
        val t = ProbeReport.text(ctx("offhook"), listOf(ok("MIC", ProbeVerdict.SILENT), ok("VOICE_RECOGNITION", ProbeVerdict.SILENT), bad))
        assertTrue("IN A CALL every source returned silence" in t)
    }
    @Test fun aWorkingRouteInACallIsHighlighted() {
        val t = ProbeReport.text(ctx("offhook"), listOf(ok("MIC", ProbeVerdict.SILENT), ok("UNPROCESSED", ProbeVerdict.AUDIO_PRESENT)))
        assertTrue("IN A CALL, audio was delivered by: UNPROCESSED" in t)
    }
    @Test fun nothingOpenableAndNoPersonalData() {
        val t = ProbeReport.text(ctx("offhook"), listOf(bad)); assertTrue("No source could be opened" in t)
        assertFalse(t.contains("+91")); assertTrue("call state=offhook" in t && "accessibility probe service=off" in t)
    }
}
