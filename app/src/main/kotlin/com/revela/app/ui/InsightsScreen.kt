package com.revela.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revela.app.AppContainer
import com.revela.core.db.InsightEntity
import com.revela.insights.InsightsScheduler
import kotlinx.coroutines.launch
import org.json.JSONObject

/** §12.2 — the insights feed: discovered patterns in plain language, the
 *  supporting numbers a tap away. Dismiss and pin; no scores, no streaks. */
@Composable
fun InsightsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { InsightsScheduler.analyzeNow(context) }

    val insights by container.database.insightDao().feed()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Insights", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        if (insights.isEmpty()) {
            Card {
                Text(
                    "Nothing surfaced yet. Patterns need a few weeks of baseline " +
                        "before they can be trusted — the feed fills in as rhythms " +
                        "become visible.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(insights, key = { it.id }) { insight ->
                    InsightCard(
                        insight = insight,
                        onDismiss = {
                            scope.launch { container.database.insightDao().dismiss(insight.id) }
                        },
                        onTogglePin = {
                            scope.launch {
                                container.database.insightDao().setPinned(insight.id, !insight.pinned)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: InsightEntity,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    typeLabel(insight.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (insight.pinned) {
                    Text("pinned", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(insight.text, style = MaterialTheme.typography.bodyLarge)

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    prettyPayload(insight.statPayload),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = onTogglePin) {
                        Text(if (insight.pinned) "Unpin" else "Pin")
                    }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    "periodic_rhythm" -> "Rhythm"
    "habit_shift" -> "Something changed"
    "unusual_day" -> "Unusual day"
    "chronotype" -> "Chronotype"
    "reflex_checks" -> "Quick checks"
    else -> "Pattern"
}

private fun prettyPayload(payload: String): String =
    runCatching {
        val json = JSONObject(payload)
        json.keys().asSequence().joinToString("\n") { key -> "$key: ${json.get(key)}" }
    }.getOrDefault(payload)
