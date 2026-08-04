package com.abdellatif.clipsave.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSerializationTest {

    @Test
    fun oldHistoryWithoutFormatUsesBestQuality() {
        val oldJson = """{"id":"old","url":"https://example.com/video"}"""

        val download = Json.decodeFromString<Download>(oldJson)

        assertEquals(DownloadFormat.BEST, download.format)
        assertEquals(0, download.speedBytesPerSecond)
        assertEquals(-1, download.etaSeconds)
    }
}
