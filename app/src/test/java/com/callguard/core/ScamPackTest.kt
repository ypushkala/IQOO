package com.callguard.core

import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamPackTest {
    private val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val pub = Base64.getEncoder().encodeToString(kp.public.encoded)
    private fun sign(b: ByteArray, key: java.security.PrivateKey = kp.private) =
        Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withECDSA").run { initSign(key); update(b); sign() })
    private val pack = "# test\nversion|5\n92|2|listed\n84|3|listed too\n".toByteArray()

    @Test fun aSignedNewerPackIsAccepted() {
        val (v, e) = ScamPack.judge(pack, sign(pack), pub, installedVersion = 4)
        assertEquals(ScamPack.Verdict.ACCEPT, v); assertEquals(2, e.size)
    }
    @Test fun aTamperedPackIsRejected() {
        val tampered = String(pack).replace("92|2", "91|5").toByteArray()
        assertEquals(ScamPack.Verdict.BAD_SIGNATURE, ScamPack.judge(tampered, sign(pack), pub, 0).first)
    }
    @Test fun aPackSignedByAnotherKeyIsRejected() {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        assertEquals(ScamPack.Verdict.BAD_SIGNATURE, ScamPack.judge(pack, sign(pack, other.private), pub, 0).first)
    }
    @Test fun noKeyConfiguredMeansNothingIsEverInstalled() {
        assertEquals(ScamPack.Verdict.BAD_SIGNATURE, ScamPack.judge(pack, sign(pack), "", 0).first)
        assertFalse(ScamPack.verify(pack, "not base64!!", pub))
    }
    @Test fun anOlderOrSamePackIsNotInstalled() {
        assertEquals(ScamPack.Verdict.NOT_NEWER, ScamPack.judge(pack, sign(pack), pub, 5).first)
        assertEquals(ScamPack.Verdict.NOT_NEWER, ScamPack.judge(pack, sign(pack), pub, 9).first)
    }
    @Test fun aSignedFileWithoutVersionOrEntriesIsNotAPack() {
        val a = "92|2|x\n".toByteArray(); val b = "version|3\n".toByteArray()
        assertEquals(ScamPack.Verdict.NOT_A_PACK, ScamPack.judge(a, sign(a), pub, 0).first)
        assertEquals(ScamPack.Verdict.NOT_A_PACK, ScamPack.judge(b, sign(b), pub, 0).first)
    }
    @Test fun oversizedPacksAreRefused() {
        val big = ByteArray(ScamPack.MAX_BYTES + 1) { 'a'.code.toByte() }
        assertEquals(ScamPack.Verdict.TOO_BIG, ScamPack.judge(big, sign(big), pub, 0).first)
    }
    @Test fun versionParsing() { assertEquals(5L, ScamPack.version("x\nversion|5\n")); assertEquals(null, ScamPack.version("nothing")) }
}

/** The "zero internet" promise, checked at the source: only the optional online build may declare network access. */
class NetworkPermissionSourceTest {
    private fun text(path: String) = File(path).takeIf { it.exists() }?.readText().orEmpty()

    @Test fun onlyTheOnlineBuildDeclaresInternet() {
        for (dir in listOf("main", "offline", "debug")) assertFalse("$dir must not declare INTERNET", "permission.INTERNET" in text("src/$dir/AndroidManifest.xml"))
        assertTrue("permission.INTERNET" in text("src/online/AndroidManifest.xml"))
    }
    @Test fun noNetworkCodeInSharedOrOfflineSources() {
        val banned = listOf("HttpURLConnection", "java.net.URL", "okhttp", "HttpsURLConnection")
        for (root in listOf("src/main/java", "src/offline/java")) File(root).walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val t = f.readText()
            for (b in banned) assertFalse("${f.name} uses $b", b in t.replace(Regex("(?m)^\\s*(//|\\*|/\\*).*$"), ""))
        }
    }
}
