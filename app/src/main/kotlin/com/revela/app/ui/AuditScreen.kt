package com.revela.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revela.app.AppContainer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TS = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")

/** §3.3 — "what left the device": every payload ever sent to the LLM API. */
@Composable
fun AuditScreen(container: AppContainer, onBack: () -> Unit) {
    val rows by container.database.llmAuditDao().recent(100)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("What left the device", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Every request ever sent to the AI service, logged before sending. " +
                "Aggregated numbers and your own questions — never raw events.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(4.dp))

        if (rows.isEmpty()) {
            Text(
                "Nothing has been sent.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn {
                items(rows, key = { it.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${TS.format(Instant.ofEpochMilli(row.ts).atZone(ZoneId.systemDefault()))}" +
                                    "  ·  ${row.purpose}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                row.payload,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 12,
                            )
                        }
                    }
                }
            }
        }
    }
}
