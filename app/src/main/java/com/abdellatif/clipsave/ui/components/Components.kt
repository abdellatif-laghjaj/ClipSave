package com.abdellatif.clipsave.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdellatif.clipsave.R
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadStatus
import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.data.model.Platform
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

fun formatEta(seconds: Long): String {
    if (seconds < 0) return ""
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remaining)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, remaining)
    }
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
        DownloadStatus.WAITING_FOR_NETWORK ->
            "Waiting for network" to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.EXTRACTING -> "Extracting" to MaterialTheme.colorScheme.primary
        DownloadStatus.DOWNLOADING -> "Downloading" to MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> "Paused" to MaterialTheme.colorScheme.onSurfaceVariant
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
    get() = status == DownloadStatus.QUEUED ||
        status == DownloadStatus.WAITING_FOR_NETWORK ||
        status == DownloadStatus.DOWNLOADING ||
        status == DownloadStatus.EXTRACTING

private val Download.isActiveTransfer: Boolean
    get() = status == DownloadStatus.QUEUED ||
        status == DownloadStatus.DOWNLOADING ||
        status == DownloadStatus.EXTRACTING

/**
 * Older builds saved X's Open Graph tweet-card preview when yt-dlp failed.
 * Those files were named from the fallback's dl_* temp file; genuine yt-dlp
 * image downloads retain their extracted filename and should not be retried.
 */
private val Download.isLegacyTwitterPreview: Boolean
    get() = status == DownloadStatus.COMPLETED &&
        platform == Platform.TWITTER &&
        mediaType == MediaType.IMAGE &&
        fileName.startsWith("dl_")

private fun transferDetails(item: Download): String {
    val parts = buildList {
        if (item.progress > 0) {
            add("${item.progress.coerceIn(0, 100)}%")
        } else if (item.bytesDownloaded > 0) {
            add(formatBytes(item.bytesDownloaded))
        }
        if (item.speedBytesPerSecond > 0) {
            add("${formatBytes(item.speedBytesPerSecond)}/s")
        }
        if (item.etaSeconds >= 0) {
            add("${formatEta(item.etaSeconds)} left")
        }
    }
    return parts.joinToString(" · ").ifBlank {
        when (item.status) {
            DownloadStatus.QUEUED -> "Queued"
            DownloadStatus.EXTRACTING -> "Preparing media"
            else -> "Transferring"
        }
    }
}

