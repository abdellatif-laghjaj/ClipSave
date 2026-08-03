package com.abdellatif.clipsave.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInbox
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdellatif.clipsave.R
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadStatus
import com.abdellatif.clipsave.ui.AppViewModel
import com.abdellatif.clipsave.ui.components.DownloadRow
import com.abdellatif.clipsave.ui.components.EmptyState
import com.abdellatif.clipsave.ui.components.SectionLabel

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onGoToPaste: () -> Unit,
    onOpen: (Download) -> Unit
) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val inProgress = remember(downloads) {
        downloads.filter {
            it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.EXTRACTING ||
                it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.PAUSED
        }
    }
    val activeCount = remember(downloads) {
        downloads.count {
            it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.EXTRACTING ||
                it.status == DownloadStatus.QUEUED
        }
    }
    val recent = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }.take(10)
    }
    val completed = remember(downloads) {
        downloads.count { it.status == DownloadStatus.COMPLETED }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { NewDownloadButton(onGoToPaste) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 104.dp)
        ) {
            item {
                Header()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = "Total",
                        value = downloads.size.toString(),
                        icon = Icons.Rounded.AllInbox
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = "Saved",
                        value = completed.toString(),
                        icon = Icons.Rounded.CheckCircle
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = "Active",
                        value = activeCount.toString(),
                        icon = Icons.Rounded.Downloading
                    )
                }
            }

            if (downloads.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing saved yet",
                        hint = "Paste a link on the New tab, or share one from any app.",
                        modifier = Modifier.fillMaxWidth().height(390.dp)
                    )
                }
            }

            if (inProgress.isNotEmpty()) {
                item {
                    SectionLabel(
                        "In progress",
                        Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                items(inProgress, key = { it.id }) {
                    DownloadRow(
                        item = it,
                        onRetry = vm::retry,
                        onPause = vm::pause,
                        onDelete = vm::delete,
                        onOpen = onOpen
                    )
                }
            }

            if (recent.isNotEmpty()) {
                item {
                    SectionLabel(
                        "Recent",
                        Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                items(recent, key = { it.id }) {
                    DownloadRow(
                        item = it,
                        onRetry = vm::retry,
                        onPause = vm::pause,
                        onDelete = vm::delete,
                        onOpen = onOpen
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("ClipSave", style = MaterialTheme.typography.headlineMedium)
            Text(
                ".",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Your media, ready offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    label: String,
    value: String,
    icon: ImageVector
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NewDownloadButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Row(
            Modifier.padding(start = 18.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.plus),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("New download", style = MaterialTheme.typography.labelLarge)
        }
    }
}
