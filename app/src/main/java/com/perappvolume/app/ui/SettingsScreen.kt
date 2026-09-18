package com.perappvolume.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perappvolume.app.data.SettingsRepository
import com.perappvolume.app.model.OperatingMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onOpenAppList: () -> Unit, onOpenDiagnostics: () -> Unit, onOpenFloatingButtons: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScopeCompat()

    val mode by repository.mode.collectAsStateWithLifecycle(initialValue = OperatingMode.LIVE)
    val overlayConfig by repository.overlayConfig.collectAsStateWithLifecycle(
        initialValue = com.perappvolume.app.model.OverlayConfig()
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Per-App Volume", style = MaterialTheme.typography.headlineMedium)

        Text("Operating mode", style = MaterialTheme.typography.titleMedium)
        Row3ModeSelector(current = mode, onSelect = { scope.launch { repository.setMode(it) } })

        Text("Overlay", style = MaterialTheme.typography.titleMedium)
        Row {
            Text("Enable custom overlay (off = stock Android volume panel)")
            Switch(
                checked = overlayConfig.overrideEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        repository.updateOverlayConfig { it.copy(overrideEnabled = enabled) }
                    }
                }
            )
        }

        Text("Auto-hide timeout: ${overlayConfig.autoHideTimeoutMs} ms")
        Slider(
            value = overlayConfig.autoHideTimeoutMs.toFloat(),
            valueRange = 1000f..6000f,
            onValueChange = { value ->
                scope.launch {
                    repository.updateOverlayConfig { it.copy(autoHideTimeoutMs = value.toLong()) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = onOpenAppList, modifier = Modifier.fillMaxWidth()) {
            Text("Per-app overrides")
        }
        Button(onClick = onOpenFloatingButtons, modifier = Modifier.fillMaxWidth()) {
            Text("Floating button")
        }
        Button(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Diagnostics")
        }
    }
}

@Composable
private fun Row3ModeSelector(current: OperatingMode, onSelect: (OperatingMode) -> Unit) {
    Row {
        OperatingMode.values().forEach { mode ->
            Button(
                onClick = { onSelect(mode) },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(if (mode == current) "[${mode.name}]" else mode.name)
            }
        }
    }
}

// Small local shim so this file doesn't need an extra import block reshuffle;
// forwards to rememberCoroutineScope().
@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

@Composable
private fun Row(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}
