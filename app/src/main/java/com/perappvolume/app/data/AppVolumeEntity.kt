package com.perappvolume.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per package name: the last-known volume level(s) for that app, plus
 * per-app configuration (opt-out toggle, mute state).
 */
@Entity(tableName = "app_volume")
data class AppVolumeEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val streamType: Int,          // AudioManager.STREAM_* the app was last controlled through
    val volumeLevel: Int,         // last recorded index for that stream
    val isMuted: Boolean = false,
    val overrideEnabled: Boolean = true, // false = user opted this app out of volume memory
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)
