package com.callguard.caller

import android.content.Context
import com.callguard.core.ScamPack
import com.callguard.core.ScamPrefix
import com.callguard.core.ScamPrefixList
import java.io.File

enum class UpdateResult { UPDATED, UP_TO_DATE, REJECTED, OFFLINE, NOT_AVAILABLE }

/** The installed (already verified) scam-number pack, in private app storage. The bundled list is used when there is none. */
class PackStore(private val context: Context) {
    private val file get() = File(context.filesDir, "scam_pack.txt")
    private val prefs get() = context.getSharedPreferences("callguard_pack", Context.MODE_PRIVATE)

    val version: Long get() = if (file.exists()) prefs.getLong("version", 0) else 0

    fun load(): List<ScamPrefix>? = runCatching { if (file.exists()) ScamPrefixList.parse(file.readText()).takeIf { it.isNotEmpty() } else null }.getOrNull()

    /** Verifies, then installs. Anything that does not verify leaves the current list untouched. */
    fun install(pack: ByteArray, signatureBase64: String, publicKeyBase64: String): UpdateResult {
        val (verdict, _) = ScamPack.judge(pack, signatureBase64, publicKeyBase64, version)
        return when (verdict) {
            ScamPack.Verdict.ACCEPT -> {
                val tmp = File(context.filesDir, "scam_pack.tmp").also { it.writeBytes(pack) }
                if (!tmp.renameTo(file)) return UpdateResult.REJECTED
                prefs.edit().putLong("version", ScamPack.version(String(pack, Charsets.UTF_8)) ?: 0).apply()
                UpdateResult.UPDATED
            }
            ScamPack.Verdict.NOT_NEWER -> UpdateResult.UP_TO_DATE
            else -> UpdateResult.REJECTED
        }
    }

    fun clear() { file.delete(); prefs.edit().clear().apply() }
}
