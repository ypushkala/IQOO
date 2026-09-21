package com.callguard.core

enum class PowerBand { NORMAL, WARM, HOT, CRITICAL }

/** What the phone reports. Statuses use android.os.PowerManager.THERMAL_STATUS_* values (0 none ... 6 shutdown, -1 unknown). */
data class PowerSnapshot(
    val thermalStatus: Int = 0,
    val batteryTempC: Float? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val powerSave: Boolean = false,
)

/** What to do about it. Whisper and the rules always keep running: they are the core protection. */
data class Throttle(
    val band: PowerBand,
    val gemmaIntervalMs: Long,
    val gemmaPaused: Boolean,
    val skipTranslation: Boolean,
    val reason: String,
) {
    val label: String
        get() = when (band) {
            PowerBand.NORMAL -> "Power: normal"
            PowerBand.WARM -> "Power: warm ($reason) — Gemma slowed"
            PowerBand.HOT -> "Power: hot ($reason) — Gemma paused"
            PowerBand.CRITICAL -> "Power: critical ($reason) — Gemma paused, extra language pass off"
        }
}

/**
 * Heuristic thermal/battery policy. Thresholds are conservative defaults for a phone held to the
 * ear or lying in the sun, not measured limits for a particular device.
 *  - WARM: Gemma runs less often.
 *  - HOT: Gemma is paused. Whisper (with Hindi translation) and the rules keep running.
 *  - CRITICAL: the extra language pass (Indic re-read / translation) is skipped too, so only Whisper and the rules run.
 * Translation is kept as long as possible because without it Hindi speech is only seen through the
 * transliterated rules, which miss more.
 */
object ResourcePolicy {
    const val NORMAL_INTERVAL_MS = 3_500L
    const val WARM_INTERVAL_MS = 9_000L

    fun decide(s: PowerSnapshot): Throttle {
        var band = PowerBand.NORMAL
        var reason = ""
        fun raise(b: PowerBand, why: String) { if (b > band) { band = b; reason = why } }

        when {
            s.thermalStatus >= 4 -> raise(PowerBand.CRITICAL, "thermal ${thermalName(s.thermalStatus)}")
            s.thermalStatus == 3 -> raise(PowerBand.HOT, "thermal severe")
            s.thermalStatus == 2 -> raise(PowerBand.WARM, "thermal moderate")
        }
        s.batteryTempC?.let {
            when {
                it >= 46f -> raise(PowerBand.CRITICAL, "battery ${"%.1f".format(it)} °C")
                it >= 43f -> raise(PowerBand.HOT, "battery ${"%.1f".format(it)} °C")
                it >= 40f -> raise(PowerBand.WARM, "battery ${"%.1f".format(it)} °C")
            }
        }
        val discharging = s.charging == false
        s.batteryPercent?.let {
            when {
                discharging && it <= 7 -> raise(PowerBand.HOT, "battery $it%")
                discharging && it <= 15 -> raise(PowerBand.WARM, "battery $it%")
            }
        }
        if (s.powerSave) raise(PowerBand.WARM, "battery saver on")

        return when (band) {
            PowerBand.NORMAL -> Throttle(band, NORMAL_INTERVAL_MS, gemmaPaused = false, skipTranslation = false, reason = reason)
            PowerBand.WARM -> Throttle(band, WARM_INTERVAL_MS, gemmaPaused = false, skipTranslation = false, reason = reason)
            PowerBand.HOT -> Throttle(band, WARM_INTERVAL_MS, gemmaPaused = true, skipTranslation = false, reason = reason)
            PowerBand.CRITICAL -> Throttle(band, WARM_INTERVAL_MS, gemmaPaused = true, skipTranslation = true, reason = reason)
        }
    }

    private fun thermalName(status: Int) = when (status) { 4 -> "critical"; 5 -> "emergency"; 6 -> "shutdown"; else -> "level $status" }
}
