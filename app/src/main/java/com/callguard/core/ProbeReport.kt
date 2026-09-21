package com.callguard.core

enum class ProbeVerdict { AUDIO_PRESENT, SILENT, MIXED, NO_DATA }

/** Measures whether an audio source delivers real sound: digital silence (exact zeros) is what Android returns when capture is blocked. */
class ProbeStats(private val blockSamples: Int = 1_600 /* 100 ms at 16 kHz */) {
    var samples = 0L; private set
    var peak = 0; private set
    private var sumSq = 0.0
    private var blocks = 0
    private var silentBlocks = 0
    private var blockPeak = 0
    private var inBlock = 0

    fun add(buf: ShortArray, n: Int) {
        for (i in 0 until n) {
            val v = kotlin.math.abs(buf[i].toInt())
            if (v > peak) peak = v
            if (v > blockPeak) blockPeak = v
            sumSq += buf[i].toDouble() * buf[i]
            if (++inBlock == blockSamples) closeBlock()
        }
        samples += n
    }

    private fun closeBlock() { blocks++; if (blockPeak == 0) silentBlocks++; blockPeak = 0; inBlock = 0 }

    val rms: Double get() = if (samples == 0L) 0.0 else Math.sqrt(sumSq / samples)
    val silentPercent: Double get() = if (blocks == 0) 100.0 else silentBlocks * 100.0 / blocks

    fun verdict(): ProbeVerdict = when {
        samples == 0L || blocks == 0 -> ProbeVerdict.NO_DATA
        silentPercent >= 95.0 -> ProbeVerdict.SILENT
        silentPercent <= 5.0 -> ProbeVerdict.AUDIO_PRESENT
        else -> ProbeVerdict.MIXED
    }
}

data class ProbeResult(
    val source: String,
    val initialized: Boolean,
    val error: String?,
    val seconds: Double,
    val rms: Double,
    val peak: Int,
    val silentPercent: Double,
    val verdict: ProbeVerdict,
)

/** Facts about the moment of the test that decide how to read it (was there a call? speaker on?). */
data class ProbeContext(
    val device: String,
    val android: String,
    val audioMode: String,
    val callState: String,
    val speakerOn: Boolean,
    val micMuted: Boolean,
    val accessibilityProbeEnabled: Boolean,
    val note: String = "",
)

object ProbeReport {
    fun line(r: ProbeResult): String =
        if (!r.initialized) "${r.source}: NOT AVAILABLE (${r.error ?: "init failed"})"
        else "${r.source}: ${r.verdict}  rms=${"%.1f".format(r.rms)} peak=${r.peak} silent=${"%.0f".format(r.silentPercent)}% over ${"%.1f".format(r.seconds)}s"

    /** Plain text meant to be shared/pasted: no audio, no transcript, no phone number. */
    fun text(ctx: ProbeContext, results: List<ProbeResult>): String = buildString {
        appendLine("CallGuard capture probe")
        appendLine("Device: ${ctx.device} · Android ${ctx.android}")
        appendLine("During the test: call state=${ctx.callState}, audio mode=${ctx.audioMode}, speaker=${if (ctx.speakerOn) "on" else "off"}, mic muted=${ctx.micMuted}, accessibility probe service=${if (ctx.accessibilityProbeEnabled) "enabled" else "off"}")
        if (ctx.note.isNotBlank()) appendLine("Note: ${ctx.note}")
        appendLine()
        results.forEach { appendLine(line(it)) }
        appendLine()
        appendLine(conclusion(ctx, results))
    }.trim()

    fun conclusion(ctx: ProbeContext, results: List<ProbeResult>): String {
        val inCall = ctx.callState != "idle"
        val working = results.filter { it.initialized && it.verdict == ProbeVerdict.AUDIO_PRESENT }.map { it.source }
        return when {
            results.none { it.initialized } -> "No source could be opened (permission or hardware problem)."
            !inCall && working.isNotEmpty() -> "Baseline (no call): microphone works via ${working.joinToString()}. Repeat this during a real call with the speaker on."
            !inCall -> "Baseline (no call) shows no audio: check the microphone permission and that nothing else is using the mic."
            working.isNotEmpty() -> "IN A CALL, audio was delivered by: ${working.joinToString()}. This route can work on this phone."
            else -> "IN A CALL every source returned silence (or was unavailable): the phone blocks third-party capture during calls."
        }
    }
}
