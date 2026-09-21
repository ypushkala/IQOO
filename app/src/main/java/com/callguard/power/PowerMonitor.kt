package com.callguard.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.callguard.core.PowerSnapshot
import com.callguard.core.ResourcePolicy
import com.callguard.core.Throttle

/**
 * Watches thermal status, battery temperature/level and battery saver, and reports the throttle
 * decided by [ResourcePolicy]. Polls every [POLL_MS] and reacts immediately to thermal-status changes.
 * It never logs anything but bands and numbers.
 */
class PowerMonitor(context: Context, private val onUpdate: (PowerSnapshot, Throttle) -> Unit) {
    private val ctx = context.applicationContext
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastBand: Any? = null
    private val thermalListener = if (Build.VERSION.SDK_INT >= 29) PowerManager.OnThermalStatusChangedListener { handler.post { evaluate() } } else null
    private val poll = object : Runnable {
        override fun run() { evaluate(); if (running) handler.postDelayed(this, POLL_MS) }
    }

    fun start() {
        if (running) return
        running = true
        if (Build.VERSION.SDK_INT >= 29) pm.addThermalStatusListener(ctx.mainExecutor, thermalListener!!)
        handler.post(poll)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(poll)
        if (Build.VERSION.SDK_INT >= 29) thermalListener?.let { pm.removeThermalStatusListener(it) }
    }

    fun snapshot(): PowerSnapshot {
        val b: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) > 0
        return PowerSnapshot(
            thermalStatus = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else 0,
            batteryTempC = temp,
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = if (b == null) null else plugged || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            powerSave = pm.isPowerSaveMode,
        )
    }

    private fun evaluate() {
        val snap = snapshot()
        val t = ResourcePolicy.decide(snap)
        if (t.band != lastBand) {
            Log.i(TAG, "power band ${lastBand ?: "-"} -> ${t.band} (${t.reason.ifEmpty { "ok" }}) thermal=${snap.thermalStatus} tempC=${snap.batteryTempC} battery=${snap.batteryPercent}% charging=${snap.charging}")
            lastBand = t.band
        }
        onUpdate(snap, t)
    }

    private companion object { const val TAG = "CallGuard.Power"; const val POLL_MS = 15_000L }
}
