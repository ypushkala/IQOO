package com.callguard.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.callguard.core.Lang
import com.callguard.core.RiskLevel
import com.callguard.core.SelfSpeechFilter
import com.callguard.core.WarningPlans
import java.util.Locale

/**
 * Haptic + spoken warning, delivered according to [WarningPlans]. In accessibility mode the warning is
 * slower, repeated, buzzes longer and is played on the alarm stream at full volume (restored afterwards).
 */
class Alerter(context: Context, private val selfSpeech: SelfSpeechFilter? = null) {
    private val appContext = context.applicationContext
    private val prefs = AppPrefs(appContext)
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION") appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    @Volatile private var ttsReady = false
    @Volatile var speaking = false
        private set
    private var savedAlarmVolume = -1
    private val handler = Handler(Looper.getMainLooper())
    private val restoreRunnable = Runnable { restoreVolume() }
    private var lastUtteranceId = ""
    private var speakStartedAtMs = 0L
    private var speakInfo = ""
    private var counter = 0
    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        Log.i(TAG, "TTS init status=$status")
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { speaking = true }
            override fun onDone(id: String?) { if (id == lastUtteranceId) finished() }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { if (id == lastUtteranceId) finished() }
        })
    }

    private fun finished() {
        speaking = false
        val now = System.currentTimeMillis()
        selfSpeech?.end(now)
        Log.i(TAG, "warning finished: $speakInfo spoken_ms=${now - speakStartedAtMs}") // how long the caller is masked
        restoreVolume()
    }

    /** True if the phone has a text-to-speech voice for [lang] installed. */
    fun voiceAvailable(lang: Lang): Boolean = lang == Lang.EN || (ttsReady && tts.isLanguageAvailable(localeOf(lang)) >= TextToSpeech.LANG_AVAILABLE)
    fun hindiVoiceAvailable(): Boolean = voiceAvailable(Lang.HI)

    /** [lang] is what the user (or the call) wants; without a Hindi voice the warning falls back to English. */
    fun warn(level: RiskLevel, lang: Lang = Lang.EN) {
        val accessible = prefs.accessibility
        val spoken = if (!voiceAvailable(lang)) {
            Log.w(TAG, "no ${lang.tag} voice installed; speaking the warning in English"); Lang.EN
        } else lang
        val plan = WarningPlans.plan(level, accessible, spoken)
        vibrator.vibrate(VibrationEffect.createWaveform(plan.vibration, -1))
        if (!ttsReady) { Log.w(TAG, "TTS not ready; spoken warning skipped"); return }
        tts.language = localeOf(spoken)
        tts.setSpeechRate(plan.speechRate)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (plan.boostVolume) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        if (plan.boostVolume) boostVolume()
        // Not muting: tell the filter what we are about to say, so only our own words are dropped if the mic hears them.
        selfSpeech?.begin(System.currentTimeMillis(), plan.messages)
        speakStartedAtMs = System.currentTimeMillis(); speakInfo = "lang=${spoken.tag} level=$level accessible=$accessible"
        plan.messages.forEachIndexed { i, m ->
            val id = "cg-alert-${++counter}"
            lastUtteranceId = id
            tts.speak(m, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        Log.i(TAG, "ALERT level=$level lang=${spoken.tag} accessible=$accessible messages=${plan.messages.size} rate=${plan.speechRate} loud=${plan.boostVolume}")
    }

    private fun boostVolume() {
        if (savedAlarmVolume >= 0) return // already boosted by an earlier warning; keep the original to restore
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }
        handler.postDelayed(restoreRunnable, MAX_BOOST_MS) // safety net: never leave the alarm volume at maximum
        Log.i(TAG, "alarm stream volume ${savedAlarmVolume}/$max -> $max for the warning")
    }

    private fun restoreVolume() {
        handler.removeCallbacks(restoreRunnable)
        if (savedAlarmVolume < 0) return
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0) }
        Log.i(TAG, "alarm stream volume restored to $savedAlarmVolume")
        savedAlarmVolume = -1
    }

    fun release() {
        tts.stop(); tts.shutdown(); vibrator.cancel(); speaking = false; restoreVolume()
    }

    companion object {
        const val TAG = "CallGuard.Alert"
        private const val MAX_BOOST_MS = 45_000L
        val HINDI: Locale = Locale("hi", "IN")
        val TELUGU: Locale = Locale("te", "IN")
        fun localeOf(lang: Lang): Locale = when (lang) { Lang.EN -> Locale.ENGLISH; Lang.HI -> HINDI; Lang.TE -> TELUGU }
    }
}
