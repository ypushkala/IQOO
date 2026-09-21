package com.callguard.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.callguard.CallGuardState
import com.callguard.asr.IndicAsr
import com.callguard.core.HealthInputs
import com.callguard.gemma.GemmaClassifier

/** Reads the phone's real state for the health card and the setup screen. */
object HealthProbe {
    fun inputs(ctx: Context): HealthInputs {
        fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val role = runCatching {
            val rm = ctx.getSystemService(RoleManager::class.java)
            rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }.getOrDefault(false)
        val battery = runCatching { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }.getOrDefault(false)
        val gemma = GemmaClassifier.modelDirs(ctx).any { d -> java.io.File(d, GemmaClassifier.MODEL_NAME).isFile || d.listFiles { f -> f.name.endsWith(".task") }?.isNotEmpty() == true }
        return HealthInputs(
            mic = granted(Manifest.permission.RECORD_AUDIO),
            phone = granted(Manifest.permission.READ_PHONE_STATE),
            notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
            running = CallGuardState.state.monitoring,
            callerIdRole = role,
            batteryExempt = battery,
            models = gemma && IndicAsr.find(ctx) != null,
        )
    }
}
