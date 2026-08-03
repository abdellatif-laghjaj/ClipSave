package com.abdellatif.clipsave.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInputParserTest {

    @Test
    fun extractsNormalizesAndDeduplicatesLinksInOrder() {
        val parsed = UrlInputParser.parse(
            """
            Watch https://youtu.be/abc?t=4, then www.tiktok.com/@creator/video/123.
            Duplicate: https://youtu.be/abc?t=4
            Bare link: example.com/media/clip
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "https://youtu.be/abc?t=4",
                "https://www.tiktok.com/@creator/video/123",
                "https://example.com/media/clip"
            ),
            parsed.urls
        )
        assertEquals(3, parsed.totalDetected)
        assertEquals(0, parsed.omittedCount)
    }

    @Test
    fun ignoresUnsupportedSchemesAndInvalidText() {
        val parsed = UrlInputParser.parse(
            "mailto:user@example.com ftp://example.com/file not-a-link localhost/path"
        )

        // The email address must not become an accidental download target.
        assertTrue(parsed.urls.isEmpty())
    }

    @Test
    fun capsLargeBatchesAndReportsOmittedCount() {
        val input = (1..5).joinToString("\n") { "https://example.com/video/$it" }
        val parsed = UrlInputParser.parse(input, limit = 3)

        assertEquals(3, parsed.urls.size)
        assertEquals(5, parsed.totalDetected)
        assertEquals(2, parsed.omittedCount)
    }
}
