package com.abdellatif.clipsave.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abdellatif.clipsave.data.preferences.AccentColor

/** Warm neutral surfaces with a user-selectable accent and deliberately flat styling. */
private data class AccentPalette(
    val light: Color,
    val dark: Color,
    val onLight: Color = Color.White,
    val onDark: Color = Color(0xFF101014)
)

private val AccentPalettes = mapOf(
    AccentColor.AMBER to AccentPalette(
        light = Color(0xFFEBA400),
        dark = Color(0xFFFFB703),
        onLight = Color(0xFF201500),
        onDark = Color(0xFF201500)
    ),
    AccentColor.BLUE to AccentPalette(
        light = Color(0xFF2F6BDE),
        dark = Color(0xFF7AA7FF)
    ),
    AccentColor.PURPLE to AccentPalette(
        light = Color(0xFF7654D8),
        dark = Color(0xFFB8A4FF)
    ),
    AccentColor.RED to AccentPalette(
        light = Color(0xFFC84B4B),
        dark = Color(0xFFFF8C86)
    ),
    AccentColor.GREEN to AccentPalette(
        light = Color(0xFF2F7D5C),
        dark = Color(0xFF69D6A6)
    ),
    AccentColor.ORANGE to AccentPalette(
        light = Color(0xFFD45F2A),
        dark = Color(0xFFFF9B6A)
    )
)

// Light: warm paper
private val PaperLight = Color(0xFFFAF9F7)
private val InkLight = Color(0xFF191817)
private val MutedLight = Color(0xFF8A867E)
private val TileLight = Color(0xFFF1EFEA)
private val HairlineLight = Color(0xFFE7E4DE)

// Dark: soft charcoal
private val PaperDark = Color(0xFF111013)
private val InkDark = Color(0xFFF0EEEA)
private val MutedDark = Color(0xFF908D87)
private val TileDark = Color(0xFF1B1A1E)
private val HairlineDark = Color(0xFF27262B)

private val NeutralLightColors = lightColorScheme(
    secondary = InkLight,
    onSecondary = PaperLight,
    secondaryContainer = TileLight,
    onSecondaryContainer = InkLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = PaperLight,
    onSurface = InkLight,
    surfaceVariant = TileLight,
    onSurfaceVariant = MutedLight,
    surfaceContainer = TileLight,
    surfaceContainerLow = Color(0xFFF5F3EF),
    surfaceContainerHigh = Color(0xFFEDEBE5),
    outline = HairlineLight,
    outlineVariant = Color(0xFFEFEDE8),
    error = Color(0xFFC0453D),
    onError = Color.White
)

private val NeutralDarkColors = darkColorScheme(
    secondary = InkDark,
    onSecondary = PaperDark,
    secondaryContainer = TileDark,
    onSecondaryContainer = InkDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = TileDark,
    onSurfaceVariant = MutedDark,
    surfaceContainer = TileDark,
    surfaceContainerLow = Color(0xFF17161A),
    surfaceContainerHigh = Color(0xFF211F25),
    outline = HairlineDark,
    outlineVariant = Color(0xFF1F1E23),
    error = Color(0xFFE5726A),
    onError = Color(0xFF230404)
)

private val ClipSaveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

internal fun accentSwatch(accentColor: AccentColor, darkTheme: Boolean): Color {
    val palette = AccentPalettes.getValue(accentColor)
    return if (darkTheme) palette.dark else palette.light
}

internal fun clipSaveColorScheme(
    darkTheme: Boolean,
    accentColor: AccentColor
): ColorScheme {
    val base = if (darkTheme) NeutralDarkColors else NeutralLightColors
    val palette = AccentPalettes.getValue(accentColor)
    val accent = if (darkTheme) palette.dark else palette.light
    val onAccent = if (darkTheme) palette.onDark else palette.onLight
    val containerAlpha = if (darkTheme) 0.18f else 0.14f
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = containerAlpha),
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent.copy(alpha = containerAlpha),
        onSecondaryContainer = accent,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent.copy(alpha = containerAlpha),
        onTertiaryContainer = accent
    )
}

@Composable
fun ClipSaveTheme(
    darkTheme: Boolean,
    accentColor: AccentColor = AccentColor.AMBER,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = clipSaveColorScheme(darkTheme, accentColor),
        typography = ClipSaveTypography,
        shapes = ClipSaveShapes,
        content = content
    )
}
