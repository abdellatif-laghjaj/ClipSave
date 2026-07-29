package com.abdellatif.clipsave.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that performs downloads.
 *  1) yt-dlp engine (1000+ sites, real video/audio with the chosen quality).
 *  2) Fallback to meta-scraper + OkHttp for pure image posts.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicInteger(0)
    @Volatile
    private var lastNotified = 0L
    private lateinit var repo: DownloadRepository

    override fun onCreate() {
        super.onCreate()
        repo = (application as ClipSaveApp).container.downloadRepository
        updateForeground("Starting…", 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)?.trim()
        val formatName = intent?.getStringExtra(EXTRA_FORMAT) ?: DownloadFormat.BEST.name
        val retryId = intent?.getStringExtra(EXTRA_RETRY_ID)
        val format =
            runCatching { DownloadFormat.valueOf(formatName) }.getOrDefault(DownloadFormat.BEST)
        if (url.isNullOrBlank()) {
            // Nothing was queued for this intent — only stop if no download is in flight.
            if (active.get() <= 0) stopServiceNow()
            return START_NOT_STICKY
        }
        active.incrementAndGet()
        scope.launch { runDownload(url, format, retryId) }
        return START_NOT_STICKY
    }

    private fun runDownload(url: String, format: DownloadFormat, retryId: String?) {
        val platform = Platform.fromUrl(url)
        val id = retryId ?: UUID.randomUUID().toString()
        val notifId = id.hashCode() and 0xFFFF
        val baseType = if (format.isAudio) MediaType.AUDIO else MediaType.VIDEO

        var item = repo.get(id)?.copy(
            status = DownloadStatus.EXTRACTING,
            progress = 0,
            errorMessage = null,
            mediaType = baseType
        ) ?: Download(
            id = id, url = url, platform = platform,
            status = DownloadStatus.EXTRACTING, mediaType = baseType
        )
        repo.upsert(item)
        updateForeground(platform.displayName, 0)

        var ytError: String? = null
        val ytOk = runCatching {
            item = item.copy(status = DownloadStatus.DOWNLOADING)
            repo.upsert(item)
            val res = YtDlpEngine.download(this, url, cacheDir, format, id) { p ->
                item = item.copy(progress = p)
                repo.upsert(item)
                updateForeground(item.title.ifBlank { platform.displayName }, p)
            }
            val type = if (format.isAudio) MediaType.AUDIO else guessType(res.file.extension)
            val savedUri = try {
                MediaPayloadValidator.requireValid(res.file, type)
                FileSaver.saveFile(
                    this,
                    res.file,
                    res.title.ifBlank { defaultName(platform) },
                    type
                )
            } finally {
                res.file.parentFile?.deleteRecursively()
            }
            finishSuccess(
                item.copy(title = res.title, mediaType = type, fileName = res.file.name),
                savedUri,
                notifId
            )
            true
        }.getOrElse {
            ytError = it.message
            android.util.Log.w("DownloadService", "yt-dlp path failed: ${it.message}")
            false
        }

        if (!ytOk) {
            if (format.isAudio) {
                // Audio extraction needs yt-dlp; no meaningful image fallback.
                finishFailure(item, "Audio extraction failed. ${ytError ?: ""}".trim(), notifId)
            } else runCatching {
                item = item.copy(status = DownloadStatus.EXTRACTING)
                repo.upsert(item)
                val media = ExtractorRegistry.extract(url).first()
                item = item.copy(
                    status = DownloadStatus.DOWNLOADING,
                    title = media.title.ifBlank { item.title },
                    mediaType = media.mediaType,
                    thumbnailUrl = media.thumbnailUrl
                )
                repo.upsert(item)
                val temp = downloadToTemp(
                    media.downloadUrl,
                    media.suggestedExtension ?: extOf(media.mediaType),
                    media.mediaType
                ) { p ->
                    item = item.copy(progress = p); repo.upsert(item)
                    updateForeground(item.title.ifBlank { platform.displayName }, p)
                }
                val saved = FileSaver.saveFile(
                    this,
                    temp,
                    item.title.ifBlank { defaultName(platform) },
                    media.mediaType
                )
                temp.delete()
                finishSuccess(item.copy(fileName = temp.name), saved, notifId)
            }.onFailure { err ->
                val msg = buildString {
                    append(err.message ?: "Download failed.")
                    if (ytError != null) append("  (engine: $ytError)")
                }
                finishFailure(item, msg, notifId)
            }
        }
        stopIfIdle()
    }

    private fun downloadToTemp(
        url: String,
        ext: String,
        mediaType: MediaType,
        onProgress: (Int) -> Unit
    ): File {
        val temp = File.createTempFile("dl_", ".$ext", cacheDir)
        return try {
            HttpClient.client.newCall(HttpClient.request(url, mobile = true)).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("Empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        var lastPct = -1
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = ((done * 100) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
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
        }
    }

    private fun finishSuccess(base: Download, uri: String, notifId: Int) {
        val done = base.copy(
            status = DownloadStatus.COMPLETED, progress = 100,
            localUri = uri, completedAt = System.currentTimeMillis(), errorMessage = null
        )
        repo.upsert(done)
        NotificationHelper.notifyDone(
            this, notifId, done.title.ifBlank { done.platform.displayName },
            true, "Saved to Download/${FileSaver.SUBDIR}/"
        )
    }

    private fun finishFailure(base: Download, message: String, notifId: Int) {
        val failed = base.copy(status = DownloadStatus.FAILED, errorMessage = message)
        repo.upsert(failed)
        NotificationHelper.notifyDone(
            this,
            notifId,
            failed.title.ifBlank { failed.platform.displayName },
            false,
            message
        )
    }

    private fun updateForeground(title: String, progress: Int) {
        // Progress callbacks fire on every percent; posting the notification that often
        // just gets rate-limited by the system. Throttle to ~4 updates/second.
        val now = android.os.SystemClock.elapsedRealtime()
        if (progress in 1..99 && now - lastNotified < 250) return
        lastNotified = now
        runCatching {
            val n = NotificationHelper.progressNotification(this, title, progress)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.FOREGROUND_ID,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.FOREGROUND_ID, n)
            }
        }
    }

    private fun stopIfIdle() {
        if (active.decrementAndGet() <= 0) stopServiceNow()
    }

    private fun stopServiceNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun guessType(ext: String): MediaType = when (ext.lowercase()) {
        "mp4", "webm", "mkv", "mov", "m4v" -> MediaType.VIDEO
        "mp3", "m4a", "aac", "wav", "ogg", "opus" -> MediaType.AUDIO
        "jpg", "jpeg", "png", "gif", "webp" -> MediaType.IMAGE
        else -> MediaType.VIDEO
    }

    private fun extOf(type: MediaType) = when (type) {
        MediaType.VIDEO -> "mp4"; MediaType.AUDIO -> "m4a"; MediaType.IMAGE -> "jpg"; MediaType.UNKNOWN -> "bin"
    }

    private fun defaultName(platform: Platform) =
        "${
            platform.displayName.lowercase().replace(Regex("[^a-z0-9]"), "")
        }_${System.currentTimeMillis()}"

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_RETRY_ID = "extra_retry_id"

        fun start(
            context: Context,
            url: String,
            format: DownloadFormat = DownloadFormat.BEST,
            retryId: String? = null
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FORMAT, format.name)
                if (retryId != null) putExtra(EXTRA_RETRY_ID, retryId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(
                intent
            )
            else context.startService(intent)
        }
    }
}
