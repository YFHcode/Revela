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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revela.app.AppContainer
import com.revela.capture.Permissions

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenDashboard: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenModes: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebugLog: () -> Unit,
) {
    val context = LocalContext.current
    val settings = container.settings
    val eventCount by container.database.eventDao().count()
        .collectAsStateWithLifecycle(initialValue = 0L)

    val daysObserved = settings.observedDays()
    val targetDays = com.revela.app.SettingsStore.BASELINE_TARGET_DAYS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Revela", style = MaterialTheme.typography.headlineLarge)

        if (daysObserved < targetDays) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Baseline building", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Day ${daysObserved.coerceAtLeast(0)} of ~$targetDays. Early insights " +
                            "are already flowing — they sharpen and deepen as the " +
                            "baseline grows.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (daysObserved.toFloat() / targetDays).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Baseline established", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Rhythms, routines, and shifts are now detectable with " +
                            "full confidence.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Button(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
            Text("Dashboard")
        }
        Button(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
            Text("Insights")
        }
        Button(onClick = onOpenModes, modifier = Modifier.fillMaxWidth()) {
            Text("Modes")
        }
        if (container.llmConfig.active) {
            Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                Text("Ask")
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                StatusRow("Usage access", Permissions.hasUsageAccess(context))
                StatusRow("Battery exemption", Permissions.isIgnoringBatteryOptimizations(context))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Events collected", style = MaterialTheme.typography.bodyMedium)
                    Text("$eventCount", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row {
            TextButton(onClick = onOpenSettings) { Text("Settings") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenDebugLog) { Text("Raw log (debug)") }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(if (ok) "✓" else "✗", style = MaterialTheme.typography.bodyMedium)
    }
}
