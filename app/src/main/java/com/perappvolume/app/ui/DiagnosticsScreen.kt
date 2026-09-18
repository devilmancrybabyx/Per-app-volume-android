package com.perappvolume.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.perappvolume.app.service.ActiveAudioTracker
import com.perappvolume.app.util.ServiceHealthChecker
import kotlinx.coroutines.delay

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Button(onClick = onBack) { Text("← Back") }
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 16.dp))

        Text("Service health", style = MaterialTheme.typography.titleMedium)
        HealthRow("Accessibility service", ServiceHealthChecker.isAccessibilityServiceEnabled(context))
        HealthRow("Notification / media session access", ServiceHealthChecker.isNotificationListenerEnabled(context))
        HealthRow("Overlay permission", ServiceHealthChecker.isOverlayPermissionGranted(context))
        HealthRow("Usage access", ServiceHealthChecker.isUsageAccessGranted(context))
        HealthRow("Battery optimization ignored", ServiceHealthChecker.isIgnoringBatteryOptimizations(context))

        Text(
            "Live MediaSession detections",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        // Reading tick forces recomposition every second to reflect the latest state.
        val sources = ActiveAudioTracker.activeSources.value
        val foreground = ActiveAudioTracker.foregroundPackage.value
        Text("Foreground app: ${foreground ?: "unknown"} (tick $tick)")
        if (sources.isEmpty()) {
            Text("No active media sessions detected right now.")
        } else {
            sources.values.forEach { source ->
                Text("${source.packageName} — playing=${source.isPlaying}")
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, healthy: Boolean) {
    Text("${if (healthy) "✅" else "⚠️"} $label")
}
