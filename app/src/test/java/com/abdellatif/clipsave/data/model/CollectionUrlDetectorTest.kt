package com.abdellatif.clipsave.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionUrlDetectorTest {
    @Test
    fun `detects explicit YouTube playlist query`() {
        assertTrue(
            CollectionUrlDetector.isLikelyCollection(
                "https://www.youtube.com/watch?v=abc123&list=PL123"
            )
        )
    }

    @Test
    fun `detects collection path across supported sites`() {
        assertTrue(
            CollectionUrlDetector.isLikelyCollection(
                "https://soundcloud.com/creator/sets/my-playlist"
            )
        )
        assertTrue(
            CollectionUrlDetector.isLikelyCollection(
                "https://vimeo.com/showcase/12345"
            )
        )
        assertTrue(
            CollectionUrlDetector.isLikelyCollection(
                "https://www.youtube.com/@AndroidDevelopers"
            )
        )
    }

    @Test
    fun `keeps normal media links on instant queue path`() {
        assertFalse(
            CollectionUrlDetector.isLikelyCollection(
                "https://www.youtube.com/watch?v=abc123"
            )
        )
        assertFalse(CollectionUrlDetector.isLikelyCollection("not a URL"))
        assertFalse(
            CollectionUrlDetector.isLikelyCollection(
                "https://evilyoutube.com/watch?list=PL123"
            )
        )
    }
}
