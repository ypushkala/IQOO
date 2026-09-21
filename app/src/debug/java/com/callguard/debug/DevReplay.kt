package com.callguard.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File

/**
 * DEBUG BUILDS ONLY. If the app is debuggable and `Android/data/<pkg>/files/eval/replay.txt` exists,
 * the test-capture button replays the listed WAVs (16 kHz mono PCM16, `eval/wavs/<name>.wav`) into the
 * pipeline in real time instead of opening the microphone, one isolated session per clip, and logs the
 * outcome under the tag "Replay". This makes ASR/Gemma/fusion runs repeatable and independent of room
 * noise. It is inert in release builds and when the file is absent, and it never reads the microphone.
 */
object DevReplay {
    private const val TAG = "Replay"

    fun available(ctx: Context): Boolean =
        (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && file(ctx, "replay.txt")?.isFile == true

    private fun file(ctx: Context, name: String) = ctx.getExternalFilesDir("eval")?.let { File(it, name) }

    /**
     * @param resetSession clears transcript/debounce/model state before each clip
     * @param endSession ends the session after each clip (building the post-call summary) and describes it
     * @param feed pushes one 512-sample frame into the ASR pipeline
     * @param waitReady blocks until the models are ready
     * @param summary one-line description of the current result (risk, signals, transcript)
     */
    fun start(ctx: Context, resetSession: () -> Unit, endSession: () -> String, feed: (ShortArray) -> Unit, waitReady: () -> Unit, summary: () -> String) {
        val names = file(ctx, "replay.txt")!!.readText().trim().split(Regex("\\s+"))
        Thread({
            waitReady()
            for (name in names) {
                resetSession()
                val b = File(ctx.getExternalFilesDir("eval"), "wavs/$name.wav").readBytes()
                val n = (b.size - 44) / 2
                val pcm = ShortArray(n) { i -> ((b[44 + 2 * i].toInt() and 0xff) or (b[45 + 2 * i].toInt() shl 8)).toShort() }
                var pos = 0
                while (pos < n + 16000 * 3) { // audio, then 3 s of silence so the VAD closes the segment
                    feed(ShortArray(512) { i -> if (pos + i < n) pcm[pos + i] else 0 })
                    pos += 512
                    Thread.sleep(32)
                }
                Thread.sleep(11_000) // let Whisper + Gemma settle
                Log.i(TAG, "R|$name|${summary()}")
                Log.i(TAG, "S|$name|${endSession()}")
            }
            Log.i(TAG, "REPLAY done")
        }, "dev-replay").start()
    }
}
