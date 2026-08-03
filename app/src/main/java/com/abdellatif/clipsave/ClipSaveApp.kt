package com.abdellatif.clipsave

import android.app.Application
import android.os.Handler
import android.os.Looper
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
        // Recovery is cheap and starts immediately so stale transfer files do not accumulate.
        thread(start = true, isDaemon = true) {
            val repository = container.downloadRepository
            runBlocking { repository.loaded.first { it } }
            DownloadCacheCleaner.clean(
                cacheDir = cacheDir,
                retainedDownloadIds = repository.downloads.value.mapTo(mutableSetOf()) { it.id },
                createdBefore = processStartedAt
            )
        }
        // Native Python/ffmpeg initialization is intentionally deferred until after the first UI
        // frame. A direct download can still initialize on demand through the synchronized engine.
        Handler(Looper.getMainLooper()).postDelayed(
            {
                thread(start = true, isDaemon = true) {
                    runCatching {
                        if (YtDlpEngine.ensureInit(this)) {
                            // Site extractors refresh only when the 24-hour policy says they are due.
                            runCatching { YtDlpEngine.update(this) }
                        }
                    }
                }
            },
            ENGINE_WARMUP_DELAY_MS
        )
    }

    private companion object {
        const val ENGINE_WARMUP_DELAY_MS = 2_500L
    }
}
