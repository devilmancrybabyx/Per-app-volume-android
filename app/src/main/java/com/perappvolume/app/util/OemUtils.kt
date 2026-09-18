package com.perappvolume.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Spec section 6: standard Android battery-optimization intents don't
 * reliably surface autostart/background-permission toggles on
 * Realme/ColorOS, Xiaomi/MIUI, Huawei, and OnePlus/OxygenOS. These deep
 * links are best-effort — OEMs change activity names across versions, so
 * every call is wrapped and falls back to the stock battery-optimization
 * settings screen when the OEM-specific intent isn't resolvable.
 */
object OemUtils {

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        safeStart(context, intent) {
            safeStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openOemAutostartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates = when {
            manufacturer.contains("xiaomi") -> listOf(
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            )
            manufacturer.contains("oneplus") -> listOf(
                componentIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
            manufacturer.contains("huawei") -> listOf(
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            )
            else -> emptyList()
        }

        for (intent in candidates) {
            if (safeStart(context, intent)) return
        }
        // Fallback: generic app details screen where the user can find battery/autostart settings manually.
        safeStart(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().apply { setClassName(pkg, cls) }

    private fun safeStart(context: Context, intent: Intent, onFailure: (() -> Unit)? = null): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            onFailure?.invoke()
            false
        } catch (e: SecurityException) {
            onFailure?.invoke()
            false
        }
    }
}
