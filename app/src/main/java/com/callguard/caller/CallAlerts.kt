package com.callguard.caller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.callguard.CallGuardService
import com.callguard.CallGuardState
import com.callguard.alert.AppPrefs
import com.callguard.core.CallerAlertText
import com.callguard.core.Lang
import com.callguard.core.NumberAssessment
import com.callguard.ui.MainActivity

/**
 * Notifications that work with the app closed: a heads-up while a suspicious number is ringing, and a reminder
 * after a restart. Both carry a one-tap action that starts CallGuard. (Starting a microphone service straight
 * from the background or from boot is not allowed on Android 14/15+, but starting it from a notification action is.)
 */
object CallAlerts {
    private const val INCOMING_CHANNEL = "callguard_incoming"
    private const val BOOT_CHANNEL = "callguard_boot"
    private const val INCOMING_ID = 3
    private const val BOOT_ID = 4

    /** The pre-answer text is read on the screen, so it follows the screen language. */
    private fun lang(ctx: Context): Lang = AppPrefs(ctx).screenLanguage

    private fun startAction(ctx: Context) = PendingIntent.getForegroundService(
        ctx, 10, Intent(ctx, CallGuardService::class.java).setAction(CallGuardService.ACTION_START),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 11, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Called by [CallerIdService] while the phone is ringing. Silent (no notification) for normal numbers. */
    fun notifyIncoming(ctx: Context, a: NumberAssessment) {
        val l = lang(ctx)
        val monitoring = CallGuardState.state.monitoring
        val text = CallerAlertText.build(a, l, monitoring) ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(INCOMING_CHANNEL, "Incoming call warnings", NotificationManager.IMPORTANCE_HIGH))
        val public = Notification.Builder(ctx, INCOMING_CHANNEL).setContentTitle("CallGuard").setSmallIcon(android.R.drawable.ic_dialog_alert).build()
        val b = Notification.Builder(ctx, INCOMING_CHANNEL)
            .setContentTitle(text.title).setContentText(text.body).setStyle(Notification.BigTextStyle().bigText(text.body))
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentIntent(openApp(ctx)).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_CALL).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
        if (!monitoring) b.addAction(Notification.Action.Builder(null, CallerAlertText.actionLabel(l), startAction(ctx)).build())
        nm.notify(INCOMING_ID, b.build())
    }

    /** Called after a restart or an app update: protection is off until the user taps once. */
    fun notifyProtectionOff(ctx: Context) {
        val l = lang(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(BOOT_CHANNEL, "Protection reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val n = Notification.Builder(ctx, BOOT_CHANNEL)
            .setContentTitle(CallerAlertText.bootTitle(l)).setContentText(CallerAlertText.bootBody(l))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentIntent(startAction(ctx)).setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, CallerAlertText.actionLabel(l), startAction(ctx)).build())
            .build()
        nm.notify(BOOT_ID, n)
    }
}
