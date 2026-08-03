package com.abdellatif.clipsave.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.abdellatif.clipsave.ClipSaveApp
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.DownloadStatus
import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.data.repository.DownloadRepository
import com.abdellatif.clipsave.extractor.ExtractorRegistry
import com.abdellatif.clipsave.network.HttpClient
import com.abdellatif.clipsave.notif.NotificationHelper
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call

/**
 * Foreground FIFO download queue.
 *
 * At most [MAX_CONCURRENT_DOWNLOADS] engine jobs run together. This keeps Python/ffmpeg memory and
 * battery usage predictable while still allowing a second independent transfer to make progress.
 * Partial yt-dlp files are retained when paused or failed so retry can continue instead of starting
 * over; completed and explicitly removed items are cleaned immediately.
 */
class DownloadService : Service() {

    private data class StartRequest(
        val url: String,
        val format: DownloadFormat,
        val id: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val activeCount = AtomicInteger(0)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val pauseRequests = ConcurrentHashMap.newKeySet<String>()
    private val removedIds = ConcurrentHashMap.newKeySet<String>()
    private val restartRequests = ConcurrentHashMap<String, StartRequest>()

    @Volatile
    private var lastNotified = 0L
    private lateinit var repo: DownloadRepository

    override fun onCreate() {
        super.onCreate()
        repo = (application as ClipSaveApp).container.downloadRepository
        updateForeground("Preparing download", 0, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.let(::pauseDownload)
            ACTION_REMOVE -> intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.let(::removeDownload)
            ACTION_CLEAR_ALL -> clearAllDownloads()
            else -> enqueueFrom(intent)
        }
        if (activeCount.get() <= 0 && jobs.isEmpty()) stopServiceNow()
        return START_NOT_STICKY
    }

    private fun enqueueFrom(intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        if (url.isBlank()) return
        val format = runCatching {
            DownloadFormat.valueOf(intent?.getStringExtra(EXTRA_FORMAT).orEmpty())
        }.getOrDefault(DownloadFormat.BEST)
        val id = intent?.getStringExtra(EXTRA_RETRY_ID) ?: UUID.randomUUID().toString()
        enqueue(StartRequest(url, format, id))
    }

    private fun enqueue(request: StartRequest) {
        val existing = jobs[request.id]
        if (existing != null) {
            // A quick Resume tap can arrive before the old native process fully exits.
            // Coalesce repeated taps and restart exactly once after cleanup finishes.
            restartRequests[request.id] = request
            return
        }

        pauseRequests.remove(request.id)
        removedIds.remove(request.id)
        val platform = Platform.fromUrl(request.url)
        val mediaType = if (request.format.isAudio) MediaType.AUDIO else MediaType.VIDEO
        val queued = repo.get(request.id)?.copy(
            url = request.url,
            platform = platform,
            mediaType = mediaType,
            format = request.format,
            status = DownloadStatus.QUEUED,
            errorMessage = null
        ) ?: Download(
            id = request.id,
            url = request.url,
            platform = platform,
            mediaType = mediaType,
            format = request.format,
            status = DownloadStatus.QUEUED
        )
        repo.upsert(queued)

        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                slots.withPermit { runDownload(request) }
            } finally {
                if (request.id in removedIds) {
                    YtDlpEngine.cleanup(this@DownloadService, request.id)
                }
                jobs.remove(request.id, job)
                val remaining = activeCount.decrementAndGet()
                val restart = restartRequests.remove(request.id)
                if (restart != null && request.id !in removedIds) {
                    enqueue(restart)
                } else if (remaining <= 0) {
                    stopServiceNow()
                }
            }
        }
        if (jobs.putIfAbsent(request.id, job) == null) {
            activeCount.incrementAndGet()
            job.start()
            updateForeground(
                if (activeCount.get() == 1) platform.displayName
                else "${activeCount.get()} downloads queued",
                0,
                request.id
            )
        } else {
            job.cancel()
            restartRequests[request.id] = request
        }
    }

    private suspend fun runDownload(request: StartRequest) {
        val platform = Platform.fromUrl(request.url)
        val notifId = request.id.hashCode() and 0xFFFF
        val baseType = if (request.format.isAudio) MediaType.AUDIO else MediaType.VIDEO
        var item = repo.get(request.id)?.copy(
            status = DownloadStatus.EXTRACTING,
            progress = 0,
            errorMessage = null,
            mediaType = baseType,
            format = request.format
        ) ?: return
        publish(item)
        updateForeground(platform.displayName, 0, request.id)

        var ytError: String? = null
        try {
            val ytOk = try {
                ensureRunning(request.id)
                item = item.copy(status = DownloadStatus.DOWNLOADING)
                publish(item)
                val result = YtDlpEngine.download(
                    this,
                    request.url,
                    cacheDir,
                    request.format,
                    request.id
                ) { progress ->
                    if (request.id !in pauseRequests && request.id !in removedIds) {
                        item = item.copy(progress = progress)
                        publish(item)
                        updateForeground(
                            item.title.ifBlank { platform.displayName },
                            progress,
                            request.id
                        )
                    }
                }
                ensureRunning(request.id)
                val type = if (request.format.isAudio) {
                    MediaType.AUDIO
                } else {
                    guessType(result.file.extension)
                }
                MediaPayloadValidator.requireValid(result.file, type)
                ensureRunning(request.id)
                val savedUri = FileSaver.saveFile(
                    this,
                    result.file,
                    result.title.ifBlank { defaultName(platform) },
                    type
                )
                finishSuccess(
                    item.copy(
                        title = result.title,
                        mediaType = type,
                        fileName = result.file.name
                    ),
                    savedUri,
                    notifId
                )
                YtDlpEngine.cleanup(this, request.id)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                ytError = error.message
                android.util.Log.w("DownloadService", "yt-dlp path failed: ${error.message}")
                false
            }

            if (!ytOk) {
                ensureRunning(request.id)
                if (request.format.isAudio) {
                    finishFailure(
                        item,
                        "Audio extraction failed. ${ytError.orEmpty()}".trim(),
                        notifId
                    )
                } else {
                    try {
                        item = item.copy(status = DownloadStatus.EXTRACTING)
                        publish(item)
                        val media = ExtractorRegistry.extract(request.url).first()
                        ensureRunning(request.id)
                        item = item.copy(
                            status = DownloadStatus.DOWNLOADING,
                            title = media.title.ifBlank { item.title },
                            mediaType = media.mediaType,
                            thumbnailUrl = media.thumbnailUrl
                        )
                        publish(item)
                        val temp = downloadToTemp(
                            request.id,
                            media.downloadUrl,
                            media.suggestedExtension ?: extOf(media.mediaType),
                            media.mediaType
                        ) { progress ->
                            item = item.copy(progress = progress)
                            publish(item)
                            updateForeground(
                                item.title.ifBlank { platform.displayName },
                                progress,
                                request.id
                            )
                        }
                        ensureRunning(request.id)
                        val savedUri = FileSaver.saveFile(
                            this,
                            temp,
                            item.title.ifBlank { defaultName(platform) },
                            media.mediaType
                        )
                        temp.delete()
                        finishSuccess(item.copy(fileName = temp.name), savedUri, notifId)
                        YtDlpEngine.cleanup(this, request.id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        val message = buildString {
                            append(error.message ?: "Download failed.")
                            if (ytError != null) append(" (engine: $ytError)")
                        }
                        finishFailure(item, message, notifId)
                    }
                }
            }
        } catch (_: CancellationException) {
            // Pause and removal are expected control flow. Their UI state is set by the action.
        }
    }

    private suspend fun downloadToTemp(
        id: String,
        url: String,
        ext: String,
        mediaType: MediaType,
        onProgress: (Int) -> Unit
    ): File {
        val temp = File.createTempFile("dl_", ".$ext", cacheDir)
        val call = HttpClient.client.newCall(HttpClient.request(url, mobile = true))
        activeCalls[id] = call
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var completed = 0L
                        var lastPercent = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            completed += read
                            if (total > 0) {
                                val percent = ((completed * 100) / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                MediaPayloadValidator.requireValid(
                    temp,
                    mediaType,
                    body.contentType()?.toString()
                )
            }
            temp
        } catch (error: Throwable) {
            temp.delete()
            throw error
        } finally {
            activeCalls.remove(id, call)
        }
    }

    private fun pauseDownload(id: String) {
        val item = repo.get(id) ?: return
        if (item.status !in BUSY_STATUSES) return
        pauseRequests += id
        restartRequests.remove(id)
        repo.upsert(
            item.copy(
                status = DownloadStatus.PAUSED,
                errorMessage = null
            )
        )
        jobs[id]?.cancel()
        activeCalls.remove(id)?.cancel()
        YtDlpEngine.cancel(id)
    }

    private fun removeDownload(id: String) {
        removedIds += id
        pauseRequests.remove(id)
        restartRequests.remove(id)
        val job = jobs[id]
        job?.cancel()
        activeCalls.remove(id)?.cancel()
        YtDlpEngine.cancel(id)
        repo.remove(id)
        if (job == null) {
            activeCount.incrementAndGet()
            scope.launch {
                try {
                    YtDlpEngine.cleanup(this@DownloadService, id)
                } finally {
                    if (activeCount.decrementAndGet() <= 0) stopServiceNow()
                }
            }
        }
    }

    private fun clearAllDownloads() {
        val ids = (repo.downloads.value.map { it.id } + jobs.keys).toSet()
        ids.forEach { id ->
            removedIds += id
            restartRequests.remove(id)
            jobs[id]?.cancel()
            activeCalls.remove(id)?.cancel()
            YtDlpEngine.cancel(id)
        }
        repo.clearAll()
        val inactiveIds = ids.filterNot(jobs::containsKey)
        if (inactiveIds.isNotEmpty()) {
            activeCount.incrementAndGet()
            scope.launch {
                try {
                    inactiveIds.forEach { id -> YtDlpEngine.cleanup(this@DownloadService, id) }
                } finally {
                    if (activeCount.decrementAndGet() <= 0) stopServiceNow()
                }
            }
        }
    }

    private suspend fun ensureRunning(id: String) {
        currentCoroutineContext().ensureActive()
        if (id in pauseRequests || id in removedIds) throw CancellationException()
    }

    private fun publish(download: Download) {
        if (download.id !in pauseRequests && download.id !in removedIds) repo.upsert(download)
    }

    private fun finishSuccess(base: Download, uri: String, notifId: Int) {
        if (base.id in removedIds) return
        val completed = base.copy(
            status = DownloadStatus.COMPLETED,
            progress = 100,
            localUri = uri,
            completedAt = System.currentTimeMillis(),
            errorMessage = null
        )
        pauseRequests.remove(base.id)
        repo.upsert(completed)
        NotificationHelper.notifyDone(
            this,
            notifId,
            completed.title.ifBlank { completed.platform.displayName },
            true,
            "Saved to Download/${FileSaver.SUBDIR}/",
            completed
        )
    }

    private fun finishFailure(base: Download, message: String, notifId: Int) {
        if (base.id in pauseRequests || base.id in removedIds) return
        val failed = base.copy(status = DownloadStatus.FAILED, errorMessage = message)
        repo.upsert(failed)
        YtDlpEngine.cleanupIfNoPartial(this, base.id)
        NotificationHelper.notifyDone(
            this,
            notifId,
            failed.title.ifBlank { failed.platform.displayName },
            false,
            message
        )
    }

    private fun updateForeground(title: String, progress: Int, downloadId: String?) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (progress in 1..99 && now - lastNotified < NOTIFICATION_INTERVAL_MS) return
        lastNotified = now
        runCatching {
            val notification = NotificationHelper.progressNotification(
                this,
                title,
                progress,
                downloadId
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.FOREGROUND_ID, notification)
            }
        }
    }

    private fun stopServiceNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        activeCalls.values.forEach(Call::cancel)
        jobs.values.forEach(Job::cancel)
        scope.cancel()
        super.onDestroy()
    }

    private fun guessType(extension: String): MediaType = when (extension.lowercase()) {
        "mp4", "webm", "mkv", "mov", "m4v" -> MediaType.VIDEO
        "mp3", "m4a", "aac", "wav", "ogg", "opus" -> MediaType.AUDIO
        "jpg", "jpeg", "png", "gif", "webp" -> MediaType.IMAGE
        else -> MediaType.VIDEO
    }

    private fun extOf(type: MediaType) = when (type) {
        MediaType.VIDEO -> "mp4"
        MediaType.AUDIO -> "m4a"
        MediaType.IMAGE -> "jpg"
        MediaType.UNKNOWN -> "bin"
    }

    private fun defaultName(platform: Platform) =
        "${platform.displayName.lowercase().replace(Regex("[^a-z0-9]"), "")}_${System.currentTimeMillis()}"

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_RETRY_ID = "extra_retry_id"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        const val ACTION_PAUSE = "com.abdellatif.clipsave.action.PAUSE_DOWNLOAD"
        private const val ACTION_REMOVE = "com.abdellatif.clipsave.action.REMOVE_DOWNLOAD"
        private const val ACTION_CLEAR_ALL = "com.abdellatif.clipsave.action.CLEAR_DOWNLOADS"
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val NOTIFICATION_INTERVAL_MS = 250L

        private val BUSY_STATUSES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.EXTRACTING,
            DownloadStatus.DOWNLOADING
        )

        fun start(
            context: Context,
            url: String,
            format: DownloadFormat = DownloadFormat.BEST,
            retryId: String? = null
        ) {
            startService(
                context,
                Intent(context, DownloadService::class.java).apply {
                    putExtra(EXTRA_URL, url)
                    putExtra(EXTRA_FORMAT, format.name)
                    if (retryId != null) putExtra(EXTRA_RETRY_ID, retryId)
                }
            )
        }

        fun pause(context: Context, id: String) {
            startService(
                context,
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_PAUSE)
                    .putExtra(EXTRA_DOWNLOAD_ID, id)
            )
        }

        fun remove(context: Context, id: String) {
            startService(
                context,
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_REMOVE)
                    .putExtra(EXTRA_DOWNLOAD_ID, id)
            )
        }

        fun clearAll(context: Context) {
            startService(
                context,
                Intent(context, DownloadService::class.java).setAction(ACTION_CLEAR_ALL)
            )
        }

        private fun startService(context: Context, intent: Intent) {
            context.startForegroundService(intent)
        }
    }
}
