package com.abdellatif.clipsave.ui.theme

import com.abdellatif.clipsave.data.preferences.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun `every accent updates actions and highlights in light and dark themes`() {
        AccentColor.entries.forEach { accent ->
            listOf(false, true).forEach { darkTheme ->
                val expected = accentSwatch(accent, darkTheme)
                val scheme = clipSaveColorScheme(darkTheme, accent)

                assertEquals(expected, scheme.primary)
                assertEquals(expected, scheme.secondary)
                assertEquals(expected, scheme.tertiary)
            }
        }
    }

    @Test
    fun `accent choice does not tint neutral app surfaces`() {
        val amber = clipSaveColorScheme(darkTheme = false, AccentColor.AMBER)
        val blue = clipSaveColorScheme(darkTheme = false, AccentColor.BLUE)

        assertEquals(amber.background, blue.background)
        assertEquals(amber.surface, blue.surface)
        assertEquals(amber.surfaceContainerLow, blue.surfaceContainerLow)
    }
}
