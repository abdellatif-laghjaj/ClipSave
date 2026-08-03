package com.abdellatif.clipsave

import android.app.Application
import com.abdellatif.clipsave.di.AppContainer
import com.abdellatif.clipsave.download.DownloadCacheCleaner
import com.abdellatif.clipsave.download.YtDlpEngine
import com.abdellatif.clipsave.notif.NotificationHelper
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ClipSaveApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val processStartedAt = System.currentTimeMillis()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
        // Warm up yt-dlp off the main thread (first run unpacks python/ffmpeg).
        thread(start = true, isDaemon = true) {
            val repository = container.downloadRepository
            runBlocking { repository.loaded.first { it } }
            DownloadCacheCleaner.clean(
                cacheDir = cacheDir,
                retainedDownloadIds = repository.downloads.value.mapTo(mutableSetOf()) { it.id },
                createdBefore = processStartedAt
            )
            runCatching {
                if (YtDlpEngine.ensureInit(this)) {
                    // Refresh extractors so new site changes keep working.
                    runCatching { YtDlpEngine.update(this) }
                }
            }
        }
    }
}
