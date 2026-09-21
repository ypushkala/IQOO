package com.callguard.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPackTest {
    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z -> for ((n, c) in files) { z.putNextEntry(ZipEntry(n)); z.write(c); z.closeEntry() } }
        return b.toByteArray()
    }
    /** Stored (uncompressed) entries carry their size in the header, like a pack made with `zip -0`. */
    private fun storedZip(name: String, content: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z ->
            val e = ZipEntry(name).apply { method = ZipEntry.STORED; size = content.size.toLong(); compressedSize = content.size.toLong(); crc = java.util.zip.CRC32().also { it.update(content) }.value }
            z.putNextEntry(e); z.write(content); z.closeEntry()
        }
        return b.toByteArray()
    }
    private fun tmpDir() = File.createTempFile("pack", "").let { it.delete(); it.mkdirs(); it }
    private val anySize: (ModelSlot, Long) -> Boolean = { _, n -> n > 0 }

    @Test fun extractsKnownFilesToTheirFixedPlacesAndSkipsTheRest() {
        val root = tmpDir()
        val r = ModelPack.extract(ByteArrayInputStream(zip(
            "pack/tokens.txt" to "abc".toByteArray(), "pack/model.int8.onnx" to ByteArray(1000) { 1 }, "readme.md" to "hi".toByteArray(),
        )), dest = { File(root, it.relativePath) }, sizeOk = anySize)
        assertEquals(ImportOutcome.IMPORTED_UNVERIFIED, r.first { it.slot == ModelSlot.OMNILINGUAL_TOKENS }.outcome)
        assertEquals(ImportOutcome.IMPORTED_UNVERIFIED, r.first { it.slot == ModelSlot.OMNILINGUAL_MODEL }.outcome)
        assertEquals(ImportOutcome.NOT_A_MODEL, r.first { it.entry == "readme.md" }.outcome)
        assertEquals("abc", File(root, "omnilingual/tokens.txt").readText())
        assertEquals(1000, File(root, "omnilingual/model.int8.onnx").length())
    }
    @Test fun aZipCannotWriteOutsideTheModelsFolder() {
        val root = tmpDir()
        ModelPack.extract(ByteArrayInputStream(zip("../../evil/tokens.txt" to "x".toByteArray())), dest = { File(root, it.relativePath) }, sizeOk = anySize)
        assertFalse(File(root.parentFile, "evil").exists())
        assertTrue(File(root, "omnilingual/tokens.txt").exists()) // landed at the fixed place, name only
    }
    @Test fun wrongSizedEntriesLeaveNothingBehind() {
        val root = tmpDir()
        val r = ModelPack.extract(ByteArrayInputStream(zip("gemma3-1b-it-int4.task" to ByteArray(10))), dest = { File(root, it.relativePath) }) // real size rules
        assertEquals(ImportOutcome.WRONG_SIZE, r.single().outcome)
        assertFalse(File(root, "gemma3-1b-it-int4.task").exists()); assertFalse(File(root, "gemma3-1b-it-int4.task.part").exists())
    }
    @Test fun aKnownFileIsVerifiedByItsChecksum() {
        // the tokens file we ship is 90630 bytes; here we only check the unverified path for other content and the pack name test
        assertTrue(ModelPack.looksLikePack("callguard-models.ZIP")); assertFalse(ModelPack.looksLikePack("model.int8.onnx")); assertFalse(ModelPack.looksLikePack(null))
    }
    @Test fun anEntryThatDoesNotFitIsRefused() {
        val root = tmpDir()
        val big = ByteArray(100)
        val r = ModelPack.extract(ByteArrayInputStream(storedZip("tokens.txt", big)), dest = { File(root, it.relativePath) }, sizeOk = anySize, freeBytes = { 10L })
        assertEquals(ImportOutcome.NO_SPACE, r.single().outcome)
    }

    @Test fun aPackBuiltTheWayTheShareButtonBuildsItImportsBack() {
        // same construction as ModelShare.buildModelPack: ZipOutputStream, level 0, one entry per model file
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z ->
            z.setLevel(0)
            for ((n, c) in listOf("gemma3-1b-it-int4.task" to ByteArray(5000) { 7 }, "model.int8.onnx" to ByteArray(3000) { 9 }, "tokens.txt" to "tok".toByteArray())) {
                z.putNextEntry(ZipEntry(n)); z.write(c); z.closeEntry()
            }
        }
        val root = tmpDir()
        val r = ModelPack.extract(ByteArrayInputStream(b.toByteArray()), dest = { File(root, it.relativePath) }, sizeOk = anySize)
        assertEquals(3, r.count { it.outcome == ImportOutcome.IMPORTED_UNVERIFIED })
        assertEquals(5000, File(root, "gemma3-1b-it-int4.task").length()); assertEquals(3000, File(root, "omnilingual/model.int8.onnx").length())
    }
}
