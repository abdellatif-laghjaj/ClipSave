package com.abdellatif.clipsave.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abdellatif.clipsave.R
import com.abdellatif.clipsave.data.model.Platform

private data class PlatformArtwork(@DrawableRes val icon: Int, val color: Color?)

private fun Platform.artwork(): PlatformArtwork = when (this) {
    Platform.YOUTUBE -> PlatformArtwork(R.drawable.platform_youtube, Color(0xFFFF0033))
    Platform.INSTAGRAM -> PlatformArtwork(R.drawable.platform_instagram, Color(0xFFE4405F))
    Platform.TIKTOK -> PlatformArtwork(R.drawable.platform_tiktok, null)
    Platform.TWITTER -> PlatformArtwork(R.drawable.platform_x, null)
    Platform.REDDIT -> PlatformArtwork(R.drawable.platform_reddit, Color(0xFFFF4500))
    Platform.FACEBOOK -> PlatformArtwork(R.drawable.platform_facebook, Color(0xFF0866FF))
    Platform.PINTEREST -> PlatformArtwork(R.drawable.platform_pinterest, Color(0xFFE60023))
    Platform.TELEGRAM -> PlatformArtwork(R.drawable.platform_telegram, Color(0xFF229ED9))
    Platform.TWITCH -> PlatformArtwork(R.drawable.platform_twitch, Color(0xFF9146FF))
    Platform.VIMEO -> PlatformArtwork(R.drawable.platform_vimeo, Color(0xFF1AB7EA))
    Platform.SOUNDCLOUD -> PlatformArtwork(R.drawable.platform_soundcloud, Color(0xFFFF5500))
    else -> PlatformArtwork(R.drawable.platform_web, null)
}

/**
 * Official brand glyphs sit on the same quiet neutral tile so platform color never becomes
 * decoration, a border, or a status signal. Unknown and long-tail sites use a globe fallback.
 */
@Composable
fun PlatformIcon(
    platform: Platform,
    modifier: Modifier = Modifier,
    containerSize: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    val artwork = platform.artwork()
    Box(
        modifier = modifier
            .size(containerSize)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(containerSize * 0.3f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(artwork.icon),
            contentDescription = platform.displayName,
            tint = artwork.color ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Brand glyph without a surrounding tile for compact source attribution. */
@Composable
fun PlatformMark(
    platform: Platform,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp
) {
    val artwork = platform.artwork()
    Icon(
        painter = painterResource(artwork.icon),
        contentDescription = platform.displayName,
        tint = artwork.color ?: MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(iconSize)
    )
}
