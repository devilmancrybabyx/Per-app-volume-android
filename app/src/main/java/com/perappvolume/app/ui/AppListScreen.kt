package com.perappvolume.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.perappvolume.app.PerAppVolumeApp
import kotlinx.coroutines.launch

@Composable
fun AppListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PerAppVolumeApp
    val dao = remember { app.database.appVolumeDao() }
    val scope = rememberCoroutineScope()

    val entries by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Button(onClick = onBack) { Text("← Back") }
        Text("Per-app overrides", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 16.dp))

        if (entries.isEmpty()) {
            Text("No apps have been adjusted yet. Volumes you change will appear here.")
        }

        LazyColumn {
            items(entries) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(entry.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text("Saved level: ${entry.volumeLevel}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = entry.overrideEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { dao.update(entry.copy(overrideEnabled = enabled)) }
                        }
                    )
                }
            }
        }
    }
}
