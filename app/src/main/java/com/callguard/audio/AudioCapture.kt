package com.callguard.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs

/**
 * Raw microphone capture: source MIC (deliberately NOT VOICE_COMMUNICATION, whose echo
 * cancellation would strip the speakerphone far-end voice), 16 kHz, mono, PCM16.
 */
class AudioCapture(private val onFrame: (ShortArray, Int) -> Unit) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    /** Returns null on success, or a human-readable failure reason. */
    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO
    @Synchronized
    fun start(): String? {
        if (running) return null
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return "getMinBufferSize failed: $minBuf"
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 2, SAMPLE_RATE / 2 * 2),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return "AudioRecord not initialized (state=${rec.state})"
        }
        Log.i(TAG, "AudioRecord init OK: source=MIC rate=${rec.sampleRate} ch=${rec.channelCount} fmt=${rec.audioFormat} minBuf=$minBuf")
        rec.startRecording()
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            return "startRecording failed (recordingState=${rec.recordingState}); mic may be in use"
        }
        record = rec
        running = true
        thread = Thread({ readLoop(rec) }, "callguard-capture").also { it.start() }
        return null
    }

    private fun readLoop(rec: AudioRecord) {
        val buf = ShortArray(FRAME_SAMPLES)
        var frames = 0L
        var samples = 0L
        var peak = 0
        var sumSq = 0.0
        var lastLog = System.nanoTime()
        while (running) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) {
                Log.w(TAG, "read returned $n")
                if (n < 0) break
                continue
            }
            frames++
            samples += n
            for (i in 0 until n) {
                val v = abs(buf[i].toInt())
                if (v > peak) peak = v
                sumSq += buf[i].toDouble() * buf[i]
            }
            if (frames == 1L) Log.i(TAG, "first PCM frame: $n samples, peak=$peak")
            onFrame(buf, n)
            val now = System.nanoTime()
            if (now - lastLog >= 1_000_000_000L) {
                val rms = if (samples > 0) Math.sqrt(sumSq / samples) else 0.0
                Log.i(TAG, "PCM stats: frames=$frames samples=$samples (~${samples * 1000 / SAMPLE_RATE}ms) rms=${"%.1f".format(rms)} peak=$peak")
                lastLog = now; samples = 0; peak = 0; sumSq = 0.0
            }
        }
        Log.i(TAG, "read loop exit after $frames frames")
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
        Log.i(TAG, "AudioRecord stopped and released")
    }

    companion object {
        const val TAG = "CallGuard.Audio"
        const val SAMPLE_RATE = 16_000
        /** 512 samples = 32 ms, matching the silero-vad window. */
        const val FRAME_SAMPLES = 512
    }
}
