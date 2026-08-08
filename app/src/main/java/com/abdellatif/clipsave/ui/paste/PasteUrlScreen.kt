package com.abdellatif.clipsave.ui.paste

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.abdellatif.clipsave.R
import com.abdellatif.clipsave.data.model.CollectionUrlDetector
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.data.model.PlaylistInspectionState
import com.abdellatif.clipsave.data.model.PlaylistItem
import com.abdellatif.clipsave.data.model.PlaylistPreview
import com.abdellatif.clipsave.data.model.UrlInputParser
import com.abdellatif.clipsave.download.FileSaver
import com.abdellatif.clipsave.ui.AppViewModel
import com.abdellatif.clipsave.ui.components.PlatformIcon
import com.abdellatif.clipsave.ui.components.SectionLabel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PasteUrlScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val playlistInspection by vm.playlistInspection.collectAsStateWithLifecycle()
    val pendingDownloadUrl by vm.pendingDownloadUrl.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(DownloadFormat.BEST) }
    var confirmation by remember { mutableStateOf("") }
    var pendingDownload by remember {
        mutableStateOf<Pair<List<String>, DownloadFormat>?>(null)
    }
    val parsed = remember(url) { UrlInputParser.parse(url) }
    val platform = remember(parsed.urls) {
        parsed.urls.singleOrNull()?.let(Platform::fromUrl)
    }
    val collectionDetected = remember(parsed.urls) {
        parsed.urls.singleOrNull()?.let(CollectionUrlDetector::isLikelyCollection) == true
    }

    fun queueDownloads(urls: List<String>, selectedFormat: DownloadFormat) {
        vm.downloadAll(urls, selectedFormat)
        confirmation = if (urls.size == 1) {
            "Queued · ${selectedFormat.label}. Progress is on the Downloads tab."
        } else {
            "Queued ${urls.size} downloads · ${selectedFormat.label}."
        }
        url = ""
    }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) {
            queueDownloads(pending.first, pending.second)
        } else if (!granted) {
            confirmation = "Storage access is required to save files on Android 8 and 9."
        }
    }

    // Auto-dismiss the confirmation banner.
    LaunchedEffect(confirmation) {
        if (confirmation.isNotBlank()) {
            delay(4000)
            confirmation = ""
        }
    }

    LaunchedEffect(playlistInspection) {
        val state = playlistInspection
        if (state is PlaylistInspectionState.Error) {
            confirmation = state.message
            vm.dismissPlaylistInspection()
        }
    }

    LaunchedEffect(pendingDownloadUrl) {
        pendingDownloadUrl?.let { incomingUrl ->
            url = incomingUrl
            vm.consumeNewDownload(incomingUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose(vm::dismissPlaylistInspection)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("New download", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Paste one link or a batch from any of 1000+ supported sites.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth()) {
            TextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Paste one or more links",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                minLines = 3,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                trailingIcon = { Spacer(Modifier.size(44.dp)) }
            )
            IconButton(
                onClick = { url = readClipboard(context) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp)
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
            ) {
                Icon(
                    painterResource(R.drawable.paste),
                    contentDescription = "Paste from clipboard",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        AnimatedVisibility(visible = parsed.urls.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (platform != null) {
                    PlatformIcon(
                        platform = platform,
                        containerSize = 32.dp,
                        iconSize = 17.dp
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.padding(7.dp).size(18.dp)
                        )
                    }
                }
                Text(
                    when {
                        collectionDetected && platform != null ->
                            "${platform.displayName} · playlist link"
                        collectionDetected -> "Playlist or collection link"
                        platform != null -> platform.displayName
                        parsed.omittedCount > 0 ->
                            "${parsed.urls.size} links ready · ${parsed.omittedCount} omitted"
                        else -> "${parsed.urls.size} links ready"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(
            visible = url.isNotBlank() && parsed.urls.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                "Add a valid http or https link to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Video quality")
        Spacer(Modifier.height(10.dp))
        QualityCardRow(
            formats = listOf(
                DownloadFormat.BEST,
                DownloadFormat.Q1080,
                DownloadFormat.Q720,
                DownloadFormat.Q480
            ),
            selected = format,
            cardWidth = 142.dp,
            onSelected = { format = it }
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("Audio only")
        Spacer(Modifier.height(10.dp))
        QualityCardRow(
            formats = listOf(DownloadFormat.AUDIO_M4A, DownloadFormat.AUDIO_MP3),
            selected = format,
            cardWidth = 176.dp,
            onSelected = { format = it }
        )

        Spacer(Modifier.height(28.dp))
        val isInspecting = playlistInspection is PlaylistInspectionState.Loading
        DownloadButton(
            enabled = parsed.urls.isNotEmpty() && !isInspecting,
            loading = isInspecting,
            label = when {
                isInspecting -> "Reading playlist"
                collectionDetected -> "Review playlist"
                parsed.urls.size <= 1 -> "Download"
                else -> "Queue ${parsed.urls.size} downloads"
            },
            onClick = {
                if (collectionDetected) {
                    vm.inspectPlaylist(parsed.urls.single())
                } else {
                    val request = parsed.urls to format
                    if (FileSaver.needsLegacyStoragePermission(context)) {
                        pendingDownload = request
                        storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        queueDownloads(request.first, request.second)
                    }
                }
            }
        )

        AnimatedVisibility(visible = confirmation.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    confirmation,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    val ready = playlistInspection as? PlaylistInspectionState.Ready
    if (ready != null) {
        PlaylistSelectionSheet(
            preview = ready.preview,
            onDismiss = vm::dismissPlaylistInspection,
            onQueue = { selectedUrls ->
                vm.dismissPlaylistInspection()
                val request = selectedUrls to format
                if (FileSaver.needsLegacyStoragePermission(context)) {
                    pendingDownload = request
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    queueDownloads(request.first, request.second)
                }
            }
        )
    }
}

@Composable
private fun QualityCardRow(
    formats: List<DownloadFormat>,
    selected: DownloadFormat,
    cardWidth: Dp,
    onSelected: (DownloadFormat) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        formats.forEach { format ->
            QualityCardOption(
                format = format,
                selected = selected == format,
                onSelected = { onSelected(format) },
                modifier = Modifier.width(cardWidth)
            )
        }
    }
}

@Composable
private fun QualityCardOption(
    format: DownloadFormat,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                onClick = onSelected,
                role = Role.RadioButton
            ),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = qualityCardLabel(format),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Text(
                text = qualityCardDescription(format),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun qualityCardLabel(format: DownloadFormat): String = when (format) {
    DownloadFormat.AUDIO_M4A -> "M4A"
    DownloadFormat.AUDIO_MP3 -> "MP3"
    else -> format.label
}

private fun qualityCardDescription(format: DownloadFormat): String = when (format) {
    DownloadFormat.BEST -> "Original resolution"
    DownloadFormat.Q1080 -> "Full HD"
    DownloadFormat.Q720 -> "HD"
    DownloadFormat.Q480 -> "Smaller file"
    DownloadFormat.AUDIO_M4A -> "Efficient audio"
    DownloadFormat.AUDIO_MP3 -> "Most compatible"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSelectionSheet(
    preview: PlaylistPreview,
    onDismiss: () -> Unit,
    onQueue: (List<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedKeys by remember(preview) {
        mutableStateOf(emptySet<String>())
    }
    val selectedItems = preview.items.filter { it.key in selectedKeys }
    val allSelected = selectedItems.size == preview.items.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                preview.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!preview.uploader.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    preview.uploader,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${selectedItems.size} of ${preview.items.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        selectedKeys = if (allSelected) {
                            emptySet()
                        } else {
                            preview.items.map(PlaylistItem::key).toSet()
                        }
                    }
                ) {
                    Text(if (allSelected) "Clear all" else "Select all")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 430.dp)
            ) {
                items(preview.items, key = PlaylistItem::key) { item ->
                    PlaylistSelectionRow(
                        item = item,
                        selected = item.key in selectedKeys,
                        onSelectedChange = { selected ->
                            selectedKeys = if (selected) {
                                selectedKeys + item.key
                            } else {
                                selectedKeys - item.key
                            }
                        }
                    )
                }
            }

            if (preview.hasMoreItems) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Showing the first ${preview.items.size} of ${preview.reportedItemCount} items.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            DownloadButton(
                enabled = selectedItems.isNotEmpty(),
                label = when (selectedItems.size) {
                    0 -> "Select items to continue"
                    1 -> "Queue 1 download"
                    else -> "Queue ${selectedItems.size} downloads"
                },
                onClick = { onQueue(selectedItems.map(PlaylistItem::url)) }
            )
        }
    }
}

@Composable
private fun PlaylistSelectionRow(
    item: PlaylistItem,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onSelectedChange(!selected) },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(width = 72.dp, height = 44.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                if (item.thumbnailUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val details = listOfNotNull(
                    item.uploader?.takeIf(String::isNotBlank),
                    item.durationSeconds?.let(::formatPlaylistDuration)
                ).joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange
            )
        }
    }
}

private fun formatPlaylistDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remaining)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, remaining)
    }
}

@Composable
private fun DownloadButton(
    enabled: Boolean,
    label: String,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSecondary
        else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    painterResource(R.drawable.download),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun readClipboard(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
}
