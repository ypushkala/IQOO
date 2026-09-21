package com.callguard.asr

import android.content.Context
import android.util.Log
import com.callguard.core.IndicScript
import com.callguard.core.LanguagePolicy
import com.callguard.core.SelfSpeechFilter
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * silero-vad cuts the mic stream into speech segments; each segment is transcribed on-device by
 * sherpa-onnx running multilingual Whisper base (int8). English costs one decode. For any other
 * detected language a second decode translates to English, so the English rules and Gemma can
 * still work, while the native text feeds the Hindi/Hinglish rules. Audio is queued from the capture thread and decoded
 * on a dedicated thread so ASR latency never causes AudioRecord overruns.
 */
/** One transcribed speech segment. [english] is set only when the detected [lang] is not English. */
data class AsrSegment(val native: String, val english: String?, val lang: String)

class AsrPipeline(
    context: Context,
    private val onSegment: (AsrSegment) -> Unit,
    /** Recognises our own spoken warning if the microphone hears it (replaces muting the input while we speak). */
    private val selfSpeech: SelfSpeechFilter? = null,
    /** Status of the optional Indic (Omnilingual) model for the UI: "ready", "loading", or why it is off. */
    private val onIndicStatus: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    @Volatile private var indic: IndicAsr? = null
    private val recognizer: OfflineRecognizer
    private val baseConfig: OfflineRecognizerConfig
    /** (language, task) the recognizer is currently set to; "" language = auto-detect. */
    private var decoding = "" to "transcribe"
    private val vad: Vad
    private class Frame(val samples: FloatArray, val atMs: Long)
    private val queue = ArrayBlockingQueue<Frame>(QUEUE_FRAMES)
    /** Capture time of the newest frame the VAD has seen; used to place segments on the same clock as our warnings. */
    private var lastFrameAtMs = 0L
    private var worker: Thread? = null
    @Volatile private var running = false
    /** Set by the thermal policy: skip the second (translate) decode to save heat. Native text is still analysed. */
    @Volatile var skipTranslation = false
    @Volatile var droppedFrames = 0L
        private set

    init {
        val dir = "asr"
        baseConfig = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$dir/base-encoder.int8.onnx",
                        decoder = "$dir/base-decoder.int8.onnx",
                        language = "",
                        task = "transcribe",
                    ),
                    tokens = "$dir/base-tokens.txt",
                    numThreads = 4,
                    modelType = "whisper",
                ),
                decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(context.assets, baseConfig)
        vad = Vad(
            context.assets,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "$dir/silero_vad.onnx",
                    threshold = 0.5f,
                    minSilenceDuration = 0.5f,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = 8.0f,
                ),
                sampleRate = 16000,
                numThreads = 1,
            ),
        )
        Log.i(TAG, "sherpa-onnx multilingual Whisper base + silero VAD loaded")
    }

    fun start() {
        if (running) return
        running = true
        // The Indic model is heavy (292 MB): load it in the background so Whisper is usable immediately.
        Thread({
            if (IndicAsr.find(appContext) == null) { onIndicStatus("Indic ASR: off (push the Omnilingual model to ${IndicAsr.expectedDir(appContext)})"); return@Thread }
            onIndicStatus("Indic ASR: loading…")
            indic = IndicAsr.load(appContext)
            onIndicStatus(if (indic != null) "Indic ASR: ready (Omnilingual — Hindi, Telugu, …)" else "Indic ASR: off (load failed)")
        }, "callguard-indic-load").start()
        worker = Thread({ loop() }, "callguard-asr").also { it.start() }
    }

    /** Called from the capture thread; never blocks. */
    fun feed(pcm: ShortArray, n: Int) {
        if (!running) return
        val f = FloatArray(n) { pcm[it] / 32768f }
        if (!queue.offer(Frame(f, System.currentTimeMillis()))) droppedFrames++
    }

    private fun loop() {
        while (running) {
            val frame = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            lastFrameAtMs = frame.atMs
            vad.acceptWaveform(frame.samples)
            while (!vad.empty()) {
                val samples = vad.front().samples
                vad.pop()
                // The VAD emits a segment about one min-silence after speech ends.
                val endMs = lastFrameAtMs - VAD_TRAILING_SILENCE_MS
                transcribe(samples, startMs = endMs - samples.size * 1000L / 16000, endMs = endMs)
            }
        }
    }

    private fun transcribe(samples: FloatArray, startMs: Long, endMs: Long) {
        val t0 = System.nanoTime()
        var (native, lang) = decode(samples, language = "", task = "transcribe")
        if (native.isEmpty() || isNoiseToken(native)) return
        val detected = lang
        var english: String? = null
        var passes = 1
        val indicModel = if (skipTranslation) null else indic // the thermal policy also gates this extra pass
        when (val route = LanguagePolicy.route(detected, audioMs = samples.size * 1000L / 16000, nativeChars = native.length, indicAvailable = indicModel != null)) {
            LanguagePolicy.Route.Native -> Unit
            LanguagePolicy.Route.Drop -> return
            LanguagePolicy.Route.Indic -> {
                val text = indicModel!!.transcribe(samples)
                if (text.isNotEmpty()) {
                    native = text
                    lang = IndicScript.code(IndicScript.detect(text)) // the script is the language; Latin output = English
                    passes = 2
                }
            }
            is LanguagePolicy.Route.Translate -> if (!skipTranslation) {
                english = decode(samples, language = route.language, task = "translate").first.takeIf { it.isNotEmpty() && !isNoiseToken(it) }
                passes = 2
            }
            LanguagePolicy.Route.RetryAsEnglish -> {
                // Whisper's language ID is unreliable on short/noisy audio; don't translate, just re-read it as English.
                native = decode(samples, language = "en", task = "transcribe").first
                lang = "en"
                passes = 2
                if (native.isEmpty() || isNoiseToken(native)) return
            }
        }
        if (selfSpeech?.shouldDrop(startMs, endMs, native, System.currentTimeMillis()) == true) {
            Log.i(TAG, "segment dropped: it is our own warning heard back (audio_ms=${samples.size * 1000L / 16000})")
            return
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val audioMs = samples.size * 1000L / 16000
        val rtf = if (audioMs > 0) ms.toDouble() / audioMs else 0.0
        // Sizes and timing only: never log the transcript itself.
        Log.i(TAG, "segment: audio_ms=$audioMs total_decode_ms=$ms rtf=${"%.2f".format(rtf)} detected=$detected used=$lang passes=$passes text_chars=${native.length} english_chars=${english?.length ?: 0}")
        onSegment(AsrSegment(native, english, lang))
    }

    private fun decode(samples: FloatArray, language: String, task: String): Pair<String, String> {
        configure(language, task)
        val t0 = System.nanoTime()
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, 16000)
        recognizer.decode(stream)
        val r = recognizer.getResult(stream)
        stream.release()
        Log.d(TAG, "decode task=$task language='$language' ms=${(System.nanoTime() - t0) / 1_000_000}")
        return r.text.trim() to r.lang.trim()
    }

    private fun configure(language: String, task: String) {
        if (decoding == language to task) return
        recognizer.setConfig(
            baseConfig.copy(
                modelConfig = baseConfig.modelConfig.copy(
                    whisper = baseConfig.modelConfig.whisper.copy(language = language, task = task),
                ),
            ),
        )
        decoding = language to task
    }

    /** Whisper emits things like "[BLANK_AUDIO]" or "(music)" on non-speech. */
    private fun isNoiseToken(t: String) = t.startsWith("[") && t.endsWith("]") || t.startsWith("(") && t.endsWith(")")

    fun stop() {
        running = false
        worker?.join(5000)
        worker = null
        queue.clear()
    }

    fun release() {
        stop()
        indic?.release()
        vad.release()
        recognizer.release()
    }

    companion object {
        const val TAG = "CallGuard.Asr"
        private const val QUEUE_FRAMES = 1000 // ~32 s
        private const val VAD_TRAILING_SILENCE_MS = 500L // matches SileroVadModelConfig.minSilenceDuration
    }
}
