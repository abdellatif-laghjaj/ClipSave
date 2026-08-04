package com.abdellatif.clipsave.download

import java.io.File

/** Removes only ClipSave-owned transfer remnants that predate the current app process. */
internal object DownloadCacheCleaner {

    data class Result(
        val removedEntries: Int,
        val reclaimedBytes: Long
    )

    fun clean(
        cacheDir: File,
        retainedDownloadIds: Set<String>,
        createdBefore: Long
    ): Result = removeMatching(cacheDir) { entry ->
        entry.isOwnedTransferCache(retainedDownloadIds, createdBefore)
    }

    /** Safe once no yt-dlp executions remain; these are wrapper-created cookie-jar copies. */
    fun cleanTransientEngineCookies(cacheDir: File): Result = removeMatching(cacheDir) { entry ->
        entry.isFile && entry.isTransientEngineCookie()
    }

    private inline fun removeMatching(cacheDir: File, matches: (File) -> Boolean): Result {
        var removedEntries = 0
        var reclaimedBytes = 0L
        cacheDir.listFiles().orEmpty().forEach { entry ->
            if (!matches(entry)) return@forEach
            val size = entry.totalSize()
            val removed = runCatching {
                if (entry.isDirectory) entry.deleteRecursively() else entry.delete()
            }.getOrDefault(false)
            if (removed) {
                removedEntries += 1
                reclaimedBytes += size
            }
        }
        return Result(removedEntries, reclaimedBytes)
    }

    private fun File.isOwnedTransferCache(
        retainedDownloadIds: Set<String>,
        createdBefore: Long
    ): Boolean {
        if (lastModified() >= createdBefore) return false
        if (isFile) {
            return name.startsWith(DIRECT_DOWNLOAD_PREFIX) || isTransientEngineCookie()
        }
        if (!isDirectory || !name.startsWith(YT_DOWNLOAD_PREFIX)) return false
        val downloadId = name.removePrefix(YT_DOWNLOAD_PREFIX)
        return downloadId.isNotBlank() && downloadId !in retainedDownloadIds
    }

    private fun File.totalSize(): Long = when {
        isFile -> length()
        isDirectory -> walkBottomUp().filter(File::isFile).sumOf(File::length)
        else -> 0L
    }

    private fun File.isTransientEngineCookie(): Boolean =
        TRANSIENT_COOKIE_PATTERN.matches(name)

    private const val YT_DOWNLOAD_PREFIX = "yt_"
    private const val DIRECT_DOWNLOAD_PREFIX = "dl_"
    private val TRANSIENT_COOKIE_PATTERN = Regex("^tmp[0-9A-Za-z_-]+\\.cookies$")
}
