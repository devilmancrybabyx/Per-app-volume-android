package com.perappvolume.app.service

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Required solely to unlock MediaSessionManager.getActiveSessions() (spec
 * section 3): Android requires an enabled NotificationListenerService
 * component before that API will return anything, system-wide, for
 * background/overlay audio.
 *
 * IMPORTANT (spec section 5): this class never reads sbn.notification content
 * (title, text, extras). It only uses the fact that the listener is enabled
 * to query active MediaSessions, which expose package name + playback state,
 * not notification text.
 */
class MediaSessionListenerService : NotificationListenerService() {

    private lateinit var sessionManager: MediaSessionManager
    private lateinit var componentName: ComponentName

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        ActiveAudioTracker.updateFromControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        componentName = ComponentName(this, MediaSessionListenerService::class.java)
        sessionManager = getSystemService(MediaSessionManager::class.java)
        sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
        // Prime the tracker with whatever is already active.
        ActiveAudioTracker.updateFromControllers(
            ActiveAudioTracker.getActiveSessionsSafely(this, componentName)
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
    }

    // Intentionally not overridden meaningfully: we never inspect notification
    // content, only whether the listener is alive (needed for getActiveSessions()).
    override fun onNotificationPosted(sbn: StatusBarNotification) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
