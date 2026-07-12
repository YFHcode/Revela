package com.revela.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revela.app.AppContainer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TS_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

/** Debug-only viewer proving that events land in the raw log (M1 acceptance). */
@Composable
fun DebugLogScreen(container: AppContainer, onBack: () -> Unit) {
    val events by container.database.eventDao().recent(limit = 200)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row {
            TextButton(onClick = onBack) { Text("← Back") }
        }
        Text("Raw event log (${events.size} newest)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(events, key = { it.id }) { event ->
                val time = TS_FORMAT.format(
                    Instant.ofEpochMilli(event.ts).atZone(ZoneId.systemDefault()),
                )
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        "$time  ${event.type}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    event.appPkg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
