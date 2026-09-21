package com.callguard.gemma

import android.content.Context
import android.os.Process
import android.util.Log
import com.callguard.core.GemmaInference
import com.callguard.core.GemmaScheduler
import com.callguard.core.GemmaSignalSource
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

/**
 * On-device Gemma-3 1B (INT4) scam classifier via the MediaPipe LLM Inference API, CPU backend.
 *
 * The model file is NOT bundled in the APK (it is ~550 MB and gated by the Gemma license). It is
 * looked up in app storage, see [modelDirs]. Loading and inference both run on one low-priority
 * worker thread, so the UI and the audio/ASR threads are never blocked. Nothing here touches the
 * network, and audio/transcripts are never stored; logs carry sizes, latency and the verdict only.
 */
class GemmaClassifier(
    private val context: Context,
    private val source: GemmaSignalSource,
    /** Short human-readable status for the UI ("Gemma: loading…"). Called from the worker thread. */
    private val onStatus: (String) -> Unit,
    /** Called after a new verdict is cached, so the caller can re-evaluate risk. */
    private val onVerdict: () -> Unit,
) {
    private val lock = Object()
    private val scheduler = GemmaScheduler()
    private var worker: Thread? = null
    @Volatile private var stopped = false

    /** Feed the latest recent-transcript window. Cheap; safe from any thread. */
    fun onTranscript(window: String) = synchronized(lock) {
        scheduler.onTranscript(window)
        lock.notifyAll()
    }

    @Volatile private var loaded = false
    @Volatile private var paused = false

    private fun readyStatus() = if (paused) "Gemma: paused (phone is hot)" else "Gemma: ready"

    /** Thermal/battery throttling from [com.callguard.core.ResourcePolicy]; safe from any thread. */
    fun setThrottle(intervalMs: Long, paused: Boolean) {
        this.paused = paused
        synchronized(lock) { scheduler.setThrottle(intervalMs, paused); lock.notifyAll() }
        if (loaded) onStatus(readyStatus())
    }

    fun start() {
        if (worker != null) return
        stopped = false
        worker = Thread({ run() }, "callguard-gemma").also { it.start() }
    }

    /** Non-blocking (safe on the main thread): the worker finishes any running inference, closes the model and exits. */
    fun stop() {
        stopped = true
        synchronized(lock) { scheduler.reset(); lock.notifyAll() }
        worker = null
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val model = findModel()
        if (model == null) {
            Log.w(TAG, "no Gemma model found; keyword rules only. Expected in: ${modelDirs(context).joinToString()}")
            onStatus("Gemma: off (model not found; push ${MODEL_NAME} to ${modelDirs(context).first()})")
            return
        }
        onStatus("Gemma: loading…")
        val t0 = System.nanoTime()
        val llm = try {
            LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(model.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setMaxTopK(1)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build(),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Gemma load failed: ${t.javaClass.simpleName}: ${t.message}")
            onStatus("Gemma: off (load failed: ${t.javaClass.simpleName})")
            return
        }
        Log.i(TAG, "Gemma loaded in ${(System.nanoTime() - t0) / 1_000_000}ms (${model.name}, ${model.length() / 1_000_000}MB, CPU)")
        try {
            // The prompt prefix is computed lazily on first use (~8 s on this phone). Do it now, while
            // idle, so the first real classification is as fast as the rest.
            onStatus("Gemma: warming up…")
            val w0 = System.nanoTime()
            runCatching { generate(llm, com.callguard.core.GemmaPrompt.build("hello there how are you")) }
            Log.i(TAG, "Gemma warm-up done in ${(System.nanoTime() - w0) / 1_000_000}ms")
            loaded = true
            onStatus(readyStatus()) // the thermal throttle may already have been applied while the model was loading
            val inference = GemmaInference { prompt -> generate(llm, prompt) }
            loop(inference)
        } finally {
            runCatching { baseSession?.close() }
            baseSession = null
            runCatching { llm.close() }
        }
    }

    private fun loop(inference: GemmaInference) {
        while (!stopped) {
            val window: String? = synchronized(lock) {
                val w = scheduler.nextWindow(System.currentTimeMillis())
                if (w == null && !stopped) {
                    val wait = scheduler.waitMs(System.currentTimeMillis())
                    lock.wait(if (wait > 0) wait else IDLE_WAIT_MS)
                }
                w
            }
            if (window == null) continue
            val t0 = System.nanoTime()
            val verdict = inference.classify(window)
            val ms = (System.nanoTime() - t0) / 1_000_000
            synchronized(lock) { scheduler.finish(System.currentTimeMillis()) }
            if (stopped) return
            if (verdict == null) {
                Log.w(TAG, "gemma: window_chars=${window.length} latency_ms=$ms result=invalid/none (ignored)")
                continue
            }
            Log.i(TAG, "gemma: window_chars=${window.length} latency_ms=$ms risk=${verdict.risk} tactic='${verdict.tactic}' reason_chars=${verdict.reason.length}")
            source.update(verdict)
            onStatus("Gemma: ${verdict.risk} — ${verdict.tactic}${if (verdict.reason.isNotEmpty()) " (${verdict.reason})" else ""}")
            onVerdict()
        }
    }

    /**
     * The static prompt prefix is prefilled once into [baseSession]; each call clones it (KV cache
     * included) and only adds the recent window, so per-call work is the window plus a short answer.
     * Each call is still stateless: the clone is discarded afterwards.
     */
    private var baseSession: LlmInferenceSession? = null

    private fun sessionOptions() = LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTopK(1).setTopP(0.9f).setTemperature(0.1f).setRandomSeed(0)
        .build()

    private fun base(llm: LlmInference): LlmInferenceSession = baseSession ?: run {
        val t0 = System.nanoTime()
        val s = LlmInferenceSession.createFromOptions(llm, sessionOptions())
        s.addQueryChunk(com.callguard.core.GemmaPrompt.PREFIX)
        Log.i(TAG, "prefix session ready in ${(System.nanoTime() - t0) / 1_000_000}ms")
        baseSession = s
        s
    }

    private fun generate(llm: LlmInference, prompt: String): String? {
        val prefix = com.callguard.core.GemmaPrompt.PREFIX
        require(prompt.startsWith(prefix)) { "prompt does not start with the cached prefix" }
        val session = base(llm).cloneSession()
        return try {
            session.addQueryChunk(prompt.substring(prefix.length))
            session.generateResponse()
        } finally {
            runCatching { session.close() }
        }
    }

    private fun findModel(): File? {
        for (dir in modelDirs(context)) {
            val exact = File(dir, MODEL_NAME)
            if (exact.isFile) return exact
            dir.listFiles { f -> f.isFile && f.name.endsWith(".task") }?.firstOrNull()?.let { return it }
        }
        return null
    }

    companion object {
        const val TAG = "CallGuard.Gemma"
        const val MODEL_NAME = "gemma3-1b-it-int4.task"
        /** Context budget (prompt + answer). The prompt is ~350 tokens plus a <=480-char window. */
        private const val MAX_TOKENS = 1024
        private const val IDLE_WAIT_MS = 2_000L

        /** App-private locations, writable over adb without extra permissions. */
        fun modelDirs(context: Context): List<File> = listOfNotNull(
            context.getExternalFilesDir("models"),
            File(context.filesDir, "models"),
        )
    }
}
