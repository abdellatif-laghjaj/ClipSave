package com.abdellatif.clipsave.download

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaEmbeddingOptionsTest {
    @Test
    fun `uses device language with English fallback`() {
        assertEquals("fr,fr-orig,en,en-orig", MediaEmbeddingOptions.subtitleLanguages("fr"))
    }

    @Test
    fun `does not duplicate English and normalizes legacy codes`() {
        assertEquals("en,en-orig", MediaEmbeddingOptions.subtitleLanguages("EN"))
        assertEquals("he,he-orig,en,en-orig", MediaEmbeddingOptions.subtitleLanguages("iw"))
    }

    @Test
    fun `falls back safely for malformed locale`() {
        assertEquals("en,en-orig", MediaEmbeddingOptions.subtitleLanguages("../../all"))
        assertEquals("en,en-orig", MediaEmbeddingOptions.subtitleLanguages(""))
    }
}
