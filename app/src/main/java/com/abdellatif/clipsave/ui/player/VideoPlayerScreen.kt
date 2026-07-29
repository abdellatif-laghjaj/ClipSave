package com.abdellatif.clipsave.ui.player

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.ui.components.PlatformIcon
import com.abdellatif.clipsave.ui.components.formatBytes
import kotlinx.coroutines.delay
import java.util.Locale

private val PlayerBackground = Color(0xFF0D0D0F)
private val PlayerSurface = Color(0xFF161619)
private val PlayerText = Color(0xFFF7F5F1)
private val PlayerMuted = Color(0xFFA8A6A1)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(download: Download, onBack: () -> Unit) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val player = remember(download.localUri) {
        val localUri = requireNotNull(download.localUri)
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(localUri)))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playbackState by remember { mutableStateOf(player.playbackState) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player, hostView) {
        hostView.keepScreenOn = true
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "This video could not be played."
                controlsVisible = true
            }
        }
        player.addListener(listener)
        onDispose {
            hostView.keepScreenOn = false
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

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_000)
            controlsVisible = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PlayerBackground)
            .navigationBarsPadding()
    ) {
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
                text = download.title.ifBlank { "Video" },
                style = MaterialTheme.typography.titleMedium,
                color = PlayerText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            PlatformIcon(
                platform = download.platform,
                containerSize = 36.dp,
                iconSize = 18.dp
            )
            Spacer(Modifier.size(8.dp))
        }

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
                    ) { controlsVisible = !controlsVisible }
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.36f))) {
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
                                    controlsVisible = true
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
                                    controlsVisible = true
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
                                    controlsVisible = true
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
                        Slider(
                            value = if (duration > 0) {
                                position.toFloat() / duration.toFloat()
                            } else {
                                0f
                            },
                            onValueChange = { fraction ->
                                if (duration > 0) player.seekTo((duration * fraction).toLong())
                                controlsVisible = true
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = PlayerText,
                                activeTrackColor = PlayerText,
                                inactiveTrackColor = PlayerText.copy(alpha = 0.28f)
                            )
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

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                download.title.ifBlank { download.fileName.ifBlank { "Saved video" } },
                style = MaterialTheme.typography.headlineSmall,
                color = PlayerText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformIcon(
                    download.platform,
                    containerSize = 36.dp,
                    iconSize = 18.dp
                )
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
                Spacer(Modifier.height(20.dp))
                Text(
                    errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF0918A)
                )
            }
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

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
