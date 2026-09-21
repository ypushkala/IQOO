package com.callguard.caller

import android.content.Context

/** Numbers the user chose to block, as salted hashes (never the numbers) in private app storage. */
class Blocklist(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("callguard_blocklist", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getString("hashes", "").orEmpty().split(',').filter { it.isNotEmpty() }.toSet()
    fun contains(hash: String) = hash in all()
    fun add(hash: String) = prefs.edit().putString("hashes", (all() + hash).joinToString(",")).apply()
    fun remove(hash: String) = prefs.edit().putString("hashes", (all() - hash).joinToString(",")).apply()
    fun clear() = prefs.edit().clear().apply()
    val size get() = all().size
}
