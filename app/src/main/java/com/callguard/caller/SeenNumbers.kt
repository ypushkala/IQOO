package com.callguard.caller

import android.content.Context
import com.callguard.core.NumberHash
import java.util.UUID

/**
 * Remembers which numbers CallGuard has seen, as salted hashes in private app storage (never the
 * numbers themselves, never uploaded, excluded from backup). Capped, oldest dropped first.
 */
class SeenNumbers(context: Context) {
    private val prefs = context.getSharedPreferences("callguard_seen", Context.MODE_PRIVATE)

    /** The salted hash used everywhere CallGuard needs to refer to a number without storing it. */
    @Synchronized fun hashOf(digits: String): String {
        val salt = prefs.getString("salt", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("salt", it).apply() }
        return NumberHash.hash(salt, digits)
    }

    /** Returns true if this is the first time [digits] was seen, and records it. */
    @Synchronized fun checkAndRecord(digits: String): Boolean {
        if (digits.isEmpty()) return false
        val salt = prefs.getString("salt", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("salt", it).apply() }
        val h = NumberHash.hash(salt, digits)
        val seen = prefs.getString("hashes", "").orEmpty().split(',').filter { it.isNotEmpty() }
        if (h in seen) return false
        prefs.edit().putString("hashes", (seen + h).takeLast(MAX).joinToString(",")).apply()
        return true
    }

    private companion object { const val MAX = 500 }
}
