package com.callguard.core

/**
 * Decides whether a detection should raise a new alert. Only MEDIUM/HIGH signals count, and each
 * signal key (rule + matched phrase) is silenced for [cooldownMs] after alerting, so a phrase
 * that stays in the rolling transcript does not re-trigger.
 */
class AlertDebouncer(private val cooldownMs: Long = 30_000L) {
    private val lastAlerted = HashMap<String, Long>()
    /** Highest level alerted within the current cooldown window (any tactic, any source). */
    private var recentAt = Long.MIN_VALUE / 2
    private var recentLevel = RiskLevel.LOW

    /** Returns the signals that should alert now (empty = stay quiet) and records them. */
    fun onDetection(result: DetectionResult, nowMs: Long): List<Signal> {
        val fresh = result.signals.filter { s ->
            s.level >= RiskLevel.MEDIUM &&
                lastAlerted[s.key].let { it == null || nowMs - it >= cooldownMs } &&
                !(s.source == "gemma" && restatesRecentAlert(s, nowMs))
        }
        if (fresh.isNotEmpty()) {
            if (nowMs - recentAt >= cooldownMs) recentLevel = RiskLevel.LOW
            recentAt = nowMs
            fresh.forEach { lastAlerted[it.key] = nowMs; if (it.level > recentLevel) recentLevel = it.level }
        }
        return fresh
    }

    /**
     * A model signal only alerts if it raises the level above any alert in the last [cooldownMs]. The
     * user was already warned (at that level or higher), so a same-or-weaker model verdict, even for a
     * tactic the rules do not know, adds nothing to act on.
     */
    private fun restatesRecentAlert(s: Signal, nowMs: Long): Boolean =
        nowMs - recentAt < cooldownMs && recentLevel >= s.level

    fun reset() { lastAlerted.clear(); recentAt = Long.MIN_VALUE / 2; recentLevel = RiskLevel.LOW }
}
