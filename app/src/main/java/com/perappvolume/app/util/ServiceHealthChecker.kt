package com.perappvolume.app.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.perappvolume.app.service.MediaSessionListenerService
import com.perappvolume.app.service.VolumeAccessibilityService

/**
 * Spec section 6: aggressive OEM battery managers (ColorOS, MIUI, OxygenOS)
 * are known to silently kill accessibility/notification-listener services.
 * The settings/diagnostics screen polls this to prompt the user to
 * re-enable a service that has been killed in the background.
 */
object ServiceHealthChecker {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = "${context.packageName}/${VolumeAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val expectedComponent = "${context.packageName}/${MediaSessionListenerService::class.java.name}"
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return !TextUtils.isEmpty(enabledListeners) &&
            enabledListeners.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun isOverlayPermissionGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
