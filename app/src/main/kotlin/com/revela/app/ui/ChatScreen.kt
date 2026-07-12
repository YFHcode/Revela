package com.revela.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revela.app.AppContainer
import com.revela.insights.llm.QueryEngine
import kotlinx.coroutines.launch

/** L3 — ask questions over the summary tables ("why was last week off?"). */
@Composable
fun ChatScreen(container: AppContainer, onBack: () -> Unit) {
    val turns = remember { mutableStateListOf<QueryEngine.Turn>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Ask your mirror", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (turns.isEmpty()) {
                item {
                    Text(
                        "Questions are answered from your local summaries — " +
                            "\"when do I usually wake up on weekends?\", " +
                            "\"which app grew the most this month?\", " +
                            "\"why was last week different?\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(turns.size) { i ->
                val turn = turns[i]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (turn.role == "user") "You" else "Revela",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(turn.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        Text("thinking…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask about your patterns") },
                modifier = Modifier.weight(1f),
                enabled = !busy,
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    val question = input.trim()
                    if (question.isEmpty()) return@Button
                    input = ""
                    val history = turns.toList()
                    turns += QueryEngine.Turn("user", question)
                    busy = true
                    scope.launch {
                        val answer = container.queryEngine.ask(history, question)
                        turns += QueryEngine.Turn("assistant", answer)
                        busy = false
                    }
                },
                enabled = !busy && input.isNotBlank(),
            ) {
                Text("Send")
            }
        }
    }
}
