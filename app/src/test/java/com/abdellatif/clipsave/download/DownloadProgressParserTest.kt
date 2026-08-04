package com.abdellatif.clipsave.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressParserTest {

    @Test
    fun parsesYtDlpSizeSpeedAndEta() {
        val progress = DownloadProgressParser.parse(
            progress = 50f,
            etaSeconds = 12,
            line = "[download] 50.0% of 10.00MiB at 2.00MiB/s ETA 00:12"
        )

        assertEquals(50, progress.percent)
        assertEquals(5L * 1024 * 1024, progress.bytesDownloaded)
        assertEquals(10L * 1024 * 1024, progress.totalBytes)
        assertEquals(2L * 1024 * 1024, progress.speedBytesPerSecond)
        assertEquals(12, progress.etaSeconds)
    }

    @Test
    fun parsesAria2TransferredAndTotalBytes() {
        val progress = DownloadProgressParser.parse(
            progress = 25f,
            etaSeconds = 6,
            line = "[#abc123 2.0MiB/8.0MiB(25%) CN:4 DL:1.0MiB ETA:6s]"
        )

        assertEquals(25, progress.percent)
        assertEquals(2L * 1024 * 1024, progress.bytesDownloaded)
        assertEquals(8L * 1024 * 1024, progress.totalBytes)
        assertEquals(1L * 1024 * 1024, progress.speedBytesPerSecond)
        assertEquals(6, progress.etaSeconds)
    }
}
