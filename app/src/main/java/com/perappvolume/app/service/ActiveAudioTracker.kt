package com.perappvolume.app.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Combines two signals into one "who is making sound / who should the next
 * volume-key press apply to" answer:
 *
 *  1. MediaSessionManager.getActiveSessions() — primary source of truth for
 *     background/overlay audio (Spotify, YouTube backgrounded, a Messenger
 *     voice note playing under Reels). Requires an enabled
 *     NotificationListenerService component, which is why this class is fed
 *     by [MediaSessionListenerService] rather than calling getActiveSessions()
 *     directly from an arbitrary context.
 *
 *  2. Foreground app package, reported by [VolumeAccessibilityService] via
 *     TYPE_WINDOW_STATE_CHANGED — the fallback for apps that don't register a
 *     MediaSession at all (games, browsers, most non-media apps).
 *
 * Priority rule (spec section 1): if more than one package is actively
 * PLAYING right now, the most-recently-changed-to-PLAYING package "wins" the
 * global stream (true simultaneous independent per-app volume isn't possible
 * on stock, non-rooted Android — this is the best available approximation).
 * Every known package's own last volume/mute level is still tracked
 * independently in Room regardless of which one currently owns the stream.
 */
object ActiveAudioTracker {

    data class AudioSource(
        val packageName: String,
        val isPlaying: Boolean,
        val lastActiveEpochMs: Long
    )

    private const val TAG = "ActiveAudioTracker"

    private val _activeSources = MutableStateFlow<Map<String, AudioSource>>(emptyMap())
    val activeSources: StateFlow<Map<String, AudioSource>> = _activeSources.asStateFlow()

    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    /** The package the next volume-key press / overlay slider should target. */
    val targetPackage: StateFlow<String?> = _activeSources.let { flow ->
        // Recomputed on demand in resolveTargetPackage(); exposed as a snapshot
        // here mainly for callers that want the StateFlow reference shape.
        MutableStateFlow(resolveTargetPackage()).asStateFlow()
    }

    fun resolveTargetPackage(): String? {
        val playing = _activeSources.value.values.filter { it.isPlaying }
        return when {
            playing.isNotEmpty() -> playing.maxByOrNull { it.lastActiveEpochMs }?.packageName
            else -> _foregroundPackage.value
        }
    }

    fun onForegroundPackageChanged(packageName: String) {
        _foregroundPackage.value = packageName
    }

    /**
     * Called by MediaSessionListenerService whenever its onNotificationPosted /
     * onActiveSessionsChanged callback fires. We deliberately read only
     * session metadata (package name + PlaybackState), never notification text.
     */
    fun updateFromControllers(controllers: List<MediaController>?) {
        if (controllers == null) return
        val now = System.currentTimeMillis()
        val updated = _activeSources.value.toMutableMap()

        val seenPackages = mutableSetOf<String>()
        for (controller in controllers) {
            val pkg = controller.packageName ?: continue
            seenPackages += pkg
            val state = controller.playbackState?.state
            val isPlaying = state == PlaybackState.STATE_PLAYING
            val previous = updated[pkg]
            updated[pkg] = AudioSource(
                packageName = pkg,
                isPlaying = isPlaying,
                lastActiveEpochMs = if (isPlaying) now else (previous?.lastActiveEpochMs ?: now)
            )
        }

        // Mark sessions that disappeared from the active list as no longer playing,
        // but keep their last-known package around so per-app volume memory persists.
        updated.keys.filter { it !in seenPackages }.forEach { pkg ->
            updated[pkg] = updated[pkg]?.copy(isPlaying = false) ?: return@forEach
        }

        _activeSources.value = updated
    }

    fun getActiveSessionsSafely(
        context: Context,
        notificationListenerComponent: ComponentName
    ): List<MediaController> {
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.getActiveSessions(notificationListenerComponent)
        } catch (e: SecurityException) {
            // NotificationListenerService not (yet) enabled by the user.
            Log.w(TAG, "getActiveSessions denied — notification listener not enabled", e)
            emptyList()
        }
    }
}
