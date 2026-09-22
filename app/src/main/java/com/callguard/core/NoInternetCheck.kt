package com.callguard.core

/**
 * The "zero internet" claim, made checkable instead of just stated: true only if none of the app's own declared permissions
 * are a network permission. The caller reads the real list from PackageManager; this just judges it.
 */
object NoInternetCheck {
    private val networkPermissions = setOf(
        "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE", "android.permission.CHANGE_NETWORK_STATE",
    )
    fun verified(declaredPermissions: List<String>): Boolean = declaredPermissions.none { it in networkPermissions }
}
