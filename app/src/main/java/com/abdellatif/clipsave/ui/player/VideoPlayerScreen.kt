package com.abdellatif.clipsave.ui.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.ui.components.PlatformIcon
import com.abdellatif.clipsave.ui.components.formatBytes
import kotlinx.coroutines.delay
import java.util.Locale

private val PlayerBackground = Color(0xFF0D0D0F)
private val PlayerSurface = Color(0xFF161619)
private val PlayerText = Color(0xFFF7F5F1)
private val PlayerMuted = Color(0xFFA8A6A1)

@Composable
fun VideoPlayerScreen(
    download: Download,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    when {
        download.localUri.isNullOrBlank() -> UnavailableMediaScreen(download, onBack)
        download.mediaType == MediaType.IMAGE -> ImageViewerScreen(download, onBack, onShare)
        download.mediaType == MediaType.VIDEO || download.mediaType == MediaType.AUDIO -> {
            PlaybackScreen(download, onBack, onShare)
        }
        else -> UnavailableMediaScreen(download, onBack)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlaybackScreen(
    download: Download,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isVideo = download.mediaType == MediaType.VIDEO
    val player = remember(download.localUri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(requireNotNull(download.localUri).toUri()))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player, hostView, lifecycleOwner, isVideo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
                hostView.keepScreenOn = isVideo && value
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "This saved media could not be played."
                controlsVisible = true
                controlsInteraction++
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            hostView.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isVideo, controlsInteraction) {
        if (isVideo && controlsVisible && isPlaying) {
            delay(3_000)
            controlsVisible = false
        }
    }

    PlayerPage(download, onBack, onShare) {
        if (isVideo) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null
                        ) {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) controlsInteraction++
                        }
                )
                PlaybackControls(
                    player = player,
                    visible = controlsVisible,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    position = position,
                    duration = duration,
                    scrim = Color.Black.copy(alpha = 0.36f),
                    onInteraction = {
                        controlsVisible = true
                        controlsInteraction++
                    }
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PlayerBackground)
            ) {
                AudioArtwork(
                    download,
                    Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
                )
                PlaybackControls(
                    player = player,
                    visible = true,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    position = position,
                    duration = duration,
                    scrim = Color.Transparent,
                    onInteraction = {}
                )
            }
        }

        MediaDetails(download, errorMessage)
    }
}

@Composable
private fun ImageViewerScreen(
    download: Download,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    var loadFailed by remember(download.localUri) { mutableStateOf(false) }
    PlayerPage(download, onBack, onShare) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (loadFailed) {
                Text(
                    "This saved image could not be opened.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlayerMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            } else {
                AsyncImage(
                    model = download.localUri,
                    contentDescription = download.title.ifBlank { "Saved image" },
                    contentScale = ContentScale.Fit,
                    onError = { loadFailed = true },
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
        }
        MediaDetails(
            download,
            if (loadFailed) "The file may have been moved or deleted." else null
        )
    }
}

@Composable
private fun PlayerPage(
    download: Download,
    onBack: () -> Unit,
    onShare: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PlayerBackground)
            .navigationBarsPadding()
    ) {
        MediaHeader(download, onBack, onShare)
        content()
    }
}

@Composable
private fun MediaHeader(download: Download, onBack: () -> Unit, onShare: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = PlayerText
            )
        }
        Text(
            text = download.title.ifBlank { mediaLabel(download.mediaType) },
            style = MaterialTheme.typography.titleMedium,
            color = PlayerText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        PlatformIcon(download.platform, containerSize = 36.dp, iconSize = 18.dp)
        if (onShare != null) {
            IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = PlayerText)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun AudioArtwork(download: Download, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).background(PlayerSurface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            PlatformIcon(download.platform, containerSize = 76.dp, iconSize = 38.dp)
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Headphones,
                contentDescription = null,
                tint = PlayerMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(7.dp))
            Text("Audio", style = MaterialTheme.typography.labelLarge, color = PlayerMuted)
        }
    }
}

