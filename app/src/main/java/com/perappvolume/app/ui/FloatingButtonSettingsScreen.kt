package com.perappvolume.app.ui

import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perappvolume.app.data.SettingsRepository
import com.perappvolume.app.model.EdgeSide
import com.perappvolume.app.model.FloatingButtonConfig
import com.perappvolume.app.model.FloatingButtonShape
import com.perappvolume.app.model.streamLabel
import kotlinx.coroutines.launch

private val selectableStreams = listOf(
    AudioManager.STREAM_MUSIC,
    AudioManager.STREAM_RING,
    AudioManager.STREAM_NOTIFICATION,
    AudioManager.STREAM_ALARM,
    AudioManager.STREAM_VOICE_CALL
)

@Composable
fun FloatingButtonSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    val config by repository.floatingButtonConfig.collectAsStateWithLifecycle(initialValue = FloatingButtonConfig())

    fun update(transform: (FloatingButtonConfig) -> FloatingButtonConfig) {
        scope.launch { repository.updateFloatingButtonConfig(transform) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) { Text("← Back") }
        Text("Floating button", style = MaterialTheme.typography.headlineSmall)

        // --- Mode 1: edge pull-tab ---------------------------------------
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Edge pull-tab", style = MaterialTheme.typography.titleMedium)
        Text(
            "A small tab at the screen edge. Drag it toward the middle of the screen to open the volume overlay.",
            style = MaterialTheme.typography.bodySmall
        )
        LabeledSwitch(
            label = "Enabled",
            checked = config.edgeTabEnabled,
            onCheckedChange = { enabled -> update { it.copy(edgeTabEnabled = enabled) } }
        )

        if (config.edgeTabEnabled) {
            LabeledSwitch(
                label = "Side: ${if (config.edgeTabSide == EdgeSide.LEFT) "Left" else "Right"} (tap to flip)",
                checked = config.edgeTabSide == EdgeSide.RIGHT,
                onCheckedChange = { checked ->
                    update { it.copy(edgeTabSide = if (checked) EdgeSide.RIGHT else EdgeSide.LEFT) }
                }
            )

            LabeledSlider(
                label = "Vertical position: ${(config.edgeTabVerticalPercent * 100).toInt()}%",
                value = config.edgeTabVerticalPercent,
                range = 0f..1f,
                onValueChange = { v -> update { it.copy(edgeTabVerticalPercent = v) } }
            )
            LabeledSlider(
                label = "Size: ${config.edgeTabSizeDp} dp",
                value = config.edgeTabSizeDp.toFloat(),
                range = 16f..48f,
                onValueChange = { v -> update { it.copy(edgeTabSizeDp = v.toInt()) } }
            )
            LabeledSlider(
                label = "Transparency: ${config.edgeTabOpacityPercent}% opaque",
                value = config.edgeTabOpacityPercent.toFloat(),
                range = 10f..100f,
                onValueChange = { v -> update { it.copy(edgeTabOpacityPercent = v.toInt()) } }
            )
            LabeledSlider(
                label = "Pull distance to trigger: ${config.edgeTabPullThresholdDp} dp",
                value = config.edgeTabPullThresholdDp.toFloat(),
                range = 24f..120f,
                onValueChange = { v -> update { it.copy(edgeTabPullThresholdDp = v.toInt()) } }
            )
        }

        // --- Mode 2: persistent button ------------------------------------
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Persistent button", style = MaterialTheme.typography.titleMedium)
        Text(
            "Always on screen. Press and swipe up/down on it to raise or lower one chosen volume directly. " +
                "Press and drag sideways to move it. A plain tap opens the full overlay.",
            style = MaterialTheme.typography.bodySmall
        )
        LabeledSwitch(
            label = "Enabled",
            checked = config.persistentButtonEnabled,
            onCheckedChange = { enabled -> update { it.copy(persistentButtonEnabled = enabled) } }
        )

        if (config.persistentButtonEnabled) {
            Text("Shape", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingButtonShape.values().forEach { shape ->
                    Button(onClick = { update { it.copy(persistentShape = shape) } }) {
                        Text(if (shape == config.persistentShape) "[${shape.name}]" else shape.name)
                    }
                }
            }

            LabeledSlider(
                label = "Size: ${config.persistentSizeDp} dp",
                value = config.persistentSizeDp.toFloat(),
                range = 36f..96f,
                onValueChange = { v -> update { it.copy(persistentSizeDp = v.toInt()) } }
            )
            LabeledSlider(
                label = "Transparency: ${config.persistentOpacityPercent}% opaque",
                value = config.persistentOpacityPercent.toFloat(),
                range = 10f..100f,
                onValueChange = { v -> update { it.copy(persistentOpacityPercent = v.toInt()) } }
            )
            LabeledSlider(
                label = "Swipe sensitivity: ${config.persistentPxPerVolumeStep} px per step (lower = more sensitive)",
                value = config.persistentPxPerVolumeStep.toFloat(),
                range = 12f..100f,
                onValueChange = { v -> update { it.copy(persistentPxPerVolumeStep = v.toInt()) } }
            )

            Text("Controls this volume:", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selectableStreams.forEach { stream ->
                    Button(onClick = { update { it.copy(persistentTargetStream = stream) } }) {
                        val label = streamLabel(stream)
                        Text(if (stream == config.persistentTargetStream) "[$label]" else label)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, valueRange = range, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
    }
}
