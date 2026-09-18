package com.perappvolume.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.perappvolume.app.util.OemUtils

private data class OnboardingStep(
    val title: String,
    val explanation: String,
    val action: (android.content.Context) -> Unit
)

private val steps = listOf(
    OnboardingStep(
        title = "Draw over other apps",
        explanation = "Needed to show the custom volume panel on top of whatever app you're using, instead of the plain system one."
    ) { context ->
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        )
    },
    OnboardingStep(
        title = "Accessibility service",
        explanation = "Lets the app notice which app you're currently using and catch volume-button presses so it can show its own panel instead of the system one. It never reads what's on your screen."
    ) { context ->
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    },
    OnboardingStep(
        title = "Media session access (notification access)",
        explanation = "Required by Android before it will tell any app what's currently playing audio in the background (Spotify, YouTube, etc.). Only playback state and the app's name are read — never notification text."
    ) { context ->
        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    },
    OnboardingStep(
        title = "Usage access",
        explanation = "Used to list recently-used apps in the volume panel's 'other apps' list, alongside apps that are actively playing audio."
    ) { context ->
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    },
    OnboardingStep(
        title = "Ignore battery optimization",
        explanation = "Some manufacturers (Xiaomi, Realme/ColorOS, OnePlus, Huawei) aggressively kill background services. This keeps the volume-memory service from being silently stopped."
    ) { context -> OemUtils.requestIgnoreBatteryOptimizations(context) },
    OnboardingStep(
        title = "Autostart / background permission",
        explanation = "On some manufacturers this is a separate toggle from battery optimization. We'll try to open it directly; if your device doesn't support the deep link, you'll land on the app's settings page instead."
    ) { context -> OemUtils.openOemAutostartSettings(context) }
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableStateOf(0) }
    val step = steps.getOrNull(index)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        LinearProgressIndicator(
            progress = { index.toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        if (step == null) {
            Text("All set.", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onFinished, modifier = Modifier.padding(top = 16.dp)) {
                Text("Continue to settings")
            }
        } else {
            Text(step.title, style = MaterialTheme.typography.headlineSmall)
            Text(step.explanation, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            Button(onClick = {
                step.action(context)
            }) {
                Text("Open settings")
            }
            Button(
                onClick = { index++ },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(if (index == steps.lastIndex) "Done" else "Next")
            }
        }
    }
}
