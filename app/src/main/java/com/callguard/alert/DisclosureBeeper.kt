package com.callguard.alert

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.callguard.core.DisclosurePolicy

/** Plays the short disclosure beep on the call's own volume, so it is as loud as the call and no louder. */
class DisclosureBeeper {
    private var tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, VOLUME) }.getOrNull()

    fun beep() {
        val t = tone ?: return
        runCatching { t.startTone(ToneGenerator.TONE_PROP_BEEP, DisclosurePolicy.BEEP_MS) }.onFailure { Log.w(TAG, "beep failed: ${it.javaClass.simpleName}") }
    }

    fun release() { tone?.release(); tone = null }

    private companion object { const val TAG = "CallGuard.Beep"; const val VOLUME = 35 }
}
