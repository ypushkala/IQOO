package com.callguard.asr

import android.content.Context
import android.util.Log
import com.callguard.gemma.GemmaClassifier
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

/**
 * Omnilingual ASR (Meta, 300M CTC, int8, 1,600 languages) via sherpa-onnx. It writes native script
 * (Devanagari, Telugu, Tamil ...), which is far more accurate than Whisper base for Indian languages, and it needs no
 * language hint. The model is NOT bundled in the APK (292 MB): it is looked up in app storage next to the Gemma model:
 * `models/omnilingual/model.int8.onnx` and `tokens.txt`. Without it CallGuard falls back to Whisper-only behaviour.
 */
class IndicAsr private constructor(private val recognizer: OfflineRecognizer) {
    /** Native-script text for one speech segment, or "" on any failure. Called only from the ASR thread. */
    fun transcribe(samples: FloatArray): String = try {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, 16000)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text.trim()
        stream.release()
        text
    } catch (t: Throwable) {
        Log.w(TAG, "indic decode failed: ${t.javaClass.simpleName}")
        ""
    }

    fun release() = runCatching { recognizer.release() }

    companion object {
        const val TAG = "CallGuard.Indic"
        const val DIR_NAME = "omnilingual"
        const val MODEL_FILE = "model.int8.onnx"
        const val TOKENS_FILE = "tokens.txt"

        fun expectedDir(context: Context): File = File(GemmaClassifier.modelDirs(context).first(), DIR_NAME)

        fun find(context: Context): File? = GemmaClassifier.modelDirs(context)
            .map { File(it, DIR_NAME) }
            .firstOrNull { File(it, MODEL_FILE).isFile && File(it, TOKENS_FILE).isFile }

        /** Returns null (and logs why) if the model is missing or cannot be loaded; never throws. */
        fun load(context: Context): IndicAsr? {
            val dir = find(context) ?: run { Log.i(TAG, "no Omnilingual model in ${expectedDir(context)}; Whisper-only for Indian languages"); return null }
            val t0 = System.nanoTime()
            return try {
                val r = OfflineRecognizer(
                    config = OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                        modelConfig = OfflineModelConfig(
                            omnilingual = OfflineOmnilingualAsrCtcModelConfig(model = File(dir, MODEL_FILE).absolutePath),
                            tokens = File(dir, TOKENS_FILE).absolutePath,
                            numThreads = 4,
                        ),
                        decodingMethod = "greedy_search",
                    ),
                )
                Log.i(TAG, "Omnilingual loaded in ${(System.nanoTime() - t0) / 1_000_000}ms (${File(dir, MODEL_FILE).length() / 1_000_000}MB)")
                IndicAsr(r)
            } catch (t: Throwable) {
                Log.e(TAG, "Omnilingual load failed: ${t.javaClass.simpleName}: ${t.message}")
                null
            }
        }
    }
}
