package com.callguard.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * One-file model delivery: a plain zip holding the model files (build it with tools/make_model_pack.sh). One file can be shared
 * by USB, memory card, Bluetooth or a chat app, or preloaded by a shop or phone maker, and opened straight from the Files app.
 * Only the known file names are used and each goes to its fixed place, so a zip cannot write anywhere else.
 */
object ModelPack {
    data class Result(val entry: String, val slot: ModelSlot?, val outcome: ImportOutcome)

    fun looksLikePack(displayName: String?) = displayName?.trim()?.lowercase()?.endsWith(".zip") == true

    /**
     * Streams the zip, copying each recognised model entry to `dest(slot)` through a `.part` file (renamed only when complete and
     * the size is sane). Unknown entries are skipped. [freeBytes] lets the caller refuse an entry that would not fit.
     */
    fun extract(
        input: InputStream,
        dest: (ModelSlot) -> File,
        sizeOk: (ModelSlot, Long) -> Boolean = ModelImport::sizeOk,
        freeBytes: (File) -> Long = { Long.MAX_VALUE },
        progress: (String) -> Unit = {},
    ): List<Result> {
        val results = ArrayList<Result>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/')
                val slot = ModelImport.classify(name)
                if (slot == null) { results += Result(name, null, ImportOutcome.NOT_A_MODEL); continue }
                val out = dest(slot).also { it.parentFile?.mkdirs() }
                if (e.size > 0 && !ModelImport.hasSpace(freeBytes(out.parentFile ?: out), e.size)) { results += Result(name, slot, ImportOutcome.NO_SPACE); continue }
                progress(name)
                val tmp = File(out.parentFile, out.name + ".part")
                results += try {
                    val md = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    tmp.outputStream().use { o ->
                        val buf = ByteArray(1 shl 20)
                        while (true) { val n = zip.read(buf); if (n < 0) break; o.write(buf, 0, n); md.update(buf, 0, n); total += n }
                    }
                    when {
                        !sizeOk(slot, total) -> { tmp.delete(); Result(name, slot, ImportOutcome.WRONG_SIZE) }
                        (out.exists() && !out.delete()) || !tmp.renameTo(out) -> { tmp.delete(); Result(name, slot, ImportOutcome.FAILED) }
                        else -> Result(name, slot, ModelImport.outcomeFor(slot, ModelImport.hex(md.digest())))
                    }
                } catch (t: Throwable) { tmp.delete(); Result(name, slot, ImportOutcome.FAILED) }
            }
        }
        return results
    }
}
