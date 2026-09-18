package com.perappvolume.app.model

import android.media.AudioManager

enum class FloatingButtonShape { CIRCLE, ROUNDED_SQUARE, SQUARE }
enum class EdgeSide { LEFT, RIGHT }

/**
 * Two independent on-screen controls, each toggled on/off separately:
 *
 *  - Edge pull-tab: a small, mostly-transparent tab that sits at the
 *    screen edge. Dragging it inward past a threshold opens the existing
 *    volume overlay panel, then it springs back to the edge.
 *
 *  - Persistent button: a floating button that stays on screen all the
 *    time. Pressing and swiping up/down on it directly raises/lowers the
 *    volume of one chosen stream (e.g. media) without opening the full
 *    panel. Dragging it sideways instead repositions it. A plain tap opens
 *    the full overlay panel.
 */
data class FloatingButtonConfig(
    val edgeTabEnabled: Boolean = false,
    val edgeTabSide: EdgeSide = EdgeSide.RIGHT,
    val edgeTabVerticalPercent: Float = 0.5f,   // 0f (top) .. 1f (bottom)
    val edgeTabSizeDp: Int = 28,
    val edgeTabOpacityPercent: Int = 40,
    val edgeTabPullThresholdDp: Int = 56,       // how far it must be dragged inward to trigger

    val persistentButtonEnabled: Boolean = false,
    val persistentShape: FloatingButtonShape = FloatingButtonShape.CIRCLE,
    val persistentSizeDp: Int = 56,
    val persistentOpacityPercent: Int = 70,
    val persistentPositionX: Int = 40,
    val persistentPositionY: Int = 600,
    val persistentTargetStream: Int = AudioManager.STREAM_MUSIC,
    val persistentPxPerVolumeStep: Int = 40     // swipe distance (px) per one volume notch
)

fun streamLabel(stream: Int): String = when (stream) {
    AudioManager.STREAM_MUSIC -> "Media"
    AudioManager.STREAM_RING -> "Ringtone"
    AudioManager.STREAM_ALARM -> "Alarm"
    AudioManager.STREAM_NOTIFICATION -> "Notification"
    AudioManager.STREAM_VOICE_CALL -> "Call"
    AudioManager.STREAM_SYSTEM -> "System"
    else -> "Stream $stream"
}
