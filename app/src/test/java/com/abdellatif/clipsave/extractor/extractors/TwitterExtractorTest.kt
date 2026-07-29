package com.abdellatif.clipsave.extractor.extractors

import com.abdellatif.clipsave.extractor.ExtractionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TwitterExtractorTest {
    @Test
    fun doesNotTreatTweetCardPreviewAsDownloadedMedia() {
        val error = assertThrows(ExtractionException::class.java) {
            TwitterExtractor().extract("https://x.com/example/status/123")
        }

        assertEquals(
            "X could not provide downloadable media. Update the download engine and retry.",
            error.message
        )
    }
}
