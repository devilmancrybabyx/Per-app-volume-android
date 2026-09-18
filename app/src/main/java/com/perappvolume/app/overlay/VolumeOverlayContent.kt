package com.perappvolume.app.overlay

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.perappvolume.app.model.OverlayConfig
import com.perappvolume.app.model.OverlayTheme
import com.perappvolume.app.service.ActiveAudioTracker

/**
 * The custom panel shown instead of the system volume UI (spec section 2):
 * a primary slider for the currently-active stream, plus an expandable list
 * of every app with an active audio session, each with its own mini slider
 * and mute toggle.
 */
@Composable
fun VolumeOverlayContent(
    config: OverlayConfig,
    activePackage: String?,
    audioManager: AudioManager,
    activeSourcesProvider: () -> Map<String, ActiveAudioTracker.AudioSource>,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val backgroundColor = when (config.theme) {
        OverlayTheme.LIGHT -> Color.White.copy(alpha = config.opacityPercent / 100f)
        OverlayTheme.DARK -> Color(0xFF1E1E1E).copy(alpha = config.opacityPercent / 100f)
        OverlayTheme.CUSTOM -> config.customColorArgb?.let { Color(it) }
            ?: Color(0xFF1E1E1E).copy(alpha = config.opacityPercent / 100f)
    }
    val contentColor = if (config.theme == OverlayTheme.LIGHT) Color.Black else Color.White

    val streamMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    var streamLevel by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Text(
            text = activePackage ?: "Media volume",
            color = contentColor,
            style = MaterialTheme.typography.labelLarge
        )

        Slider(
            value = streamLevel,
            valueRange = 0f..streamMax,
            onValueChange = {
                streamLevel = it
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (expanded) "Hide apps ▲" else "Other apps ▼",
                color = contentColor,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (expanded) {
            val sources = activeSourcesProvider()
            Column(modifier = Modifier.padding(top = 8.dp)) {
                sources.values.forEach { source ->
                    AppMiniSlider(
                        packageName = source.packageName,
                        isPlaying = source.isPlaying,
                        contentColor = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun AppMiniSlider(packageName: String, isPlaying: Boolean, contentColor: Color) {
    var muted by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0.5f) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.7f)) {
            Text(text = packageName, color = contentColor, style = MaterialTheme.typography.bodySmall)
            Slider(value = level, onValueChange = { level = it })
        }
        IconButton(onClick = { muted = !muted }) {
            // A real build should swap in a mute/unmute vector icon here.
            Text(if (muted) "🔇" else "🔊", color = contentColor)
        }
    }
}
