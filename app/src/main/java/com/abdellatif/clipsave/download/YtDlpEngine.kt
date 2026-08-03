package com.abdellatif.clipsave.download

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.abdellatif.clipsave.data.model.DownloadFormat
import java.io.File

/**
 * Wraps yt-dlp (via youtubedl-android). Supports 1000+ sites and merges best video+audio
 * with ffmpeg. The bundled yt-dlp is stale, so we self-update it from GitHub on first use.
 */
object YtDlpEngine {

    private const val TAG = "YtDlpEngine"

    @Volatile
    private var initialized = false
    @Volatile
    private var updated = false
    @Volatile
    var lastInitError: String? = null
        private set
    @Volatile
    var ytdlpVersion: String? = null
        private set

    fun ensureInit(context: Context): Boolean {
        if (initialized) return true
        synchronized(this) {
            if (initialized) return true
            return try {
                YoutubeDL.getInstance().init(context.applicationContext)
                runCatching { FFmpeg.getInstance().init(context.applicationContext) }
                runCatching { Aria2c.getInstance().init(context.applicationContext) }
                initialized = true
                lastInitError = null
                ytdlpVersion = runCatching {
                    YoutubeDL.getInstance().version(context.applicationContext)
                }.getOrNull()
                true
            } catch (t: Throwable) {
                lastInitError = t.message
                Log.e(TAG, "yt-dlp init failed", t)
                false
            }
        }
    }

    /** Pull the latest yt-dlp extractors. Safe to call repeatedly; cheap if already current. */
    fun update(context: Context, force: Boolean = false): String {
        if (!ensureInit(context)) return "Engine not available: ${lastInitError ?: "init failed"}"
        if (updated && !force) return "Already updated this session (yt-dlp ${ytdlpVersion ?: "?"})"
        return try {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
            updated = true
            ytdlpVersion = runCatching {
                YoutubeDL.getInstance().version(context.applicationContext)
            }.getOrNull()
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> "Updated to yt-dlp ${ytdlpVersion ?: "latest"}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already up to date (yt-dlp ${ytdlpVersion ?: "?"})"
                else -> "Update finished"
            }
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp update failed", t)
            "Update failed: ${t.message}"
        }
    }

    data class YtResult(val file: File, val title: String)

    private fun formatSelector(format: DownloadFormat): String = when (format) {
        DownloadFormat.BEST -> "bv*+ba/b"
        DownloadFormat.Q1080 -> "bv*[height<=1080]+ba/b[height<=1080]/b"
        DownloadFormat.Q720 -> "bv*[height<=720]+ba/b[height<=720]/b"
        DownloadFormat.Q480 -> "bv*[height<=480]+ba/b[height<=480]/b"
        DownloadFormat.AUDIO_M4A, DownloadFormat.AUDIO_MP3 -> "bestaudio/best"
    }

    fun download(
        context: Context,
        url: String,
        parentDir: File,
        format: DownloadFormat,
        processId: String,
        onProgress: (Int) -> Unit
    ): YtResult {
        if (!ensureInit(context)) {
            throw IllegalStateException("yt-dlp not available: ${lastInitError ?: "init failed"}")
        }
        // Best effort: make sure extractors are fresh before the first real download.
        if (!updated) runCatching { update(context) }

        // Keep an existing work directory so a paused or interrupted transfer can continue.
        val workDir = File(parentDir, "yt_$processId").apply { mkdirs() }
        val request = YoutubeDLRequest(url).apply {
            addOption("-o", File(workDir, "%(title).80s.%(ext)s").absolutePath)
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--restrict-filenames")
            addOption("--no-warnings")
            addOption("--continue")
            addOption("-f", formatSelector(format))
            if (format.isAudio) {
                addOption("-x")
                addOption(
                    "--audio-format",
                    if (format == DownloadFormat.AUDIO_MP3) "mp3" else "m4a"
                )
            } else {
                addOption("--merge-output-format", "mp4")
            }
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
            if (progress >= 0f) onProgress(progress.toInt().coerceIn(0, 100))
        }

        // The merged video (or the extracted audio) is the largest file in the work dir.
        val produced = workDir.listFiles()
            ?.filter {
                it.isFile &&
                    it.length() > 0 &&
                    !it.name.endsWith(".part") &&
                    !it.name.endsWith(".ytdl") &&
                    !it.name.contains(".frag")
            }
            ?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("yt-dlp produced no file.")

        val title = produced.nameWithoutExtension.replace('_', ' ').trim()
        return YtResult(produced, title)
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    fun cleanup(context: Context, processId: String) {
        File(context.cacheDir, "yt_$processId").deleteRecursively()
    }

    fun cleanupIfNoPartial(context: Context, processId: String) {
        val workDir = File(context.cacheDir, "yt_$processId")
        val hasResumableData = workDir.listFiles()?.any {
            it.isFile && it.length() > 0 && it.name.endsWith(".part")
        } == true
        if (!hasResumableData) workDir.deleteRecursively()
    }
}
