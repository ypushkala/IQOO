package com.callguard.core

/**
 * Lets CallGuard keep listening while its own spoken warning plays, instead of muting (which threw away the
 * caller's words for the whole warning, up to ~9 s in Telugu).
 *
 * The phone's microphone will hear the warning and Whisper/Omnilingual will transcribe it. A segment is dropped only if
 * BOTH (a) it overlaps a time window in which we were speaking and (b) its text closely matches one of the exact
 * warning strings we spoke. Anything else, including the caller talking over the warning, is kept. Comparison uses
 * character trigrams so it works for English, Hindi and Telugu alike, whatever spelling the recogniser chooses.
 *
 * A leaked self-transcription could not raise an alert anyway: a test proves no spoken warning triggers the detector.
 */
class SelfSpeechFilter(
    private val tailMs: Long = 1_500L,
    private val maxWindowMs: Long = 45_000L,
    private val threshold: Double = DEFAULT_THRESHOLD,
) {
    private class Window(val startMs: Long, var endMs: Long?, val references: List<Set<String>>)

    private val windows = ArrayList<Window>()

    /** Call when a warning starts playing. [texts] are the exact strings handed to text-to-speech. */
    @Synchronized fun begin(nowMs: Long, texts: List<String>) {
        windows.removeAll { (it.endMs ?: (it.startMs + maxWindowMs)) + tailMs + KEEP_MS < nowMs }
        windows += Window(nowMs, null, texts.distinct().map { grams(it) }.filter { it.isNotEmpty() })
    }

    /** Call when the warning has finished (or failed). */
    @Synchronized fun end(nowMs: Long) {
        windows.lastOrNull { it.endMs == null }?.endMs = nowMs
    }

    /** True if the segment [segStartMs, segEndMs] with [text] is CallGuard hearing itself. */
    @Synchronized fun shouldDrop(segStartMs: Long, segEndMs: Long, text: String, nowMs: Long): Boolean {
        val g = grams(text)
        if (g.size < MIN_TRIGRAMS) return false // too short to judge: keep it
        for (w in windows) {
            val effectiveEnd = (w.endMs ?: (w.startMs + maxWindowMs)).coerceAtMost(if (w.endMs == null) nowMs.coerceAtLeast(w.startMs) else Long.MAX_VALUE) + tailMs
            if (segEndMs < w.startMs || segStartMs > effectiveEnd) continue
            if (w.references.any { containment(g, it) >= threshold }) return true
        }
        return false
    }

    @Synchronized fun activeWindows(): Int = windows.size

    companion object {
        /**
         * Calibrated on the phone-side ASR models: our own warnings heard back score 0.77-1.00 (one English outlier 0.44),
         * real caller speech in 41 English/Hindi/Telugu clips scores at most 0.43. Erring high only ever keeps a segment.
         */
        const val DEFAULT_THRESHOLD = 0.6
        private const val MIN_TRIGRAMS = 4
        private const val KEEP_MS = 60_000L

        private val nonLetters = Regex("[^\\p{L}\\p{M}\\p{N}]+")

        /** Character trigrams of the text with punctuation removed and spaces collapsed. */
        fun grams(text: String): Set<String> {
            val s = nonLetters.replace(text.lowercase(), " ").trim()
            if (s.length < 3) return emptySet()
            return (0..s.length - 3).mapTo(HashSet()) { s.substring(it, it + 3) }
        }

        /** Fraction of [segment]'s trigrams that also occur in [reference] (0..1). */
        fun containment(segment: Set<String>, reference: Set<String>): Double =
            if (segment.isEmpty()) 0.0 else segment.count { it in reference }.toDouble() / segment.size

        fun similarity(segmentText: String, referenceText: String): Double = containment(grams(segmentText), grams(referenceText))
    }
}
