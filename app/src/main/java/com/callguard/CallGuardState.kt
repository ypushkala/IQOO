package com.callguard

import android.os.Handler
import android.os.Looper
import com.callguard.core.CallSummary
import com.callguard.core.DetectionResult
import com.callguard.core.Lang
import com.callguard.core.Tactic

/** Snapshot published by the service and rendered by the activity. Lives only in memory. */
data class UiState(
    val monitoring: Boolean = false,
    val callState: String = "Idle (not monitoring)",
    val captureState: String = "Stopped",
    val transcript: String = "",
    val detection: DetectionResult = DetectionResult.NONE,
    val lastAlert: String = "",
    /** The tactic(s) behind the most recent alert, so the screen can show a short localized "why" and coaching line
     *  in whatever language is active right now, without re-firing the alert. Cleared at the start of each call. */
    val alertTactics: List<Tactic> = emptyList(),
    val gemma: String = "Gemma: off",
    val indic: String = "",
    val caller: String = "",
    /** The last finished call, shown after hang-up. In memory only; never written to disk. */
    val summary: CallSummary? = null,
    /** Language the summary should open in (follows the warning-language setting and the call). */
    val summaryLang: Lang = Lang.EN,
    /** The language the current/last call was in (English until Hindi or Telugu is heard). */
    val callLang: Lang = Lang.EN,
    /** Thermal/battery status line, e.g. "Power: normal · 34.5 °C · 43% (charging)". */
    val power: String = "",
)

object CallGuardState {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var state = UiState()
        private set
    @Volatile var listener: ((UiState) -> Unit)? = null

    @Synchronized fun update(f: (UiState) -> UiState) {
        state = f(state)
        val s = state
        main.post { listener?.invoke(s) }
    }
}
