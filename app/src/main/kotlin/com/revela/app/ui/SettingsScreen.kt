package com.revela.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.revela.app.AppContainer
import com.revela.capture.CaptureScheduler
import com.revela.pipeline.RollupScheduler

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = container.settings
    var captureEnabled by remember { mutableStateOf(settings.captureEnabled) }
    var devMode by remember { mutableStateOf(settings.devMode) }
    var showWipeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingSwitch(
                    title = "Observation",
                    description = "Pause to stop collecting entirely. Nothing is recorded while paused.",
                    checked = captureEnabled,
                    onCheckedChange = { enabled ->
                        captureEnabled = enabled
                        settings.captureEnabled = enabled
                        if (enabled) {
                            CaptureScheduler.ensureScheduled(context)
                            RollupScheduler.ensureScheduled(context)
                        } else {
                            CaptureScheduler.cancel(context)
                            RollupScheduler.cancel(context)
                        }
                    },
                )
                SettingSwitch(
                    title = "Developer mode",
                    description = "Bypasses the quiet-observation window so screens show " +
                        "immediately. Baselines may be less honest while enabled.",
                    checked = devMode,
                    onCheckedChange = {
                        devMode = it
                        settings.devMode = it
                    },
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Delete everything", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Erases the entire event history, all derived data, and the " +
                        "encryption key, then restarts the app as a fresh install. " +
                        "This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showWipeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Wipe all data", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe all data?") },
            text = {
                Text(
                    "Every observed event and everything derived from it will be " +
                        "permanently deleted from this device.",
                )
            },
            confirmButton = {
                Button(onClick = { container.fullWipeAndRestart() }) {
                    Text("Delete everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
