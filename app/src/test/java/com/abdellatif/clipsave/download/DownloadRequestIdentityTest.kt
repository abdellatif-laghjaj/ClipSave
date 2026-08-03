package com.abdellatif.clipsave.download

import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.DownloadStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestIdentityTest {

    @Test
    fun matchesEquivalentActiveRequests() {
        val active = Download(
            id = "one",
            url = "HTTPS://Example.com:443/watch/../watch?v=42#chapter",
            format = DownloadFormat.Q720,
            status = DownloadStatus.DOWNLOADING
        )

        assertTrue(active.matchesActiveRequest("https://example.com/watch?v=42", DownloadFormat.Q720))
    }

    @Test
    fun keepsQualityAndQueryDifferencesDistinct() {
        val queued = Download(
            id = "one",
            url = "https://example.com/watch?v=42",
            format = DownloadFormat.Q720,
            status = DownloadStatus.QUEUED
        )

        assertFalse(queued.matchesActiveRequest("https://example.com/watch?v=43", DownloadFormat.Q720))
        assertFalse(queued.matchesActiveRequest("https://example.com/watch?v=42", DownloadFormat.Q1080))
    }

    @Test
    fun allowsIntentionalRedownloadAfterTransferStops() {
        val completed = Download(
            id = "one",
            url = "https://example.com/watch?v=42",
            format = DownloadFormat.BEST,
            status = DownloadStatus.COMPLETED
        )

        assertFalse(completed.matchesActiveRequest(completed.url, completed.format))
    }
}
