package com.abdellatif.clipsave.ui.downloads

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdellatif.clipsave.data.model.DownloadStatus
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.ui.AppViewModel
import com.abdellatif.clipsave.ui.components.DownloadRow
import com.abdellatif.clipsave.ui.components.EmptyState
import com.abdellatif.clipsave.ui.components.MinimalChip
import java.util.Locale

@Composable
fun DownloadsScreen(vm: AppViewModel, onOpen: (Download) -> Unit) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf<DownloadStatus?>(null) }

    val filtered = remember(downloads, query, filter) {
        downloads.filter { d ->
            (filter == null || d.status == filter) &&
                (query.isBlank() ||
                    d.title.contains(query, ignoreCase = true) ||
                    d.url.contains(query, ignoreCase = true) ||
                    d.platform.displayName.contains(query, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text("Downloads", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (downloads.isNotEmpty()) {
                Text(
                    "${downloads.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinimalChip(selected = filter == null, onClick = { filter = null }, label = "All")
            DownloadStatus.entries.forEach { status ->
                MinimalChip(
                    selected = filter == status,
                    onClick = { filter = if (filter == status) null else status },
                    label = status.name.lowercase(Locale.US)
                        .replaceFirstChar { it.uppercase(Locale.US) }
                )
            }
        }

        if (downloads.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (downloads.any { it.status == DownloadStatus.COMPLETED }) {
                    QuietTextButton("Clear completed") { vm.clearCompleted() }
                }
                QuietTextButton("Clear all") { vm.clearAll() }
            }
        }

        if (filtered.isEmpty()) {
            EmptyState(
                title = if (downloads.isEmpty()) "Nothing saved yet" else "No matches",
                hint = if (downloads.isEmpty())
                    "Downloads you start will show up here."
                else "Try a different search or filter."
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) {
                    DownloadRow(it, vm::retry, vm::delete, onOpen)
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun QuietTextButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
