package com.callguard.ui

import android.content.Context
import android.content.pm.PackageManager
import com.callguard.core.NoInternetCheck

/** Reads the app's own declared permissions and judges them with [NoInternetCheck], so "no internet" is checkable, not just claimed. */
object TrustCheck {
    fun noInternetVerified(ctx: Context): Boolean = runCatching {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        NoInternetCheck.verified(pkg.requestedPermissions?.toList() ?: emptyList())
    }.getOrDefault(false) // unknown counts as "not verified", never as a false claim of safety
}