@Composable
fun DownloadRow(
    item: Download,
    modifier: Modifier = Modifier,
    onRetry: (String) -> Unit,
    onPause: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShare: ((Download) -> Unit)? = null,
    onOpen: ((Download) -> Unit)? = null
) {
    var confirmFileDelete by remember(item.id) { mutableStateOf(false) }
    val openable = item.status == DownloadStatus.COMPLETED &&
        (item.mediaType == MediaType.VIDEO ||
            item.mediaType == MediaType.AUDIO ||
            item.mediaType == MediaType.IMAGE) &&
        !item.localUri.isNullOrBlank() &&
        onOpen != null
    val shareable = item.status == DownloadStatus.COMPLETED &&
        !item.localUri.isNullOrBlank() &&
        onShare != null
    val contentModifier = if (openable) {
        Modifier.clickable(role = Role.Button) { onOpen?.invoke(item) }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(contentModifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                MediaThumbnail(item)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifBlank { item.url },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    val size = maxOf(item.totalBytes, item.bytesDownloaded)
                    val meta = buildString {
                        append(item.mediaType.name.lowercase(Locale.US))
                        append(" · ")
                        append(item.format.label)
                        if (size > 0) append(" · ").append(formatBytes(size))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        PlatformMark(item.platform)
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isLegacyTwitterPreview) {
                            Text(
                                text = "Preview only · retry",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            StatusBadge(item.status)
                        }
                        if (item.status == DownloadStatus.COMPLETED) {
                            CompletedActions(
                                item = item,
                                shareable = shareable,
                                onRetry = onRetry,
                                onShare = onShare,
                                onDeleteRequested = { confirmFileDelete = true }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = item.isActiveTransfer,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val progress by animateFloatAsState(
                    targetValue = item.progress.coerceIn(0, 100) / 100f,
                    animationSpec = tween(220),
                    label = "downloadProgress"
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
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
                        Row(
                            Modifier.fillMaxWidth().padding(top = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = transferDetails(item),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.isBusy) {
                            RowAction(
                                label = "Pause",
                                icon = { Icon(Icons.Rounded.Pause, contentDescription = null) }
                            ) { onPause(item.id) }
                        }
                        RowAction(
                            label = "Remove",
                            destructive = true,
                            icon = {
                                Icon(
                                    painterResource(R.drawable.delete),
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        ) { onDelete(item.id) }
                    }
                }
            }

            if ((item.status == DownloadStatus.FAILED ||
                    item.status == DownloadStatus.PAUSED ||
                    item.status == DownloadStatus.WAITING_FOR_NETWORK) &&
                !item.errorMessage.isNullOrBlank()
            ) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == DownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.status != DownloadStatus.COMPLETED && !item.isActiveTransfer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.PAUSED) {
                        RowAction(
                            label = if (item.status == DownloadStatus.PAUSED) "Resume" else "Retry",
                            icon = {
                                if (item.status == DownloadStatus.PAUSED) {
                                    Icon(
                                        painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        ) { onRetry(item.id) }
                    }
                    RowAction(
                        label = "Remove",
                        destructive = true,
                        icon = {
                            Icon(
                                painterResource(R.drawable.delete),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) { onDelete(item.id) }
                }
            }
        }
    }

    if (confirmFileDelete) {
        AlertDialog(
            onDismissRequest = { confirmFileDelete = false },
            title = { Text("Delete saved file?") },
            text = {
                Text("This permanently deletes the media from your device and removes its download history.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFileDelete = false
                        onDelete(item.id)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFileDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CompletedActions(
    item: Download,
    shareable: Boolean,
    onRetry: (String) -> Unit,
    onShare: ((Download) -> Unit)?,
    onDeleteRequested: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.isLegacyTwitterPreview) {
            RowAction(
                label = "Retry media extraction",
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) }
            ) { onRetry(item.id) }
        }
        if (shareable) {
            RowAction(
                label = "Share",
                icon = {
                    Icon(
                        painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            ) { onShare?.invoke(item) }
        }
        RowAction(
            label = "Delete file",
            destructive = true,
            icon = {
                Icon(
                    painterResource(R.drawable.delete),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
            }
        ) { onDeleteRequested() }
    }
}

@Composable
private fun MediaThumbnail(item: Download) {
    val context = LocalContext.current
    val localPreview by produceState<Bitmap?>(
        initialValue = null,
        key1 = item.localUri,
        key2 = item.mediaType,
        key3 = item.thumbnailUrl
    ) {
        value = if (item.thumbnailUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) { loadLocalMediaPreview(context, item) }
        } else {
            null
        }
    }
    val remoteOrImageModel = item.thumbnailUrl
        ?: item.localUri?.takeIf { item.mediaType == MediaType.IMAGE }

    Box(
        modifier = Modifier
            .size(width = 104.dp, height = 88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (item.mediaType) {
                MediaType.VIDEO -> Icons.Rounded.Movie
                MediaType.AUDIO -> Icons.Rounded.MusicNote
                MediaType.IMAGE -> Icons.Rounded.Photo
                MediaType.UNKNOWN -> Icons.AutoMirrored.Rounded.InsertDriveFile
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(25.dp)
        )
        when {
            remoteOrImageModel != null -> AsyncImage(
                model = remoteOrImageModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            localPreview != null -> Image(
                bitmap = requireNotNull(localPreview).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun loadLocalMediaPreview(context: Context, item: Download): Bitmap? {
    val localUri = item.localUri?.let(Uri::parse) ?: return null
    if (item.mediaType != MediaType.VIDEO && item.mediaType != MediaType.AUDIO) return null

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, localUri)
        if (item.mediaType == MediaType.AUDIO) {
            retriever.embeddedPicture?.let(::decodeArtworkPreview)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                416,
                352
            )
        } else {
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.scaledPreview(416, 352)
        }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun decodeArtworkPreview(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 416 || bounds.outHeight / sampleSize > 352) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    )
}

private fun Bitmap.scaledPreview(maxWidth: Int, maxHeight: Int): Bitmap {
    if (width <= maxWidth && height <= maxHeight) return this
    val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    val scaled = Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true
    )
    if (scaled !== this) recycle()
    return scaled
}

@Composable
private fun RowAction(
    label: String,
    destructive: Boolean = false,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "rowActionPress"
    )
    val containerColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(44.dp)
            .semantics { contentDescription = label },
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = contentColor
        )
    ) {
        Box(
            Modifier
                .size(34.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(17.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}
