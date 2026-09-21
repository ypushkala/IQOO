package com.callguard.debug

import android.content.Context

/** Release builds have no replay tool: the real one lives only in the debug source set. */
object DevReplay {
    @Suppress("UNUSED_PARAMETER") fun available(ctx: Context) = false
    @Suppress("UNUSED_PARAMETER") fun start(ctx: Context, resetSession: () -> Unit, endSession: () -> String, feed: (ShortArray) -> Unit, waitReady: () -> Unit, summary: () -> String) = Unit
}
