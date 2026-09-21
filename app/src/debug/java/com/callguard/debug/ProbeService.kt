package com.callguard.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import com.callguard.core.ProbeResult
import com.callguard.core.ProbeStats

/** Results of the latest probe run, in memory only. */
object ProbeState {
    @Volatile var running = false
    @Volatile var status = "Idle"
    val results = ArrayList<ProbeResult>()
    @Volatile var listener: (() -> Unit)? = null
}

/**
 * DEBUG ONLY. Records each selected audio source for a few seconds and reports whether real sound arrived, so one build
 * can test many phones during a real call. Runs as a microphone foreground service so it keeps recording while the user
 * switches to the call screen. Nothing is stored or sent: only RMS/peak/silence statistics are kept.
 */
class ProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sources = intent?.getStringArrayExtra(EXTRA_SOURCES) ?: return START_NOT_STICKY
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 10)
        val delay = intent.getIntExtra(EXTRA_DELAY, 0)
        if (ProbeState.running) return START_NOT_STICKY
        startForeground()
        ProbeState.running = true; ProbeState.results.clear()
        Thread({ run(sources, seconds, delay) }, "probe").start()
        return START_NOT_STICKY
    }

    private fun startForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("probe", "Capture probe", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "probe").setContentTitle("CallGuard capture probe running").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(9, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    private fun run(sources: Array<String>, seconds: Int, delay: Int) {
        try {
            for (i in delay downTo 1) { update("Starting in $i s: switch to the call screen and put it on speaker"); Thread.sleep(1000) }
            for (name in sources) {
                update("Recording $name for $seconds s")
                ProbeState.results += record(name, seconds)
                notifyUi()
            }
            update("Done")
        } catch (t: Throwable) {
            Log.e(TAG, "probe failed: ${t.javaClass.simpleName}")
            update("Failed: ${t.javaClass.simpleName}")
        } finally {
            ProbeState.running = false; notifyUi()
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }

    @Suppress("MissingPermission") // RECORD_AUDIO is granted before the probe can start
    private fun record(name: String, seconds: Int): ProbeResult {
        val source = SOURCES[name] ?: return ProbeResult(name, false, "unknown source", 0.0, 0.0, 0, 100.0, com.callguard.core.ProbeVerdict.NO_DATA)
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 16000))
        } catch (t: Throwable) {
            return ProbeResult(name, false, t.javaClass.simpleName, 0.0, 0.0, 0, 100.0, com.callguard.core.ProbeVerdict.NO_DATA)
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return ProbeResult(name, false, "not initialized", 0.0, 0.0, 0, 100.0, com.callguard.core.ProbeVerdict.NO_DATA) }
        val stats = ProbeStats()
        val buf = ShortArray(1600)
        val endAt = System.nanoTime() + seconds * 1_000_000_000L
        try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) return ProbeResult(name, false, "could not start recording", 0.0, 0.0, 0, 100.0, com.callguard.core.ProbeVerdict.NO_DATA)
            while (System.nanoTime() < endAt) { val n = rec.read(buf, 0, buf.size); if (n > 0) stats.add(buf, n) else if (n < 0) break }
        } finally { runCatching { rec.stop() }; rec.release() }
        val r = ProbeResult(name, true, null, stats.samples / 16000.0, stats.rms, stats.peak, stats.silentPercent, stats.verdict())
        Log.i(TAG, com.callguard.core.ProbeReport.line(r))
        return r
    }

    private fun update(s: String) { ProbeState.status = s; notifyUi() }
    private fun notifyUi() { ProbeState.listener?.invoke() }

    companion object {
        const val TAG = "CallGuard.Probe"
        const val EXTRA_SOURCES = "sources"; const val EXTRA_SECONDS = "seconds"; const val EXTRA_DELAY = "delay"
        /** VOICE_COMMUNICATION is deliberately absent. VOICE_DOWNLINK/VOICE_CALL are system-only and expected to fail: they document the OEM/system route. */
        val SOURCES: Map<String, Int> = linkedMapOf(
            "MIC" to MediaRecorder.AudioSource.MIC,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED,
            "CAMCORDER" to MediaRecorder.AudioSource.CAMCORDER,
            "VOICE_DOWNLINK (system-only)" to MediaRecorder.AudioSource.VOICE_DOWNLINK,
            "VOICE_CALL (system-only)" to MediaRecorder.AudioSource.VOICE_CALL,
        )
    }
}
