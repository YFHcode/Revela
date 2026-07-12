package com.revela.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.revela.app.AppContainer
import com.revela.capture.CaptureScheduler
import com.revela.insights.InsightsScheduler
import com.revela.pipeline.RollupScheduler
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenManageData: () -> Unit,
) {
    val context = LocalContext.current
    val settings = container.settings
    var captureEnabled by remember { mutableStateOf(settings.captureEnabled) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var llmEnabled by remember { mutableStateOf(container.llmConfig.enabled) }
    var hasKey by remember { mutableStateOf(!container.llmConfig.apiKey.isNullOrBlank()) }
    var keyInput by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentModel by remember { mutableStateOf(container.llmConfig.model) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun testKeyAndLoadModels() {
        testing = true
        testResult = null
        scope.launch {
            val found = container.llmGateway.listModels()
            testResult = if (found != null) {
                models = found
                "Key works — ${found.size} usable models available."
            } else {
                "Couldn't verify the key. Check it (and your connection) and try again."
            }
            testing = false
        }
    }

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
                            InsightsScheduler.ensureScheduled(context)
                        } else {
                            CaptureScheduler.cancel(context)
                            RollupScheduler.cancel(context)
                            InsightsScheduler.cancel(context)
                        }
                    },
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Signals", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add notification-timing, location, and calendar sources to " +
                        "unlock relationship and place insights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenSources, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage sources")
                }
                OutlinedButton(onClick = onOpenManageData, modifier = Modifier.fillMaxWidth()) {
                    Text("Places & contacts")
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI narration & chat", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Optional. With your own OpenAI API key, insights get richer " +
                        "wording and you can ask questions about your patterns. Only " +
                        "aggregated numbers ever leave the device — see the audit " +
                        "log below. Without a key, everything still works on " +
                        "built-in wording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasKey) {
                    SettingSwitch(
                        title = "Use AI features",
                        description = "Off reverts to built-in wording instantly.",
                        checked = llmEnabled,
                        onCheckedChange = {
                            llmEnabled = it
                            container.llmConfig.enabled = it
                        },
                    )
                    Button(
                        onClick = ::testKeyAndLoadModels,
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (testing) "Testing…" else "Test key & load models")
                    }
                    testResult?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        OutlinedButton(
                            onClick = {
                                if (models.isEmpty()) testKeyAndLoadModels()
                                modelMenuOpen = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Model: $currentModel")
                        }
                        DropdownMenu(
                            expanded = modelMenuOpen && models.isNotEmpty(),
                            onDismissRequest = { modelMenuOpen = false },
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        currentModel = model
                                        container.llmConfig.model = model
                                        modelMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            container.llmConfig.apiKey = null
                            hasKey = false
                            models = emptyList()
                            testResult = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove API key")
                    }
                } else {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("OpenAI API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            container.llmConfig.apiKey = keyInput.trim()
                            keyInput = ""
                            hasKey = true
                            testKeyAndLoadModels()
                        },
                        enabled = keyInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save key")
                    }
                }
                TextButton(onClick = onOpenAudit) {
                    Text("What left the device →")
                }
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
