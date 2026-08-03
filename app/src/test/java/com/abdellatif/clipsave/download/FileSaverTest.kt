package com.abdellatif.clipsave.download

import com.abdellatif.clipsave.data.model.MediaType
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

    @Test
    fun `maps share mime types from saved media containers`() {
        assertEquals("video/webm", FileSaver.mimeFor(MediaType.VIDEO, "webm"))
        assertEquals("audio/mp4", FileSaver.mimeFor(MediaType.AUDIO, ".m4a"))
        assertEquals("audio/mpeg", FileSaver.mimeFor(MediaType.AUDIO, "mp3"))
        assertEquals("image/webp", FileSaver.mimeFor(MediaType.IMAGE, "webp"))
    }
}
