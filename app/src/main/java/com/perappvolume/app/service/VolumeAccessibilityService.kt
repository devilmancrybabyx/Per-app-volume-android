package com.perappvolume.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Two jobs (spec sections 1 & 2):
 *
 *  1. Foreground-app detection fallback for apps that never register a
 *     MediaSession (games, browsers): listens for TYPE_WINDOW_STATE_CHANGED
 *     and reports the package name to [ActiveAudioTracker].
 *
 *  2. Hardware volume key interception: onKeyEvent() is the only reliable,
 *     no-root way to intercept volume keys system-wide on modern Android.
 *     When interception is enabled in settings, we consume the key event and
 *     forward it to [OverlayService] to show the custom panel and adjust the
 *     resolved target app's volume, instead of letting the system panel show.
 */
class VolumeAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            ActiveAudioTracker.onForegroundPackageChanged(pkg)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (!isVolumeKey) return false
        if (event.action != KeyEvent.ACTION_DOWN) {
            // Consume the matching ACTION_UP too, since we already handled DOWN.
            return true
        }

        val interceptionEnabled = OverlayService.isOverrideCurrentlyEnabled()
        if (!interceptionEnabled) return false

        val direction = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_VOLUME_KEY
            putExtra(OverlayService.EXTRA_DIRECTION, direction)
        }
        startService(intent)
        return true // consume: prevents the stock system volume panel from appearing
    }

    override fun onInterrupt() {}
}
