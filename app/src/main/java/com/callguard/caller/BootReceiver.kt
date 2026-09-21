package com.callguard.caller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a restart or app update CallGuard cannot start its microphone service on its own (Android 14/15 rule), so it asks for one tap. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) CallAlerts.notifyProtectionOff(context)
    }
}
