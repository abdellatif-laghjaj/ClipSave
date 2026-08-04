package com.abdellatif.clipsave.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistParserTest {
    @Test
    fun `parses selectable playlist metadata and canonical URLs`() {
        val preview = PlaylistParser.parse(
            """
            {
              "_type": "playlist",
              "title": "Clean coding",
              "channel": "ClipSave Labs",
              "playlist_count": 3,
              "entries": [
                {
                  "id": "one",
                  "title": "First lesson",
                  "webpage_url": "https://example.com/watch/one",
                  "duration": 125,
                  "thumbnail": "https://img.example.com/one.jpg",
                  "uploader": "Teacher"
                },
                {
                  "id": "two",
                  "title": "Second lesson",
                  "url": "two",
                  "ie_key": "Youtube"
                }
              ]
            }
            """.trimIndent(),
            previewLimit = 100
        )!!

        assertEquals("Clean coding", preview.title)
        assertEquals("ClipSave Labs", preview.uploader)
        assertEquals(2, preview.items.size)
        assertEquals("https://example.com/watch/one", preview.items[0].url)
        assertEquals(125L, preview.items[0].durationSeconds)
        assertEquals("https://youtu.be/two", preview.items[1].url)
        assertEquals(3, preview.reportedItemCount)
        assertTrue(preview.hasMoreItems)
    }

    @Test
    fun `deduplicates entries and ignores unavailable items`() {
        val preview = PlaylistParser.parse(
            """
            {
              "title": "Small list",
              "playlist_count": 2,
              "entries": [
                {"id":"one","title":"One","webpage_url":"https://example.com/one"},
                {"id":"copy","title":"Copy","webpage_url":"https://example.com/one"},
                {"id":"missing","title":"Unavailable"}
              ]
            }
            """.trimIndent(),
            previewLimit = 100
        )!!

        assertEquals(1, preview.items.size)
        assertEquals(2, preview.reportedItemCount)
        assertTrue(preview.hasMoreItems)
    }

    @Test
    fun `returns null for a single media object`() {
        assertNull(
            PlaylistParser.parse(
                """{"id":"one","title":"Single video","webpage_url":"https://example.com/one"}""",
                previewLimit = 100
            )
        )
    }

    @Test
    fun `does not claim more items for a complete bounded list`() {
        val preview = PlaylistParser.parse(
            """{"playlist_count":1,"entries":[{"id":"one","url":"https://example.com/one"}]}""",
            previewLimit = 100
        )!!

        assertFalse(preview.hasMoreItems)
    }
}