@Composable
private fun PlaybackControls(
    player: Player,
    visible: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    position: Long,
    duration: Long,
    scrim: Color,
    onInteraction: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize().background(scrim)) {
            if (playbackState == Player.STATE_BUFFERING) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(38.dp),
                    color = PlayerText,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerControlButton(
                        onClick = {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                            onInteraction()
                        },
                        contentDescription = "Back 10 seconds"
                    ) {
                        Icon(Icons.Rounded.Replay10, contentDescription = null)
                    }

                    Surface(
                        onClick = {
                            if (isPlaying) {
                                player.pause()
                            } else {
                                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                                player.play()
                            }
                            onInteraction()
                        },
                        shape = CircleShape,
                        color = PlayerText,
                        contentColor = PlayerBackground
                    ) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier
                                    .padding(start = if (isPlaying) 0.dp else 2.dp)
                                    .size(30.dp)
                            )
                        }
                    }

                    PlayerControlButton(
                        onClick = {
                            player.seekTo(
                                (player.currentPosition + 10_000)
                                    .coerceAtMost(duration.coerceAtLeast(player.currentPosition))
                            )
                            onInteraction()
                        },
                        contentDescription = "Forward 10 seconds"
                    ) {
                        Icon(Icons.Rounded.Forward10, contentDescription = null)
                    }
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                PlayerSeekBar(
                    value = if (duration > 0) {
                        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onValueChange = { fraction ->
                        if (duration > 0) player.seekTo((duration * fraction).toLong())
                        onInteraction()
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        formatDuration(position),
                        style = MaterialTheme.typography.labelMedium,
                        color = PlayerText
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatDuration(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = PlayerText
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSeekBar(value: Float, onValueChange: (Float) -> Unit) {
    var widthPx by remember { mutableIntStateOf(0) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val fraction = value.coerceIn(0f, 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    currentOnValueChange(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(widthPx) {
                if (widthPx <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnValueChange((down.position.x / widthPx).coerceIn(0f, 1f))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        currentOnValueChange((change.position.x / widthPx).coerceIn(0f, 1f))
                        change.consume()
                    }
                }
            }
    ) {
        val centerY = size.height / 2f
        val inset = 6.dp.toPx()
        val trackWidth = (size.width - inset * 2f).coerceAtLeast(0f)
        val thumbX = inset + trackWidth * fraction
        drawLine(
            color = PlayerText.copy(alpha = 0.24f),
            start = androidx.compose.ui.geometry.Offset(inset, centerY),
            end = androidx.compose.ui.geometry.Offset(size.width - inset, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = PlayerText,
            start = androidx.compose.ui.geometry.Offset(inset, centerY),
            end = androidx.compose.ui.geometry.Offset(thumbX, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawCircle(
            color = PlayerText,
            radius = 6.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(thumbX, centerY)
        )
    }
}

@Composable
private fun MediaDetails(download: Download, errorMessage: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
        Text(
            download.title.ifBlank {
                download.fileName.ifBlank { "Saved ${mediaLabel(download.mediaType).lowercase()}" }
            },
            style = MaterialTheme.typography.headlineSmall,
            color = PlayerText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformIcon(download.platform, containerSize = 36.dp, iconSize = 18.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    download.platform.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = PlayerText
                )
                val bytes = maxOf(download.totalBytes, download.bytesDownloaded)
                Text(
                    if (bytes > 0) "${formatBytes(bytes)} · saved to device" else "Saved to device",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerMuted
                )
            }
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF0918A)
            )
        }
    }
}

@Composable
private fun UnavailableMediaScreen(download: Download, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PlayerBackground)
            .navigationBarsPadding()
    ) {
        MediaHeader(download, onBack, onShare = null)
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "This saved media is no longer available.",
                style = MaterialTheme.typography.titleMedium,
                color = PlayerText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "It may have been moved or deleted outside ClipSave.",
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlayerControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { this.contentDescription = contentDescription }
    ) {
        Box(
            Modifier.size(44.dp).background(PlayerSurface.copy(alpha = 0.88f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides PlayerText
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            }
        }
    }
}

private fun mediaLabel(mediaType: MediaType): String = when (mediaType) {
    MediaType.VIDEO -> "Video"
    MediaType.AUDIO -> "Audio"
    MediaType.IMAGE -> "Image"
    MediaType.UNKNOWN -> "Media"
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
