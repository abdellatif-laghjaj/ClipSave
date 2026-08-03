package com.abdellatif.clipsave.data.repository

import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRepositoryTest {

    @Test
    fun recoversOnlyInterruptedWorkAsPaused() {
        val downloads = listOf(
            Download(id = "queued", url = "https://example.com/1"),
            Download(
                id = "active",
                url = "https://example.com/2",
                status = DownloadStatus.DOWNLOADING
            ),
            Download(
                id = "done",
                url = "https://example.com/3",
                status = DownloadStatus.COMPLETED
            )
        )

        val recovered = downloads.recoverInterruptedDownloads()

        assertEquals(DownloadStatus.PAUSED, recovered[0].status)
        assertEquals(DownloadStatus.PAUSED, recovered[1].status)
        assertEquals(DownloadStatus.COMPLETED, recovered[2].status)
        assertEquals(
            "Interrupted before completion. Tap Resume to continue.",
            recovered[0].errorMessage
        )
        assertNull(recovered[2].errorMessage)
    }
}
