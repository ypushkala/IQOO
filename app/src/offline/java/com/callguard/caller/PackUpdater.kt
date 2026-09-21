package com.callguard.caller

import android.content.Context

/** The offline build has no network code at all: updating the scam-number list is not available. */
object PackUpdater {
    val available = false
    fun update(context: Context): UpdateResult = UpdateResult.NOT_AVAILABLE
}
