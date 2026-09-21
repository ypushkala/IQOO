package com.callguard.core

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * A downloadable scam-number list. Same line format as the bundled list plus a `version|N` line. It is only ever installed if
 * its signature checks out against the public key built into the app, and only if it is newer than what is installed.
 */
object ScamPack {
    const val MAX_BYTES = 512 * 1024
    const val MAX_ENTRIES = 5000

    fun version(text: String): Long? = text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("version|") }?.substringAfter('|')?.trim()?.toLongOrNull()

    /** ECDSA P-256 / SHA-256 over the exact bytes of the pack. False for anything malformed, never throws. */
    fun verify(pack: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean = runCatching {
        if (publicKeyBase64.isBlank() || signatureBase64.isBlank()) return false
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getMimeDecoder().decode(publicKeyBase64.trim())))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key); update(pack)
            verify(Base64.getMimeDecoder().decode(signatureBase64.trim()))
        }
    }.getOrDefault(false)

    enum class Verdict { ACCEPT, BAD_SIGNATURE, TOO_BIG, NOT_A_PACK, NOT_NEWER }

    fun judge(pack: ByteArray, signatureBase64: String, publicKeyBase64: String, installedVersion: Long): Pair<Verdict, List<ScamPrefix>> {
        if (pack.size > MAX_BYTES) return Verdict.TOO_BIG to emptyList()
        if (!verify(pack, signatureBase64, publicKeyBase64)) return Verdict.BAD_SIGNATURE to emptyList()
        val text = String(pack, Charsets.UTF_8)
        val v = version(text) ?: return Verdict.NOT_A_PACK to emptyList()
        val entries = ScamPrefixList.parse(text)
        if (entries.isEmpty() || entries.size > MAX_ENTRIES) return Verdict.NOT_A_PACK to emptyList()
        if (v <= installedVersion) return Verdict.NOT_NEWER to emptyList()
        return Verdict.ACCEPT to entries
    }
}
