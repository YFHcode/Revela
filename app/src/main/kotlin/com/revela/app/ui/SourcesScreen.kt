package com.revela.app.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.revela.app.AppContainer
import com.revela.capture.Permissions
import com.revela.capture.Phase2Scheduler

/**
 * Phase-2 capture sources (§4.2). Each is optional and independently
 * grantable; the app degrades gracefully with any subset. Message bodies are
 * never read (D7); locations are clustered on-device and only place labels
 * ever leave (D5/§3.3).
 */
@Composable
fun SourcesScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current

    var notif by remember { mutableStateOf(Permissions.hasNotificationAccess(context)) }
    var location by remember { mutableStateOf(Permissions.hasForegroundLocation(context)) }
    var background by remember { mutableStateOf(Permissions.hasBackgroundLocation(context)) }
    var calendar by remember { mutableStateOf(Permissions.hasCalendar(context)) }

    fun refresh() {
        notif = Permissions.hasNotificationAccess(context)
        location = Permissions.hasForegroundLocation(context)
        background = Permissions.hasBackgroundLocation(context)
        calendar = Permissions.hasCalendar(context)
        if (location || calendar) Phase2Scheduler.ensureScheduled(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh() }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Sources", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Optional signals that unlock relationship and place insights. Grant " +
                "only what you're comfortable with — each works on its own.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SourceCard(
            title = "Notification timing",
            granted = notif,
            description = "When (not what) you message, to reveal communication " +
                "rhythms and relationship drift. Message contents are never read.",
            actionLabel = "Open notification access",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )

        SourceCard(
            title = "Location",
            granted = location,
            description = "Low-power fixes are clustered into significant places on " +
                "this device. Only place labels and dwell times are used downstream — " +
                "raw coordinates never leave the raw log.",
            actionLabel = "Allow location",
            onAction = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
        )
        if (location && !background && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            SourceCard(
                title = "Location in background",
                granted = false,
                description = "Needed to capture places while the app is closed. " +
                    "Choose \"Allow all the time\".",
                actionLabel = "Allow all the time",
                onAction = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
            )
        }

        SourceCard(
            title = "Calendar",
            granted = calendar,
            description = "Recurring event titles and times as a signal for social " +
                "and other patterns. Read-only.",
            actionLabel = "Allow calendar",
            onAction = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
        )
    }
}

@Composable
private fun SourceCard(
    title: String,
    granted: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (granted) "✓ on" else "off",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}
