package com.callguard.core

/** The model files CallGuard can use, where each goes (relative to the app's models folder) and what a sane file looks like. */
enum class ModelSlot(val destDir: String, val destName: String, val minBytes: Long, val maxBytes: Long, val knownSha256: Set<String>) {
    GEMMA("", "gemma3-1b-it-int4.task", 300_000_000, 2_000_000_000, setOf("e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee")),
    OMNILINGUAL_MODEL("omnilingual", "model.int8.onnx", 100_000_000, 1_500_000_000, setOf("e3042b2f3b3ef0af2211bf99d2b4bf94a21f5ac0e9898827e7dd6d003a860e91")),
    OMNILINGUAL_TOKENS("omnilingual", "tokens.txt", 1_000, 5_000_000, setOf("7d99997ef207ff14c2cfe825f2aa037528ea250113cc3c6392bfe49326884ba6"));

    val relativePath get() = if (destDir.isEmpty()) destName else "$destDir/$destName"
}

enum class ImportOutcome { IMPORTED_VERIFIED, IMPORTED_UNVERIFIED, NOT_A_MODEL, WRONG_SIZE, NO_SPACE, FAILED }

object ModelImport {
    /** Which slot a picked file is meant for, judged by its name (people rename downloads: "model.int8 (1).onnx"). */
    fun classify(displayName: String?): ModelSlot? {
        val n = displayName?.trim()?.lowercase() ?: return null
        return when {
            n.endsWith(".task") -> ModelSlot.GEMMA
            n.endsWith(".onnx") && "int8" in n -> ModelSlot.OMNILINGUAL_MODEL
            n.endsWith(".txt") && n.startsWith("tokens") -> ModelSlot.OMNILINGUAL_TOKENS
            else -> null
        }
    }

    fun sizeOk(slot: ModelSlot, bytes: Long) = bytes in slot.minBytes..slot.maxBytes

    /** Free space needed: the file itself plus a margin, because the copy is written to a temporary file first. */
    fun hasSpace(freeBytes: Long, fileBytes: Long) = freeBytes >= fileBytes + 50_000_000

    fun outcomeFor(slot: ModelSlot, sha256Hex: String) =
        if (sha256Hex.lowercase() in slot.knownSha256) ImportOutcome.IMPORTED_VERIFIED else ImportOutcome.IMPORTED_UNVERIFIED

    fun hex(digest: ByteArray) = digest.joinToString("") { "%02x".format(it) }
}
