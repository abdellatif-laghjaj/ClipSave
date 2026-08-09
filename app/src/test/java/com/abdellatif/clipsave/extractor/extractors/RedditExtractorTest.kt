package com.abdellatif.clipsave.extractor.extractors

import com.abdellatif.clipsave.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class RedditExtractorTest {
    @Test
    fun `parses a reddit image post as an image`() {
        val body = redditListing(
            """
            "title": "How's this case?",
            "url_overridden_by_dest": "https://i.redd.it/5gmiirtf7cih1.jpeg"
            """.trimIndent()
        )

        val media = RedditExtractor().parseJson(body)?.single()

        assertEquals(MediaType.IMAGE, media?.mediaType)
        assertEquals("How's this case?", media?.title)
        assertEquals("https://i.redd.it/5gmiirtf7cih1.jpeg", media?.downloadUrl)
    }

    @Test
    fun `parses reddit gallery images in display order`() {
        val body = redditListing(
            """
            "title": "Gallery post",
            "gallery_data": {"items": [{"media_id": "one"}, {"media_id": "two"}]},
            "media_metadata": {
              "one": {"s": {"u": "https://i.redd.it/first.png?width=1080&amp;format=png"}},
              "two": {"s": {"u": "https://i.redd.it/second.webp"}}
            }
            """.trimIndent()
        )

        val media = RedditExtractor().parseJson(body).orEmpty()

        assertEquals(2, media.size)
        assertEquals("https://i.redd.it/first.png?width=1080&format=png", media[0].downloadUrl)
        assertEquals("https://i.redd.it/second.webp", media[1].downloadUrl)
        assertEquals(listOf(MediaType.IMAGE, MediaType.IMAGE), media.map { it.mediaType })
    }

    private fun redditListing(postFields: String): String =
        """[{"data":{"children":[{"data":{$postFields}}]}}]"""
}
