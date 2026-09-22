package com.callguard.core

/**
 * What to say, not just what to do: a short scripted line shown under a live warning (never spoken, to keep the TTS warning
 * inside its short time budget) and repeated in the post-call advice, so the moment of panic has a ready-made way out.
 */
object CoachingLine {
    // Same priority order as RecoveryPlan.urgentTactic: the tactic most worth acting on first.
    private val priority = listOf(Tactic.REMOTE_ACCESS, Tactic.CREDENTIAL_REQUEST, Tactic.AUTHORITY_THREAT, Tactic.ACCOUNT_THREAT, Tactic.PAYMENT_LURE)

    fun forTactics(tactics: Collection<Tactic>, lang: Lang): String? = priority.firstOrNull { it in tactics }?.let { text(it, lang) }

    private fun text(t: Tactic, lang: Lang): String = when (t) {
        Tactic.REMOTE_ACCESS -> when (lang) {
            Lang.EN -> "Say: \"I will not install anything. I'm hanging up.\""
            Lang.HI -> "कहें: \"मैं कुछ इंस्टॉल नहीं करूँगा/करूँगी। मैं फ़ोन रख रहा/रही हूँ।\""
            Lang.TE -> "ఇలా చెప్పండి: \"నేను ఏమీ ఇన్‌స్టాల్ చేయను. ఫోన్ పెడుతున్నాను.\""
        }
        Tactic.CREDENTIAL_REQUEST -> when (lang) {
            Lang.EN -> "Say: \"I don't share OTPs. I'll call my bank myself.\""
            Lang.HI -> "कहें: \"मैं OTP नहीं बताता/बताती। मैं ख़ुद बैंक को फ़ोन करूँगा/करूँगी।\""
            Lang.TE -> "ఇలా చెప్పండి: \"నేను OTP చెప్పను. నేనే బ్యాంకుకు ఫోన్ చేస్తాను.\""
        }
        Tactic.AUTHORITY_THREAT -> when (lang) {
            Lang.EN -> "Say: \"Send this in writing to my local police station. I'm hanging up.\""
            Lang.HI -> "कहें: \"यह मुझे लिखित में मेरे थाने भेजिए। मैं फ़ोन रख रहा/रही हूँ।\""
            Lang.TE -> "ఇలా చెప్పండి: \"దీన్ని నా స్థానిక పోలీస్ స్టేషన్‌కు రాతపూర్వకంగా పంపండి. ఫోన్ పెడుతున్నాను.\""
        }
        Tactic.ACCOUNT_THREAT -> when (lang) {
            Lang.EN -> "Say: \"I'll check this myself on the official app.\""
            Lang.HI -> "कहें: \"मैं यह ख़ुद आधिकारिक ऐप पर देख लूँगा/लूँगी।\""
            Lang.TE -> "ఇలా చెప్పండి: \"దీన్ని నేనే అధికారిక యాప్‌లో చూసుకుంటాను.\""
        }
        Tactic.PAYMENT_LURE -> when (lang) {
            Lang.EN -> "Say: \"I don't pay fees to receive money. Goodbye.\""
            Lang.HI -> "कहें: \"पैसे पाने के लिए मैं फ़ीस नहीं देता/देती। नमस्ते।\""
            Lang.TE -> "ఇలా చెప్పండి: \"డబ్బు పొందడానికి నేను ఫీజు కట్టను. వెళ్తున్నాను.\""
        }
        else -> when (lang) { Lang.EN -> "Say: \"I'll call you back on a number I look up myself.\""; Lang.HI -> "कहें: \"मैं ख़ुद ढूँढे नंबर से आपको वापस फ़ोन करूँगा/करूँगी।\""; Lang.TE -> "ఇలా చెప్పండి: \"నేనే వెతికిన నంబర్‌తో మీకు తిరిగి కాల్ చేస్తాను.\"" }
    }
}
