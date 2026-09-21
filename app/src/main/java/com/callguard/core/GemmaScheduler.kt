package com.callguard.core

/**
 * Decides when Gemma may run. It runs only when the transcript window has changed since the last
 * run, at most once per [minIntervalMs] (start to start), never concurrently, and never sooner
 * than [minGapAfterFinishMs] after the previous run ended (so a slow inference cannot run
 * back-to-back and starve Whisper). Windows with too few words are skipped.
 * Thread-safety: callers synchronize externally.
 */
class GemmaScheduler(
    minIntervalMs: Long = 3_500L,
    private val minGapAfterFinishMs: Long = 1_000L,
    private val minWords: Int = 3,
) {
    private var minIntervalMs = minIntervalMs
    private var paused = false
    private var pending: String? = null
    private var lastClassified: String? = null
    private var running = false
    private var lastStartMs = Long.MIN_VALUE / 2
    private var lastFinishMs = Long.MIN_VALUE / 2

    /** Call whenever the transcript changes. Cheap; keeps only the newest window. */
    fun onTranscript(rawWindow: String) {
        val w = GemmaPrompt.sanitize(rawWindow)
        pending = if (w.split(' ').count { it.isNotEmpty() } >= minWords && w != lastClassified) w else null
    }

    /** Thermal/battery throttling: a longer interval, or paused entirely (the newest window is kept for when it resumes). */
    fun setThrottle(intervalMs: Long, paused: Boolean) { minIntervalMs = intervalMs; this.paused = paused }

    /** Returns the window to classify now, or null. Marks the run as started. */
    fun nextWindow(nowMs: Long): String? {
        val w = pending ?: return null
        if (paused || running || waitMs(nowMs) > 0) return null
        running = true
        lastStartMs = nowMs
        lastClassified = w
        pending = null
        return w
    }

    fun finish(nowMs: Long) { running = false; lastFinishMs = nowMs }

    /** Milliseconds until a pending window may run (0 = ready or nothing pending). */
    fun waitMs(nowMs: Long): Long {
        if (pending == null) return 0
        return maxOf(0, lastStartMs + minIntervalMs - nowMs, lastFinishMs + minGapAfterFinishMs - nowMs)
    }

    fun hasPending() = pending != null
    fun reset() { pending = null; lastClassified = null }
}

/** Prompt + model call + strict parsing, with every failure mode collapsed to null. */
class GemmaInference(private val generate: (prompt: String) -> String?) {
    fun classify(window: String): GemmaVerdict? =
        try { GemmaOutputParser.parse(generate(GemmaPrompt.build(window))) } catch (t: Throwable) { null }
}
