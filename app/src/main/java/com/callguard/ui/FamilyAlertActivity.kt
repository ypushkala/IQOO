package com.callguard.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
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
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(theme.bg); setPadding(theme.dp(32), theme.dp(48), theme.dp(32), theme.dp(32)) }.also { theme.avoidStatusBar(it) }
        setContentView(ScrollView(this).apply { setBackgroundColor(theme.bg); addView(root) })
        build()
    }

    private fun hasSms() = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    private val automaticOn get() = prefs.autoFamilyAlert && hasSms()

    /** A chosen family contact: name, and a quiet "remove" tap — not a full-width button per contact. */
    private fun contactRow(c: FamilyContact) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = theme.dp(48)
        setPadding(theme.dp(4), theme.dp(8), theme.dp(4), theme.dp(8))
        addView(theme.text(15f).apply { text = c.label; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setPadding(0, 0, 0, 0) })
        addView(theme.text(13.5f, muted = true).apply {
            text = UiStrings.get(Ui.FA_REMOVE, lang)
            setPadding(theme.dp(12), 0, 0, 0)
            isClickable = true; isFocusable = true; foreground = theme.rowRipple()
            setOnClickListener { prefs.removeFamily(c); if (prefs.familyContacts.isEmpty()) prefs.autoFamilyAlert = false; build() }
        })
    }

    private fun build() {
        root.removeAllViews()
        root.addView(theme.text(22f, bold = true).apply { text = UiStrings.get(Ui.FA_TITLE, lang); setTextColor(theme.fg) })

        // Selected contacts: who an alert would go to, and where to add or remove one.
        val contacts = prefs.familyContacts
        Rows.section(this, theme, root, UiStrings.get(Ui.FA_SEC_CONTACTS, lang), buildList {
            contacts.forEach { add(contactRow(it)) }
            if (contacts.size < FamilyContacts.MAX) add(Rows.navRow(this@FamilyAlertActivity, theme, UiStrings.get(if (contacts.isEmpty()) Ui.FAMILY_CHOOSE else Ui.FA_ADD_ANOTHER, lang)) {
                startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_PICK)
            })
        })

        // When it sends: the automatic-alert switch is itself the consent action (same behaviour as before —
        // turning it on still requires SEND_SMS permission via agree()); the rule it follows sits underneath as its description.
        root.addView(Rows.sectionHeader(this, theme, UiStrings.get(Ui.FA_SEC_TRIGGER, lang)))
        root.addView(Rows.switchRow(this, theme, UiStrings.get(Ui.FA_AUTO_LABEL, lang), UiStrings.get(Ui.FA_WHEN, lang), checked = automaticOn) { on ->
            if (on) agree() else { prefs.autoFamilyAlert = false; build() }
        })

        // What's shared / never shared, and the exact message so there's no guessing.
        root.addView(Rows.sectionHeader(this, theme, UiStrings.get(Ui.FA_SEC_SHARED, lang)))
        root.addView(theme.text(14f, muted = true).apply { text = UiStrings.get(Ui.FA_SENDS, lang) })
        root.addView(Rows.switchRow(this, theme, UiStrings.get(Ui.FA_CALLER_LABEL, lang), checked = prefs.includeCallerInAlert) { prefs.includeCallerInAlert = it; build() })
        root.addView(theme.text(13f, bold = true, muted = true).apply { text = UiStrings.get(Ui.FA_SAMPLE, lang); setPadding(theme.dp(4), theme.dp(12), theme.dp(4), theme.dp(4)) })
        root.addView(theme.text(14f).apply { text = AutoFamilyAlert.sampleMessage(prefs.familyMessageLanguage, prefs.includeCallerInAlert); background = theme.cardDrawable(); setPadding(theme.dp(16), theme.dp(16), theme.dp(16), theme.dp(16)) })

        root.addView(Rows.sectionHeader(this, theme, UiStrings.get(Ui.FA_SEC_NOT_SHARED, lang)))
        root.addView(theme.text(14f, muted = true).apply { text = UiStrings.get(Ui.FA_NEVER, lang) })

        root.addView(theme.button(UiStrings.get(Ui.FA_TEST, lang)) { testMessage() })

        root.addView(Rows.sectionHeader(this, theme, UiStrings.get(Ui.FA_LOG, lang)))
        val log = AlertLogEntry.parseAll(prefs.familyAlertLog).reversed()
        if (log.isEmpty()) {
            root.addView(theme.text(14f, muted = true).apply { text = UiStrings.get(Ui.FA_LOG_EMPTY, lang) })
        } else {
            log.forEachIndexed { i, e ->
                if (i > 0) root.addView(View(this).apply { setBackgroundColor(theme.cardBorder); layoutParams = LinearLayout.LayoutParams(-1, theme.dp(1)).also { it.marginStart = theme.dp(4) } })
                val o = when (e.outcome) { AlertLogEntry.Outcome.SENT -> Ui.FA_LOG_SENT; AlertLogEntry.Outcome.FAILED -> Ui.FA_LOG_FAILED; AlertLogEntry.Outcome.TEST -> Ui.FA_LOG_TEST }
                val dateTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.atEpochMs))
                root.addView(Rows.historyRow(this, theme, theme.fgMuted, dateTime, UiStrings.get(o, lang), e.contactName))
            }
        }
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
