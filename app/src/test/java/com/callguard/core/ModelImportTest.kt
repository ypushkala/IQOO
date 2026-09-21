package com.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelImportTest {
    @Test fun filesAreRecognisedByNameEvenIfRenamed() {
        assertEquals(ModelSlot.GEMMA, ModelImport.classify("gemma3-1b-it-int4.task"))
        assertEquals(ModelSlot.GEMMA, ModelImport.classify("Gemma3 (1).TASK"))
        assertEquals(ModelSlot.OMNILINGUAL_MODEL, ModelImport.classify("model.int8.onnx"))
        assertEquals(ModelSlot.OMNILINGUAL_MODEL, ModelImport.classify("model.int8 (1).onnx"))
        assertEquals(ModelSlot.OMNILINGUAL_TOKENS, ModelImport.classify("tokens.txt"))
    }
    @Test fun otherFilesAreNotModels() {
        for (n in listOf("photo.jpg", "notes.txt", "model.onnx", "setup.apk", "", null)) assertNull(n, ModelImport.classify(n))
    }
    @Test fun sizesAreSanityChecked() {
        assertTrue(ModelImport.sizeOk(ModelSlot.GEMMA, 554_661_243))
        assertFalse(ModelImport.sizeOk(ModelSlot.GEMMA, 5_000))              // a stub or a failed download
        assertFalse(ModelImport.sizeOk(ModelSlot.OMNILINGUAL_TOKENS, 900_000_000))
    }
    @Test fun spaceCheckKeepsAMargin() {
        assertFalse(ModelImport.hasSpace(500_000_000, 480_000_000))
        assertTrue(ModelImport.hasSpace(700_000_000, 480_000_000))
    }
    @Test fun knownChecksumsAreVerifiedOthersAreOnlyAccepted() {
        val good = ModelSlot.OMNILINGUAL_TOKENS.knownSha256.first().uppercase()
        assertEquals(ImportOutcome.IMPORTED_VERIFIED, ModelImport.outcomeFor(ModelSlot.OMNILINGUAL_TOKENS, good))
        assertEquals(ImportOutcome.IMPORTED_UNVERIFIED, ModelImport.outcomeFor(ModelSlot.OMNILINGUAL_TOKENS, "00"))
    }
    @Test fun destinationsMatchWhereTheAppLooks() {
        assertEquals("gemma3-1b-it-int4.task", ModelSlot.GEMMA.relativePath)
        assertEquals("omnilingual/model.int8.onnx", ModelSlot.OMNILINGUAL_MODEL.relativePath)
        assertEquals("00ff", ModelImport.hex(byteArrayOf(0, -1)))
    }
}
