package com.callguard.debug

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.callguard.core.ProbeContext
import com.callguard.core.ProbeReport

/** DEBUG ONLY: open by long-pressing the title on the main screen. See [ProbeService]. */
class ProbeActivity : Activity() {
    private lateinit var out: TextView
    private lateinit var status: TextView
    private val checks = LinkedHashMap<String, CheckBox>()
    private var seconds = 10
    private var delay = 0
    private lateinit var secondsButton: Button
    private lateinit var delayButton: Button
    private lateinit var note: android.widget.EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        fun tv(sp: Float, bold: Boolean = false) = TextView(this).apply { textSize = sp; setPadding(0, 8, 0, 8); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
        root.addView(tv(20f, true).apply { text = "Capture probe (debug)" })
        root.addView(tv(13f).apply { text = "Does the microphone deliver real sound during a call? Start a call, put it on speaker, tap Run, then switch to the call screen. Results contain statistics only: no audio, no numbers." })
        ProbeService.SOURCES.keys.forEach { name ->
            val cb = CheckBox(this).apply { text = name; isChecked = name in setOf("MIC", "VOICE_RECOGNITION", "UNPROCESSED", "CAMCORDER") }
            checks[name] = cb; root.addView(cb)
        }
        secondsButton = Button(this).apply { setOnClickListener { seconds = if (seconds == 10) 20 else if (seconds == 20) 30 else 10; refresh() } }.also(root::addView)
        delayButton = Button(this).apply { setOnClickListener { delay = if (delay == 0) 15 else if (delay == 15) 30 else 0; refresh() } }.also(root::addView)
        note = android.widget.EditText(this).apply { hint = "Note (e.g. caller on speaker, phone model)" }.also(root::addView)
        root.addView(Button(this).apply { text = "Run probe"; setOnClickListener { run() } })
        root.addView(Button(this).apply { text = "Accessibility settings (experiment E3)"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        root.addView(Button(this).apply { text = "Share results"; setOnClickListener { share() } })
        status = tv(15f, true).also(root::addView)
        out = tv(13f).also(root::addView)
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onStart() { super.onStart(); ProbeState.listener = { runOnUiThread { render() } }; render() }
    override fun onStop() { ProbeState.listener = null; super.onStop() }

    private fun refresh() { secondsButton.text = "Record each source for: $seconds s"; delayButton.text = "Start delay (time to open the call screen): $delay s" }

    private fun run() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return }
        val chosen = checks.filter { it.value.isChecked }.keys.toTypedArray()
        if (chosen.isEmpty()) return
        startForegroundService(Intent(this, ProbeService::class.java).putExtra(ProbeService.EXTRA_SOURCES, chosen).putExtra(ProbeService.EXTRA_SECONDS, seconds).putExtra(ProbeService.EXTRA_DELAY, delay))
    }

    private fun context(): ProbeContext {
        val am = getSystemService(AudioManager::class.java)
        val tm = getSystemService(TelephonyManager::class.java)
        val mode = when (am.mode) { AudioManager.MODE_NORMAL -> "NORMAL"; AudioManager.MODE_RINGTONE -> "RINGTONE"; AudioManager.MODE_IN_CALL -> "IN_CALL"; AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"; else -> "OTHER(${am.mode})" }
        val call = runCatching { when (tm.callState) { TelephonyManager.CALL_STATE_IDLE -> "idle"; TelephonyManager.CALL_STATE_RINGING -> "ringing"; else -> "offhook" } }.getOrDefault("unknown")
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .contains(ComponentName(this, ProbeAccessibilityService::class.java).flattenToString())
        @Suppress("DEPRECATION")
        return ProbeContext("${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE, mode, call, am.isSpeakerphoneOn, am.isMicrophoneMute, enabled, note.text.toString())
    }

    private fun render() {
        status.text = "${ProbeState.status}${if (ProbeState.running) " …" else ""}"
        out.text = if (ProbeState.results.isEmpty()) "(no results yet)" else ProbeReport.text(context(), ProbeState.results.toList())
    }

    private fun share() {
        val text = ProbeReport.text(context(), ProbeState.results.toList())
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share probe results"))
    }
}
