package com.abdellatif.clipsave.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadStatus
import com.abdellatif.clipsave.data.model.MediaType
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** A flat filter control: filled when selected, quiet neutral surface otherwise. */
@Composable
fun MinimalChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(150),
        label = "chipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(150),
        label = "chipContent"
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = content
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
fun StatusBadge(status: DownloadStatus) {
    val (label, color) = when (status) {
        DownloadStatus.QUEUED -> "Queued" to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.EXTRACTING -> "Extracting" to MaterialTheme.colorScheme.primary
        DownloadStatus.DOWNLOADING -> "Downloading" to MaterialTheme.colorScheme.primary
        DownloadStatus.COMPLETED -> "Saved" to Color(0xFF3E9C5C)
        DownloadStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private val Download.isBusy: Boolean
    get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.EXTRACTING

@Composable
fun DownloadRow(
    item: Download,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: ((Download) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val playable = item.status == DownloadStatus.COMPLETED &&
        item.mediaType == MediaType.VIDEO &&
        !item.localUri.isNullOrBlank() &&
        onOpen != null
    val contentModifier = if (playable) {
        Modifier.clickable(role = Role.Button) { onOpen?.invoke(item) }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(contentModifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformIcon(item.platform)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifBlank { item.url },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    val size = maxOf(item.totalBytes, item.bytesDownloaded)
                    val meta = buildString {
                        append(item.platform.displayName)
                        append(" · ")
                        append(item.mediaType.name.lowercase(Locale.US))
                        if (size > 0) append(" · ").append(formatBytes(size))
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.status == DownloadStatus.COMPLETED) {
                        Spacer(Modifier.height(3.dp))
                        StatusBadge(item.status)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (playable) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Play ${
                                    item.title.ifBlank { item.fileName.ifBlank { "video" } }
                                }",
                                modifier = Modifier.padding(start = 2.dp).size(23.dp)
                            )
                        }
                    }
                }
                if (item.status == DownloadStatus.COMPLETED) {
                    RowAction(
                        label = "Remove",
                        icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) }
                    ) { onDelete(item.id) }
                } else {
                    StatusBadge(item.status)
                }
            }

            AnimatedVisibility(
                visible = item.isBusy,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val progress by animateFloatAsState(
                    targetValue = item.progress.coerceIn(0, 100) / 100f,
                    animationSpec = tween(220),
                    label = "downloadProgress"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            if (item.status == DownloadStatus.FAILED && !item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.status != DownloadStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status == DownloadStatus.FAILED) {
                        RowAction(
                            label = "Retry",
                            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) }
                        ) { onRetry(item.id) }
                    }
                    RowAction(
                        label = "Remove",
                        icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) }
                    ) { onDelete(item.id) }
                }
            }
        }
    }
}

@Composable
private fun RowAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides
                    MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
            }
        }
    }
}
