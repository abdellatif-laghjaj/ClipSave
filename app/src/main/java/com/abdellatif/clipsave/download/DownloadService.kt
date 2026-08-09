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
import com.abdellatif.clipsave.data.preferences.NetworkPolicy
import com.abdellatif.clipsave.data.preferences.UserPreferences
import com.abdellatif.clipsave.data.repository.DownloadRepository
import com.abdellatif.clipsave.extractor.DirectMediaUrl
import com.abdellatif.clipsave.extractor.ExtractorRegistry
import com.abdellatif.clipsave.extractor.ImageDownloadResolver
import com.abdellatif.clipsave.extractor.MediaInfo
import com.abdellatif.clipsave.network.HttpClient
import com.abdellatif.clipsave.network.NetworkMonitor
import com.abdellatif.clipsave.network.NetworkState
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private lateinit var prefs: UserPreferences
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()
        val container = (application as ClipSaveApp).container
        repo = container.downloadRepository
        prefs = container.userPreferences
        networkMonitor = NetworkMonitor(this)
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

        // onStartCommand is serialized on the main thread, so this check and the upsert below
        // form one queue-boundary decision even when several share/paste intents arrive together.
        val duplicate = repo.downloads.value.firstOrNull {
            it.id != request.id && it.matchesActiveRequest(request.url, request.format)
        }
        if (duplicate != null) {
            updateForeground(
                duplicate.title.ifBlank { "Already in download queue" },
                duplicate.progress,
                duplicate.id
            )
            return
        }

        pauseRequests.remove(request.id)
        removedIds.remove(request.id)
        val platform = Platform.fromUrl(request.url)
        val mediaType = requestedMediaType(request.url, request.format)
        val queued = repo.get(request.id)?.copy(
            url = request.url,
            platform = platform,
            mediaType = mediaType,
            format = request.format,
            status = DownloadStatus.QUEUED,
            speedBytesPerSecond = 0,
            etaSeconds = -1,
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
                awaitEligibleNetwork(request)
                slots.withPermit {
                    ensureRunning(request.id)
                    val networkGuard = monitorNetworkEligibility(request)
                    try {
                        runDownload(request)
                    } finally {
                        networkGuard.cancel()
                    }
                }
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

    private suspend fun awaitEligibleNetwork(request: StartRequest) {
        val policy = prefs.settings.first().networkPolicy
        val network = networkMonitor.state.value
        if (!network.isEligible(policy)) {
            val message = waitingMessage(policy, network)
            repo.get(request.id)?.let { current ->
                publish(
                    current.copy(
                        status = DownloadStatus.WAITING_FOR_NETWORK,
                        speedBytesPerSecond = 0,
                        etaSeconds = -1,
                        errorMessage = message
                    )
                )
            }
            updateForeground(
                if (network.connected) "Waiting for an unmetered network" else "Waiting for internet",
                0,
                request.id
            )
        }

        combine(networkMonitor.state, prefs.settings) { state, settings ->
            state.isEligible(settings.networkPolicy)
        }.first { it }
        ensureRunning(request.id)
        repo.get(request.id)?.takeIf {
            it.status == DownloadStatus.WAITING_FOR_NETWORK
        }?.let { waiting ->
            publish(waiting.copy(status = DownloadStatus.QUEUED, errorMessage = null))
        }
    }

    private fun monitorNetworkEligibility(request: StartRequest): Job = scope.launch {
        combine(networkMonitor.state, prefs.settings) { state, settings ->
            state.isEligible(settings.networkPolicy)
        }
            .distinctUntilChanged()
            .first { eligible -> !eligible }

        val current = repo.get(request.id) ?: return@launch
        if (current.status !in BUSY_STATUSES || request.id in pauseRequests ||
            request.id in removedIds
        ) {
            return@launch
        }
        val policy = prefs.settings.first().networkPolicy
        val network = networkMonitor.state.value
        repo.upsert(
            current.copy(
                status = DownloadStatus.WAITING_FOR_NETWORK,
                speedBytesPerSecond = 0,
                etaSeconds = -1,
                errorMessage = waitingMessage(policy, network)
            )
        )
        restartRequests[request.id] = request
        activeCalls.remove(request.id)?.cancel()
        YtDlpEngine.cancel(request.id)
        jobs[request.id]?.cancel()
    }

    private fun waitingMessage(policy: NetworkPolicy, network: NetworkState): String = when {
        !network.connected -> "Waiting for an internet connection. Download starts automatically."
        policy == NetworkPolicy.UNMETERED_ONLY ->
            "Waiting for an unmetered connection. Download starts automatically."
        else -> "Waiting for a usable network. Download starts automatically."
    }

    private suspend fun runDownload(request: StartRequest) {
        val downloadSettings = prefs.settings.first()
        val platform = Platform.fromUrl(request.url)
        val notifId = request.id.hashCode() and 0xFFFF
        val baseType = requestedMediaType(request.url, request.format)
        var item = repo.get(request.id)?.copy(
            status = DownloadStatus.EXTRACTING,
            progress = 0,
            speedBytesPerSecond = 0,
            etaSeconds = -1,
            errorMessage = null,
            mediaType = baseType,
            format = request.format
        ) ?: return
        publish(item)
        updateForeground(platform.displayName, 0, request.id)

        var ytError: String? = null
        var engineImage: MediaInfo? = null
        try {
            if (!request.format.isAudio) {
                val image = ImageDownloadResolver.resolve(request.url, platform)
                if (image != null) {
                    try {
                        downloadResolvedMedia(request, item, image, platform, notifId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        finishFailure(item.copy(mediaType = MediaType.IMAGE), error.message ?: "Image download failed.", notifId)
                    }
                    return
                }
            }

            val ytOk = try {
                ensureRunning(request.id)
                item = item.copy(status = DownloadStatus.DOWNLOADING)
                publish(item)
                val result = YtDlpEngine.download(
                    this,
                    request.url,
                    cacheDir,
                    request.format,
                    request.id,
                    embedSubtitles = downloadSettings.embedSubtitles
                ) { progress ->
                    if (request.id !in pauseRequests && request.id !in removedIds) {
                        item = item.withProgress(progress)
                        publish(item)
                        updateForeground(
                            item.title.ifBlank { platform.displayName },
                            progress.percent,
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
                        fileName = result.file.name,
                        bytesDownloaded = result.file.length(),
                        totalBytes = result.file.length()
                    ),
                    savedUri,
                    notifId
                )
                YtDlpEngine.cleanup(this, request.id)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ytError = error.message
                engineImage = DirectMediaUrl.imageFromText(
                    error.message.orEmpty(),
                    item.title.ifBlank {
                        if (platform == Platform.REDDIT) "Reddit image" else ""
                    }
                )
                android.util.Log.w("DownloadService", "yt-dlp path failed: ${error.message}")
                false
            }

            if (!ytOk) {
                ensureRunning(request.id)
                if (!request.format.isAudio && engineImage != null) {
                    try {
                        downloadResolvedMedia(
                            request,
                            item,
                            requireNotNull(engineImage),
                            platform,
                            notifId
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        finishFailure(
                            item.copy(mediaType = MediaType.IMAGE),
                            error.message ?: "Image download failed.",
                            notifId
                        )
                    }
                    return
                }
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
                        downloadResolvedMedia(request, item, media, platform, notifId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
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

    private suspend fun downloadResolvedMedia(
        request: StartRequest,
        base: Download,
        media: MediaInfo,
        platform: Platform,
        notifId: Int
    ) {
        ensureRunning(request.id)
        var item = base.copy(
            status = DownloadStatus.DOWNLOADING,
            title = media.title.ifBlank { base.title },
            mediaType = media.mediaType,
            thumbnailUrl = media.thumbnailUrl ?: media.downloadUrl.takeIf {
                media.mediaType == MediaType.IMAGE
            }
        )
        publish(item)
        val temp = downloadToTemp(
            request.id,
            media.downloadUrl,
            media.suggestedExtension ?: extOf(media.mediaType),
            media.mediaType
        ) { progress ->
            item = item.withProgress(progress)
            publish(item)
            updateForeground(
                item.title.ifBlank { platform.displayName },
                progress.percent,
                request.id
            )
        }
        ensureRunning(request.id)
        val displayTitle = item.title.ifBlank { defaultName(platform) }
        val savedFileName = FileSaver.safeDisplayName(displayTitle, temp.extension)
        val savedUri = FileSaver.saveFile(
            this,
            temp,
            displayTitle,
            media.mediaType
        )
        val savedSize = temp.length()
        temp.delete()
        finishSuccess(
            item.copy(
                fileName = savedFileName,
                bytesDownloaded = savedSize,
                totalBytes = savedSize
            ),
            savedUri,
            notifId
        )
        YtDlpEngine.cleanup(this, request.id)
    }

    private suspend fun downloadToTemp(
        id: String,
        url: String,
        ext: String,
        mediaType: MediaType,
        onProgress: (DownloadProgress) -> Unit
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
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var completed = 0L
                        var lastPercent = -1
                        var lastBytes = 0L
                        var lastSampleAt = android.os.SystemClock.elapsedRealtime()
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            completed += read
                            val now = android.os.SystemClock.elapsedRealtime()
                            val percent = if (total > 0) {
                                ((completed * 100) / total).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            val elapsed = now - lastSampleAt
                            if (percent != lastPercent || elapsed >= PROGRESS_INTERVAL_MS) {
                                val speed = if (elapsed >= MIN_SPEED_SAMPLE_MS) {
                                    ((completed - lastBytes) * 1_000L / elapsed).coerceAtLeast(0)
                                } else {
                                    0
                                }
                                val eta = if (total > completed && speed > 0) {
                                    (total - completed) / speed
                                } else {
                                    -1
                                }
                                lastPercent = percent
                                lastBytes = completed
                                lastSampleAt = now
                                onProgress(
                                    DownloadProgress(
                                        percent = percent,
                                        bytesDownloaded = completed,
                                        totalBytes = total.coerceAtLeast(0),
                                        speedBytesPerSecond = speed,
                                        etaSeconds = eta
                                    )
                                )
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
        } catch (error: Exception) {
            temp.delete()
            throw error
        } finally {
            activeCalls.remove(id, call)
        }
    }

    private fun pauseDownload(id: String, reason: String? = null) {
        val item = repo.get(id) ?: return
        if (item.status !in BUSY_STATUSES) return
        pauseRequests += id
        restartRequests.remove(id)
        repo.upsert(
            item.copy(
                status = DownloadStatus.PAUSED,
                speedBytesPerSecond = 0,
                etaSeconds = -1,
                errorMessage = reason
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

    private fun Download.withProgress(progress: DownloadProgress): Download = copy(
        progress = progress.percent,
        bytesDownloaded = progress.bytesDownloaded.takeIf { it > 0 } ?: bytesDownloaded,
        totalBytes = progress.totalBytes.takeIf { it > 0 } ?: totalBytes,
        speedBytesPerSecond = progress.speedBytesPerSecond,
        etaSeconds = progress.etaSeconds
    )

    private fun finishSuccess(base: Download, uri: String, notifId: Int) {
        if (base.id in removedIds) return
        val completed = base.copy(
            status = DownloadStatus.COMPLETED,
            progress = 100,
            bytesDownloaded = maxOf(base.bytesDownloaded, base.totalBytes),
            totalBytes = maxOf(base.bytesDownloaded, base.totalBytes),
            speedBytesPerSecond = 0,
            etaSeconds = 0,
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
        val failed = base.copy(
            status = DownloadStatus.FAILED,
            speedBytesPerSecond = 0,
            etaSeconds = -1,
            errorMessage = message
        )
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

    override fun onTimeout(startId: Int, fgsType: Int) {
        repo.downloads.value
            .filter { it.status in BUSY_STATUSES }
            .forEach { pauseDownload(it.id, FGS_TIMEOUT_MESSAGE) }
        stopServiceNow()
    }

    override fun onDestroy() {
        activeCalls.values.forEach(Call::cancel)
        jobs.values.forEach(Job::cancel)
        networkMonitor.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun guessType(extension: String): MediaType = when (extension.lowercase()) {
        "mp4", "webm", "mkv", "mov", "m4v" -> MediaType.VIDEO
        "mp3", "m4a", "aac", "wav", "ogg", "opus" -> MediaType.AUDIO
        "jpg", "jpeg", "png", "gif", "webp" -> MediaType.IMAGE
        else -> MediaType.VIDEO
    }

    private fun requestedMediaType(url: String, format: DownloadFormat): MediaType = when {
        format.isAudio -> MediaType.AUDIO
        DirectMediaUrl.isImage(url) -> MediaType.IMAGE
        else -> MediaType.UNKNOWN
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
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MIN_SPEED_SAMPLE_MS = 250L
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val FGS_TIMEOUT_MESSAGE =
            "Android paused this long-running background download. Tap Resume to continue."

        private val BUSY_STATUSES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.WAITING_FOR_NETWORK,
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
