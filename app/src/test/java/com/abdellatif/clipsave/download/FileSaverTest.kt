package com.abdellatif.clipsave.download

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSaverTest {

    @Test
    fun `appends media extension when title contains punctuation`() {
        assertEquals(
            "Launching Copper everywhere.mp4",
            FileSaver.safeDisplayName("Launching Copper everywhere...", "mp4")
        )
    }

    @Test
    fun `does not duplicate matching media extension`() {
        assertEquals(
            "Downloaded clip.mp4",
            FileSaver.safeDisplayName("Downloaded clip.MP4", ".mp4")
        )
    }

    @Test
    fun `keeps extension when a long title is truncated`() {
        val name = FileSaver.safeDisplayName("a".repeat(240), "webm")

        assertEquals(180, name.length)
        assertEquals(true, name.endsWith(".webm"))
    }
}
