package com.callguard.caller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

/** On-device contacts check. Returns null when it cannot know (no number or no READ_CONTACTS permission). */
object ContactLookup {
    fun isInContacts(context: Context, number: String?): Boolean? {
        if (number.isNullOrBlank()) return null
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 }
        }.getOrNull()
    }
}
