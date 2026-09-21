package com.callguard.caller

import android.content.Context
import com.callguard.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the signed scam-number list, only when the user taps "Update". Sends nothing but a plain GET (no identifiers,
 * no cookies, no numbers, no call data). The pack is installed only if its signature verifies against the built-in key.
 * Call from a background thread.
 */
object PackUpdater {
    val available get() = BuildConfig.PACK_URL.startsWith("https://") && BuildConfig.PACK_PUBLIC_KEY.isNotBlank()

    fun update(context: Context): UpdateResult {
        if (!available) return UpdateResult.NOT_AVAILABLE
        val pack = fetch(BuildConfig.PACK_URL) ?: return UpdateResult.OFFLINE
        val sig = fetch(BuildConfig.PACK_URL + ".sig") ?: return UpdateResult.OFFLINE
        return PackStore(context).install(pack, String(sig, Charsets.UTF_8), BuildConfig.PACK_PUBLIC_KEY)
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = false; c.useCaches = false
            c.setRequestProperty("User-Agent", "CallGuard")
            if (c.responseCode != 200) return null
            c.inputStream.use { it.readNBytes(com.callguard.core.ScamPack.MAX_BYTES + 1) }
        } finally { c.disconnect() }
    }.getOrNull()
}
