package com.revela.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.revela.app.AppContainer
import com.revela.capture.CaptureScheduler
import com.revela.capture.Permissions

private enum class Step { Welcome, UsageAccess, Battery }

@Composable
fun OnboardingScreen(container: AppContainer, onFinished: () -> Unit) {
    var step by remember { mutableStateOf(Step.Welcome) }
    var silentDays by remember { mutableIntStateOf(container.settings.silentWindowDays) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            Step.Welcome -> WelcomeStep(
                silentDays = silentDays,
                onSilentDaysChange = { silentDays = it },
                onNext = { step = Step.UsageAccess },
            )
            Step.UsageAccess -> UsageAccessStep(
                onGranted = { step = Step.Battery },
            )
            Step.Battery -> BatteryStep(
                onDone = {
                    val settings = container.settings
                    settings.silentWindowDays = silentDays
                    settings.observationStart = System.currentTimeMillis()
                    settings.onboardingComplete = true
                    onFinished()
                },
            )
        }
    }
}

@Composable
private fun WelcomeStep(
    silentDays: Int,
    onSilentDaysChange: (Int) -> Unit,
    onNext: () -> Unit,
) {
    Text("Revela", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(16.dp))
    Text(
        "A mirror, not a scold. Revela quietly observes how you use your phone " +
            "and surfaces the patterns you can't see from the inside — rhythms, " +
            "routines, and changes over time.\n\n" +
            "Everything stays on this device, encrypted. No accounts, no cloud, " +
            "no scores, no streaks.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "First, I'll watch quietly for $silentDays days before showing you anything, " +
            "so your baseline stays honest.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = silentDays.toFloat(),
        onValueChange = { onSilentDaysChange(it.toInt()) },
        valueRange = 7f..28f,
        steps = 20,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text("Get started")
    }
}

@Composable
private fun UsageAccessStep(onGranted: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }

    // Re-check when the user comes back from Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = Permissions.hasUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text("Usage access", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text(
        "Revela needs Android's \"usage access\" to see which apps are used and " +
            "when the screen turns on. This is metadata only — no content, no " +
            "keystrokes, nothing leaves the device.\n\n" +
            "On the next screen, find Revela in the list and enable it.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(24.dp))
    if (granted) {
        Text("✓ Usage access granted", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGranted, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    } else {
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open usage access settings")
        }
    }
}

@Composable
private fun BatteryStep(onDone: () -> Unit) {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = Permissions.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun finish() {
        CaptureScheduler.ensureScheduled(context)
        CaptureScheduler.captureNow(context)
        onDone()
    }

    Text("Keep collection alive", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text(
        "Some phones aggressively stop background apps. Exempting Revela from " +
            "battery optimization keeps observation running. Collection is very " +
            "light — a few seconds of work every 15 minutes.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(24.dp))
    if (exempt) {
        Text("✓ Battery optimization disabled for Revela", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
            Text("Start observing")
        }
    } else {
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open battery settings")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now")
        }
    }
}
