package com.callguard.core

/**
 * What to do with a segment given the language Whisper detected. Whisper's language ID is
 * unreliable on short clips and noise (it will "detect" Spanish or Japanese in a quiet room), so
 * only English and Indian languages are trusted; anything else is re-decoded as English instead of
 * being translated into junk text. Very short clips are not worth a second decode at all.
 */
object LanguagePolicy {
    sealed interface Route {
        /** English (or too little text to translate): use the transcript as is. */
        data object Native : Route
        /** A supported Indian language: also translate to English, decoding as [language]. */
        data class Translate(val language: String) : Route
        /** Not a language we support: treat the audio as English. */
        data object RetryAsEnglish : Route
        /** A blip of audio labelled as an unsupported language: noise, discard without decoding again. */
        data object Drop : Route
        /**
         * The Indic model (Omnilingual, native-script output) re-reads the segment. Whisper's language label is only
         * a hint that the audio is not English: its Telugu/Hindi transcripts are poor and its labels are often wrong.
         */
        data object Indic : Route
    }

    /** Hindi/Hinglish plus the regional languages Whisper base can handle. "ur" is included because it is how Whisper often labels Hindi. */
    val supportedIndian = setOf("hi", "ur", "bn", "ta", "te", "mr", "gu", "kn", "ml", "pa", "ne", "as", "or", "sa", "sd")

    /** Shorter than this and labelled foreign: almost certainly a cough, click or background noise. */
    const val MIN_RETRY_AUDIO_MS = 1_000L
    /** Fewer characters than this is not worth a translation pass. */
    const val MIN_TRANSLATE_CHARS = 8

    fun route(detected: String, audioMs: Long = Long.MAX_VALUE, nativeChars: Int = Int.MAX_VALUE, indicAvailable: Boolean = false): Route = when {
        detected.isEmpty() || detected == "en" -> Route.Native
        // With the Indic model, anything Whisper calls non-English is re-read by it. Short blips labelled with a
        // language we do not support are still noise.
        indicAvailable -> if (detected in supportedIndian || audioMs >= MIN_RETRY_AUDIO_MS) Route.Indic else Route.Drop
        detected in supportedIndian ->
            if (nativeChars < MIN_TRANSLATE_CHARS) Route.Native else Route.Translate(if (detected == "ur") "hi" else detected)
        audioMs < MIN_RETRY_AUDIO_MS -> Route.Drop
        else -> Route.RetryAsEnglish
    }
}
