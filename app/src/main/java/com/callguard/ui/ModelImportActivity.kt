package com.callguard.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.alert.AppPrefs
import com.callguard.core.ImportOutcome
import com.callguard.core.ModelImport
import com.callguard.core.ModelPack
import com.callguard.core.ModelSlot
import com.callguard.core.Ui
import com.callguard.core.UiStrings
import com.callguard.gemma.GemmaClassifier
import com.callguard.models.ModelDownloader
import java.io.File
import java.security.MessageDigest

/**
 * Copies model files the user picks (file picker, no storage permission needed) into CallGuard's private folder, checking the
 * size and a SHA-256. Replaces the adb script for non-developers. Nothing is uploaded; files are only read and copied.
 */
class ModelImportActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private lateinit var log: android.widget.TextView
    private val lang get() = prefs.screenLanguage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        ModelShare.cleanUp(this) // a temporary share copy from an earlier visit
        build("")
        // "Open with CallGuard" from the Files app, a chat app or a download notification: import straight away.
        val incoming = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            else -> emptyList()
        }
        if (incoming.isNotEmpty()) process(incoming)
    }

    private fun build(results: String) {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.MI_TITLE, lang); setTextColor(theme.accent) })
        root.addView(theme.text(15f, bold = true).apply { text = UiStrings.get(Ui.MI_PACK_HINT, lang) })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.MI_INTRO, lang) })
        val have = { s: ModelSlot -> File(modelsDir(), s.relativePath).isFile }
        val gemma = if (have(ModelSlot.GEMMA)) "✓" else "✗"
        val indic = if (have(ModelSlot.OMNILINGUAL_MODEL) && have(ModelSlot.OMNILINGUAL_TOKENS)) "✓" else "✗"
        root.addView(theme.text(15f, bold = true).apply { text = UiStrings.fmt(Ui.MI_STATUS_FMT, lang, gemma, indic); setPadding(0, 16, 0, 16) })
        root.addView(theme.button(UiStrings.get(Ui.MI_PICK, lang)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQ_PICK)
        })
        if (ModelDownloader.available) {
            root.addView(theme.button(UiStrings.get(Ui.MI_DL_WIFI, lang)) { ModelDownloader.start(this, wifiOnly = true); build(results) })
            root.addView(theme.button(UiStrings.get(Ui.MI_DL_MOBILE, lang)) { ModelDownloader.start(this, wifiOnly = false); build(results) })
            val st = ModelDownloader.state(this)
            if (st != null && st.active) { root.addView(theme.text(14f).apply { text = UiStrings.fmt(Ui.MI_DL_PROGRESS_FMT, lang, st.percent) }); root.postDelayed({ if (!isFinishing) build(results) }, 2000) }
            else if (st != null && st.failedFiles > 0) root.addView(theme.text(14f).apply { text = UiStrings.fmt(Ui.MI_DL_FAILED_FMT, lang, st.failedFiles) })
        }
        if (ModelShare.installedModels(this).isNotEmpty()) {
            root.addView(theme.button(UiStrings.get(Ui.MI_SHARE_MODELS, lang)) { share(app = false) })
            root.addView(theme.button(UiStrings.get(Ui.MI_SHARE_APP, lang)) { share(app = true) })
        }
        log = theme.text(14f).apply { text = results }
        root.addView(log)
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }

    private fun share(app: Boolean) = ModelShare.share(this, app, lang) { log.text = it }

    private fun modelsDir(): File = GemmaClassifier.modelDirs(this).first()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return
        process(data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data))
    }

    private fun process(uris: List<Uri>) {
        Thread {
            val lines = StringBuilder()
            fun show(extra: String = "") = runOnUiThread { if (::log.isInitialized) log.text = lines.toString() + extra }
            for (u in uris) {
                val (name, size) = describe(u)
                if (ModelPack.looksLikePack(name)) {
                    val results = runCatching {
                        contentResolver.openInputStream(u)!!.use { input ->
                            ModelPack.extract(input, dest = { File(modelsDir(), it.relativePath) },
                                freeBytes = { StatFs(it.absolutePath).availableBytes }, progress = { n -> show(UiStrings.fmt(Ui.MI_WORKING_FMT, lang, n)) })
                        }
                    }.getOrDefault(emptyList())
                    if (results.none { it.slot != null }) lines.append("• ${name ?: "?"}: ").append(UiStrings.get(Ui.MI_NOT_PACK, lang)).append('\n')
                    for (r in results) if (r.slot != null) lines.append("• ${r.entry}: ").append(UiStrings.get(text(r.outcome), lang)).append('\n')
                } else {
                    val outcome = importOne(u, name, size) { show(UiStrings.fmt(Ui.MI_WORKING_FMT, lang, name ?: "?")) }
                    lines.append("• ${name ?: "?"}: ").append(UiStrings.get(text(outcome), lang)).append('\n')
                }
                show()
            }
            runOnUiThread { build(lines.toString() + "\n" + UiStrings.get(Ui.MI_RESTART, lang)) }
        }.start()
    }

    private fun text(o: ImportOutcome) = when (o) {
        ImportOutcome.IMPORTED_VERIFIED -> Ui.MI_OK_VERIFIED
        ImportOutcome.IMPORTED_UNVERIFIED -> Ui.MI_OK_UNVERIFIED
        ImportOutcome.NOT_A_MODEL -> Ui.MI_NOT_MODEL
        ImportOutcome.WRONG_SIZE -> Ui.MI_WRONG_SIZE
        ImportOutcome.NO_SPACE -> Ui.MI_NO_SPACE
        ImportOutcome.FAILED -> Ui.MI_FAILED
    }

    private fun describe(u: Uri): Pair<String?, Long> = runCatching {
        contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getLong(1) else null
        }
    }.getOrNull() ?: (u.lastPathSegment?.substringAfterLast('/') to (if (u.scheme == "file") runCatching { File(u.path!!).length() }.getOrDefault(-1L) else -1L))

    private fun importOne(u: Uri, name: String?, size: Long, progress: () -> Unit): ImportOutcome {
        val slot = ModelImport.classify(name) ?: return ImportOutcome.NOT_A_MODEL
        if (size > 0 && !ModelImport.sizeOk(slot, size)) return ImportOutcome.WRONG_SIZE
        val dest = File(modelsDir(), slot.relativePath).also { it.parentFile?.mkdirs() }
        if (size > 0 && !ModelImport.hasSpace(StatFs(dest.parentFile!!.absolutePath).availableBytes, size)) return ImportOutcome.NO_SPACE
        val tmp = File(dest.parentFile, dest.name + ".part")
        return try {
            progress()
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            contentResolver.openInputStream(u)!!.use { input -> tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); md.update(buf, 0, n); total += n }
            } }
            if (!ModelImport.sizeOk(slot, total)) { tmp.delete(); return ImportOutcome.WRONG_SIZE }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) { tmp.delete(); return ImportOutcome.FAILED }
            ModelImport.outcomeFor(slot, ModelImport.hex(md.digest()))
        } catch (t: Throwable) { tmp.delete(); ImportOutcome.FAILED }
    }

    private companion object { const val REQ_PICK = 1 }
}
