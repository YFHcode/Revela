package com.revela.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revela.app.AppContainer
import com.revela.core.db.EntityKind
import com.revela.core.db.TrackedEntity
import kotlinx.coroutines.launch

/** Label places, and delete a contact or place entirely from history (D6). */
@Composable
fun ManageDataScreen(container: AppContainer, onBack: () -> Unit) {
    val places by container.database.trackedEntityDao().byKindFlow(EntityKind.PLACE)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val contacts by container.database.trackedEntityDao().byKindFlow(EntityKind.CONTACT)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<TrackedEntity?>(null) }
    var deleting by remember { mutableStateOf<TrackedEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Places & contacts", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (places.isNotEmpty()) {
                item { SectionHeader("Places") }
                items(places, key = { "p${it.id}" }) { place ->
                    EntityRow(
                        name = place.displayName,
                        subtitle = place.placeLabel?.lowercase(),
                        onEdit = { editing = place },
                        onDelete = { deleting = place },
                    )
                }
            }
            if (contacts.isNotEmpty()) {
                item { SectionHeader("Contacts") }
                items(contacts, key = { "c${it.id}" }) { contact ->
                    EntityRow(
                        name = contact.displayName,
                        subtitle = null,
                        onEdit = null,
                        onDelete = { deleting = contact },
                    )
                }
            }
            if (places.isEmpty() && contacts.isEmpty()) {
                item {
                    Text(
                        "Places and contacts appear here once notification or " +
                            "location capture has gathered enough to identify them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }

    editing?.let { place ->
        RenameDialog(
            initial = place.displayName,
            onDismiss = { editing = null },
            onConfirm = { newName ->
                scope.launch {
                    container.database.trackedEntityDao()
                        .relabelPlace(place.id, newName, place.placeLabel ?: "OTHER")
                }
                editing = null
            },
        )
    }

    deleting?.let { entity ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${entity.displayName}?") },
            text = {
                Text(
                    "Every event, rollup, and insight involving ${entity.displayName} " +
                        "will be permanently removed from this device.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.deleteEntity(entity.id) }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun EntityRow(
    name: String,
    subtitle: String?,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Card {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            onEdit?.let {
                TextButton(onClick = it) { Text("Rename") }
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename place") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
