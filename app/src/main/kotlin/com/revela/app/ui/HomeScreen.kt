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
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebugLog: () -> Unit,
) {
    val context = LocalContext.current
    val settings = container.settings
    val eventCount by container.database.eventDao().count()
        .collectAsStateWithLifecycle(initialValue = 0L)

    val daysElapsed = settings.silentDaysElapsed()
    val windowDays = settings.silentWindowDays

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Revela", style = MaterialTheme.typography.headlineLarge)

        if (!settings.silentWindowOver()) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Watching quietly", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Day ${daysElapsed.coerceAtMost(windowDays)} of $windowDays. " +
                            "Insights unlock once there's an honest baseline.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (daysElapsed.toFloat() / windowDays).coerceIn(0f, 1f) },
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
                        "Your rhythms are visible in the dashboard. The insights feed " +
                            "arrives in the next milestone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (settings.dashboardUnlocked()) {
            Button(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("Dashboard")
            }
        }
        if (settings.silentWindowOver() || settings.devMode) {
            Button(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
                Text("Insights")
            }
            if (container.llmConfig.active) {
                Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                    Text("Ask")
                }
            }
        }
        if (!settings.dashboardUnlocked()) {
            Card {
                Text(
                    "The dashboard unlocks after day ${com.revela.app.SettingsStore.DASHBOARD_UNLOCK_DAYS} " +
                        "of observation.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
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
