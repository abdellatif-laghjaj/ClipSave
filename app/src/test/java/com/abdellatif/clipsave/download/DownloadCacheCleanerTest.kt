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
        val staleEngineCookie = File(cache, "tmpabc_123.cookies").apply {
            writeBytes(ByteArray(3))
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
        val savedCookieName = File(cache, "cookies.txt").apply {
            writeBytes(ByteArray(2))
            setLastModified(cutoff - 2_000)
        }

        val result = DownloadCacheCleaner.clean(cache, setOf("keep"), cutoff)

        assertEquals(3, result.removedEntries)
        assertEquals(22, result.reclaimedBytes)
        assertFalse(staleYt.exists())
        assertFalse(staleDirect.exists())
        assertFalse(staleEngineCookie.exists())
        assertTrue(retained.exists())
        assertTrue(currentProcess.exists())
        assertTrue(unrelated.exists())
        assertTrue(savedCookieName.exists())
    }

    @Test
    fun removesOnlyTransientEngineCookieFilesAfterExecutionsFinish() {
        val cache = temporaryFolder.newFolder("runtime-cache")
        val first = File(cache, "tmp3jpop47c.cookies").apply { writeBytes(ByteArray(79)) }
        val second = File(cache, "tmp_cookie-2.cookies").apply { writeBytes(ByteArray(11)) }
        val importedName = File(cache, "cookies.txt").apply { writeBytes(ByteArray(5)) }
        val nearMatch = File(cache, "tmp.cookies").apply { writeBytes(ByteArray(7)) }

        val result = DownloadCacheCleaner.cleanTransientEngineCookies(cache)

        assertEquals(2, result.removedEntries)
        assertEquals(90, result.reclaimedBytes)
        assertFalse(first.exists())
        assertFalse(second.exists())
        assertTrue(importedName.exists())
        assertTrue(nearMatch.exists())
    }
}
