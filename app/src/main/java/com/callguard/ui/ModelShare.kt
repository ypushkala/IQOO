package com.callguard.ui

import android.content.Context
import android.content.Intent
import android.os.StatFs
import androidx.core.content.FileProvider
import com.callguard.core.ModelImport
import com.callguard.core.ModelSlot
import com.callguard.gemma.GemmaClassifier
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Lets someone who already has CallGuard set it up on a family member's phone: builds the same one-file model pack (or copies the
 * app) into a temporary folder and hands it to Android's share sheet (chat app, Quick Share, Bluetooth ...). Nothing goes anywhere
 * unless the user picks a target, and the temporary copy is removed the next time this screen opens.
 */
object ModelShare {
    enum class Result { READY, NOTHING_TO_SHARE, NO_SPACE, FAILED }

    private fun dir(c: Context) = File(c.cacheDir, "share")
    fun cleanUp(c: Context) { dir(c).deleteRecursively() }

    fun installedModels(c: Context): List<Pair<ModelSlot, File>> {
        val root = GemmaClassifier.modelDirs(c).first()
        return ModelSlot.values().map { it to File(root, it.relativePath) }.filter { it.second.isFile }
    }

    /** Builds callguard-models.zip (uncompressed, same layout the importer reads). */
    fun buildModelPack(c: Context, onFile: (String) -> Unit): Pair<Result, File?> {
        val files = installedModels(c)
        if (files.isEmpty()) return Result.NOTHING_TO_SHARE to null
        cleanUp(c); dir(c).mkdirs()
        val need = files.sumOf { it.second.length() }
        if (!ModelImport.hasSpace(StatFs(c.cacheDir.absolutePath).availableBytes, need)) return Result.NO_SPACE to null
        val out = File(dir(c), "callguard-models.zip")
        return try {
            ZipOutputStream(out.outputStream().buffered(1 shl 20)).use { z ->
                z.setLevel(0) // the models are already compressed
                for ((slot, f) in files) { onFile(slot.destName); z.putNextEntry(ZipEntry(slot.destName)); f.inputStream().use { it.copyTo(z, 1 shl 20) }; z.closeEntry() }
            }
            Result.READY to out
        } catch (t: Throwable) { out.delete(); Result.FAILED to null }
    }

    /** A copy of the installed app, so a family member can install it too (they must allow installs from this source). */
    fun buildAppCopy(c: Context): Pair<Result, File?> {
        val src = File(c.applicationInfo.sourceDir)
        cleanUp(c); dir(c).mkdirs()
        if (!ModelImport.hasSpace(StatFs(c.cacheDir.absolutePath).availableBytes, src.length())) return Result.NO_SPACE to null
        val out = File(dir(c), "CallGuard.apk")
        return try { src.copyTo(out, overwrite = true); Result.READY to out } catch (t: Throwable) { out.delete(); Result.FAILED to null }
    }

    fun shareIntent(c: Context, f: File, mime: String, title: String): Intent {
        val uri = FileProvider.getUriForFile(c, c.packageName + ".share", f)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            title,
        )
    }

    /** Builds the file on a background thread, then opens the share sheet. [status] shows progress or a problem message. */
    fun share(activity: android.app.Activity, app: Boolean, lang: com.callguard.core.Lang, status: (String) -> Unit) {
        val what = if (app) "CallGuard.apk" else "callguard-models.zip"
        status(com.callguard.core.UiStrings.fmt(com.callguard.core.Ui.MI_SHARE_BUILDING_FMT, lang, what))
        Thread {
            val (r, f) = if (app) buildAppCopy(activity) else buildModelPack(activity) { }
            activity.runOnUiThread {
                when {
                    r == Result.READY && f != null -> {
                        status("")
                        activity.startActivity(shareIntent(activity, f, if (app) "application/vnd.android.package-archive" else "application/zip",
                            com.callguard.core.UiStrings.get(if (app) com.callguard.core.Ui.MI_SHARE_APP else com.callguard.core.Ui.MI_SHARE_MODELS, lang)))
                    }
                    r == Result.NOTHING_TO_SHARE -> status(com.callguard.core.UiStrings.get(com.callguard.core.Ui.MI_SHARE_NONE, lang))
                    r == Result.NO_SPACE -> status(com.callguard.core.UiStrings.get(com.callguard.core.Ui.MI_SHARE_NO_SPACE, lang))
                    else -> status(com.callguard.core.UiStrings.get(com.callguard.core.Ui.MI_SHARE_FAILED, lang))
                }
            }
        }.start()
    }
}
