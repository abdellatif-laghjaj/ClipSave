package com.abdellatif.clipsave.extractor

import com.abdellatif.clipsave.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectMediaUrlTest {
    @Test
    fun `recognizes direct images with query parameters`() {
        val media = DirectMediaUrl.image("https://cdn.example.com/photo.JPEG?width=1600")

        assertEquals(MediaType.IMAGE, media?.mediaType)
        assertEquals("jpg", media?.suggestedExtension)
        assertEquals("photo", media?.title)
        assertEquals("https://cdn.example.com/photo.JPEG?width=1600", media?.downloadUrl)
    }

    @Test
    fun `recognizes extensionless images from response content type`() {
        val media = DirectMediaUrl.image(
            "https://cdn.example.com/resource/12345",
            contentType = "image/webp; charset=binary"
        )

        assertEquals(MediaType.IMAGE, media?.mediaType)
        assertEquals("webp", media?.suggestedExtension)
    }

    @Test
    fun `unwraps reddit media redirect`() {
        val media = DirectMediaUrl.image(
            "https://www.reddit.com/media?url=https%3A%2F%2Fi.redd.it%2F5gmiirtf7cih1.jpeg"
        )

        assertEquals("https://i.redd.it/5gmiirtf7cih1.jpeg", media?.downloadUrl)
        assertEquals(MediaType.IMAGE, media?.mediaType)
    }

    @Test
    fun `does not classify a regular web page as an image`() {
        assertNull(DirectMediaUrl.image("https://example.com/article", "text/html"))
    }

    @Test
    fun `recovers a reddit image from an extractor error`() {
        val media = DirectMediaUrl.imageFromText(
            "ERROR: Unsupported URL: " +
                "https://www.reddit.com/media?url=https%3A%2F%2Fi.redd.it%2Fphoto.jpeg"
        )

        assertEquals("https://i.redd.it/photo.jpeg", media?.downloadUrl)
        assertEquals(MediaType.IMAGE, media?.mediaType)
    }
}
