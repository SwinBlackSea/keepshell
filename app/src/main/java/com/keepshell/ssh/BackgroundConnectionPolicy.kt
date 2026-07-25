package com.keepshell.ssh

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/**
 * Long-lived SSH sockets are frozen by some Android vendors unless the user
 * explicitly allows unrestricted background execution. Keep the check and the
 * system intent in one place so the connect flow and Settings screen behave the
 * same way.
 */
object BackgroundConnectionPolicy {
    fun requiresVendorBackgroundAccess(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        val brand = Build.BRAND.lowercase(Locale.US)
        return listOf(manufacturer, brand).any {
            it.contains("oppo") || it.contains("realme") || it.contains("oneplus")
        }
    }

    fun isUnrestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestUnrestricted(context: Context): Boolean {
        if (isUnrestricted(context)) return true
        return runCatching {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
