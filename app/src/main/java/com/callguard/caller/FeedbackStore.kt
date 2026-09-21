package com.callguard.caller

import android.content.Context
import com.callguard.core.FeedbackCodec
import com.callguard.core.FeedbackEntry
import com.callguard.core.FeedbackLog

/** The user's answers to "was this a scam?", kept in private app storage on this phone. Never uploaded, excluded from backup. */
class FeedbackStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("callguard_feedback", Context.MODE_PRIVATE)

    @Synchronized fun all(): List<FeedbackEntry> = FeedbackCodec.decodeAll(prefs.getString("log", "").orEmpty())

    @Synchronized fun add(e: FeedbackEntry) {
        prefs.edit().putString("log", FeedbackCodec.encodeAll(FeedbackLog.append(all(), e))).apply()
    }

    fun trustedHashes(): Set<String> = FeedbackLog.trustedHashes(all())

    @Synchronized fun clear() { prefs.edit().remove("log").apply() }
}
