package com.abdellatif.clipsave.download

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadCacheCleanerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removesOnlyStaleUntrackedTransferCache() {
        val cache = temporaryFolder.newFolder("cache")
        val cutoff = System.currentTimeMillis()
        val staleYt = File(cache, "yt_orphan").apply {
            mkdir()
            File(this, "video.part").writeBytes(ByteArray(12))
            setLastModified(cutoff - 2_000)
        }
        val staleDirect = File(cache, "dl_old.mp4").apply {
            writeBytes(ByteArray(7))
            setLastModified(cutoff - 2_000)
        }
        val retained = File(cache, "yt_keep").apply {
            mkdir()
            File(this, "audio.part").writeBytes(ByteArray(5))
            setLastModified(cutoff - 2_000)
        }
        val currentProcess = File(cache, "yt_new").apply {
            mkdir()
            setLastModified(cutoff + 1_000)
        }
        val unrelated = File(cache, "image_cache").apply {
            mkdir()
            setLastModified(cutoff - 2_000)
        }

        val result = DownloadCacheCleaner.clean(cache, setOf("keep"), cutoff)

        assertEquals(2, result.removedEntries)
        assertEquals(19, result.reclaimedBytes)
        assertFalse(staleYt.exists())
        assertFalse(staleDirect.exists())
        assertTrue(retained.exists())
        assertTrue(currentProcess.exists())
        assertTrue(unrelated.exists())
    }
}
