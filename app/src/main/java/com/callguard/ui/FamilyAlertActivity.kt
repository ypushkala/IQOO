package com.callguard.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.LinearLayout
import android.widget.ScrollView
import com.callguard.alert.AppPrefs
import com.callguard.core.AlertLogEntry
import com.callguard.core.AutoFamilyAlert
import com.callguard.core.FamilyContact
import com.callguard.core.FamilyContacts
import com.callguard.core.Ui
import com.callguard.core.UiStrings
import java.text.DateFormat
import java.util.Date

/**
 * Consent screen for family alerts. It shows exactly what would be sent (the real message text, with an example caller) and
 * when. Automatic sending is OFF until the user taps the agree button here and grants the SMS permission.
 */
class FamilyAlertActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var theme: UiTheme
    private lateinit var root: LinearLayout
    private val lang get() = prefs.screenLanguage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        theme = UiTheme(this, prefs.accessibility)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(32, 48, 32, 32) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        build()
    }

    private fun hasSms() = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    private val automaticOn get() = prefs.autoFamilyAlert && hasSms()

    private fun build() {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.FA_TITLE, lang); setTextColor(theme.accent) })
        val contacts = prefs.familyContacts
        for (c in contacts) root.addView(theme.button("${c.label}  ✕  (${UiStrings.get(Ui.FA_REMOVE, lang)})") { prefs.removeFamily(c); if (prefs.familyContacts.isEmpty()) prefs.autoFamilyAlert = false; build() })
        if (contacts.size < FamilyContacts.MAX) root.addView(theme.button(UiStrings.get(if (contacts.isEmpty()) Ui.FAMILY_CHOOSE else Ui.FA_ADD_ANOTHER, lang)) {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_PICK)
        })
        root.addView(theme.text(15f).apply { text = UiStrings.get(Ui.FA_SENDS, lang) + "\n\n" + UiStrings.get(Ui.FA_NEVER, lang) + "\n\n" + UiStrings.get(Ui.FA_WHEN, lang) })
        val onOff = UiStrings.get(if (prefs.includeCallerInAlert) Ui.OPT_YES else Ui.OPT_NO, lang)
        root.addView(theme.button(UiStrings.fmt(Ui.FA_CALLER, lang, onOff)) { prefs.includeCallerInAlert = !prefs.includeCallerInAlert; build() })
        root.addView(theme.text(15f, bold = true).apply { text = UiStrings.get(Ui.FA_SAMPLE, lang); setPadding(0, 16, 0, 0) })
        root.addView(theme.text(15f).apply { text = AutoFamilyAlert.sampleMessage(prefs.familyMessageLanguage, prefs.includeCallerInAlert); background = theme.cardDrawable(); setPadding(20, 20, 20, 20) })

        if (automaticOn) {
            root.addView(theme.button(UiStrings.get(Ui.FA_AUTO_OFF, lang)) { prefs.autoFamilyAlert = false; build() })
        } else {
            root.addView(theme.button(UiStrings.get(Ui.FA_AUTO_ON, lang)) { agree() })
        }
        root.addView(theme.button(UiStrings.get(Ui.FA_TEST, lang)) { testMessage() })

        root.addView(theme.text(17f, bold = true).apply { text = UiStrings.get(Ui.FA_LOG, lang); setPadding(0, 24, 0, 0) })
        val log = AlertLogEntry.parseAll(prefs.familyAlertLog).reversed()
        root.addView(theme.text(14f).apply {
            text = if (log.isEmpty()) UiStrings.get(Ui.FA_LOG_EMPTY, lang) else log.joinToString("\n") {
                val o = when (it.outcome) { AlertLogEntry.Outcome.SENT -> Ui.FA_LOG_SENT; AlertLogEntry.Outcome.FAILED -> Ui.FA_LOG_FAILED; AlertLogEntry.Outcome.TEST -> Ui.FA_LOG_TEST }
                "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.atEpochMs))}  ${it.contactName}: ${UiStrings.get(o, lang)}"
            }
        })
        root.addView(theme.button(UiStrings.get(Ui.DONE, lang)) { finish() })
    }

    private fun agree() {
        if (prefs.familyContacts.isEmpty()) { toast(Ui.FA_NEED_CONTACT); return }
        prefs.autoFamilyAlert = true // consent recorded; only effective once the SMS permission is granted
        if (hasSms()) build() else requestPermissions(arrayOf(Manifest.permission.SEND_SMS), REQ_SMS)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (code == REQ_SMS && !hasSms()) prefs.autoFamilyAlert = false // declined: stay off
        build()
    }

    private fun testMessage() {
        val contacts = prefs.familyContacts
        if (contacts.isEmpty()) { toast(Ui.FA_NEED_CONTACT); return }
        if (contacts.size == 1) sendTest(contacts[0]) else android.app.AlertDialog.Builder(this).setTitle(UiStrings.get(Ui.FA_TEST, lang))
            .setItems(contacts.map { it.label }.toTypedArray()) { _, i -> sendTest(contacts[i]) }.show()
    }

    private fun sendTest(c: FamilyContact) {
        val body = AutoFamilyAlert.sampleMessage(prefs.familyMessageLanguage, prefs.includeCallerInAlert)
        prefs.familyAlertLog = AlertLogEntry.append(prefs.familyAlertLog, AlertLogEntry(System.currentTimeMillis(), AlertLogEntry.Outcome.TEST, c.label))
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.number))).putExtra("sms_body", body))
    }

    private fun toast(k: Ui) = android.widget.Toast.makeText(this, UiStrings.get(k, lang), android.widget.Toast.LENGTH_LONG).show()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) prefs.addFamily(c.getString(1), c.getString(0))
            }
        }
        build()
    }

    private companion object { const val REQ_PICK = 1; const val REQ_SMS = 2 }
}
