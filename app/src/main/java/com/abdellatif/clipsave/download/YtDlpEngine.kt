package com.abdellatif.clipsave.download

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.PlaylistPreview
import java.io.File
import java.util.Locale

/**
 * Wraps yt-dlp (via youtubedl-android). Supports 1000+ sites and merges best video+audio
 * with ffmpeg. The bundled yt-dlp is stale, so we self-update it from GitHub on first use.
 */
object YtDlpEngine {

    private const val TAG = "YtDlpEngine"
    const val PLAYLIST_PREVIEW_LIMIT = 100

    @Volatile
    private var initialized = false
    @Volatile
    private var updated = false
    @Volatile
    private var aria2Available = false
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
                aria2Available = runCatching {
                    Aria2c.getInstance().init(context.applicationContext)
                }.isSuccess
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

    fun inspectPlaylist(
        context: Context,
        url: String,
        processId: String
    ): PlaylistPreview {
        if (!ensureInit(context)) {
            throw IllegalStateException("yt-dlp not available: ${lastInitError ?: "init failed"}")
        }
        if (!updated) runCatching { update(context) }

        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--no-colors")
            addOption("--playlist-end", PLAYLIST_PREVIEW_LIMIT.toString())
        }
        val response = YoutubeDL.getInstance().execute(request, processId)
        return PlaylistParser.parse(response.out, PLAYLIST_PREVIEW_LIMIT)
            ?.takeIf { it.items.isNotEmpty() }
            ?: throw IllegalArgumentException("This link does not contain a downloadable playlist.")
    }

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
        embedSubtitles: Boolean = false,
        onProgress: (DownloadProgress) -> Unit
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
            addOption("--newline")
            addOption("-f", formatSelector(format))
            addOption("--embed-metadata")
            addOption("--embed-chapters")
            if (aria2Available) {
                // Four conservative segments improve throughput without excessive radio, CPU, or
                // server pressure. HLS/DASH remain on yt-dlp's native downloader for compatibility.
                addOption("--downloader", "libaria2c.so")
                addOption("--downloader", "dash,m3u8:native")
                val certificate = File(
                    context.noBackupFilesDir,
                    "youtubedl-android/packages/python/usr/etc/tls/cert.pem"
                )
                val ariaArgs = buildString {
                    append("aria2c:-x 4 -s 4 -j 4 -k 1M ")
                    append("--summary-interval=1 --file-allocation=none")
                    if (certificate.isFile) {
                        append(" --ca-certificate=").append(certificate.absolutePath)
                    }
                }
                // The wrapper appends its own aria arguments during execute(). Custom commands
                // are serialized afterward, so this final value wins over yt-dlp's -x16 default.
                addCommands(listOf("--external-downloader-args", ariaArgs))
            }
            if (format.isAudio) {
                addOption("-x")
                addOption(
                    "--audio-format",
                    if (format == DownloadFormat.AUDIO_MP3) "mp3" else "m4a"
                )
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
            } else {
                addOption("--merge-output-format", "mp4")
                if (embedSubtitles) {
                    addOption("--write-subs")
                    addOption("--write-auto-subs")
                    addOption(
                        "--sub-langs",
                        MediaEmbeddingOptions.subtitleLanguages(Locale.getDefault().language)
                    )
                    addOption("--sub-format", "srt/best")
                    addOption("--embed-subs")
                    addOption("--compat-options", "no-keep-subs")
                }
            }
        }

        var previous: DownloadProgress? = null
        try {
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                val progressLine = line.contains("[download]", ignoreCase = true) ||
                    line.trimStart().startsWith("[#") ||
                    line.trimStart().startsWith("size=")
                if (progress >= 0f && progressLine) {
                    val snapshot = DownloadProgressParser.parse(progress, eta, line, workDir)
                    if (snapshot != previous) {
                        previous = snapshot
                        onProgress(snapshot)
                    }
                }
            }
        } catch (error: Exception) {
            val subtitleFailure = error.message.orEmpty().contains("subtitle", ignoreCase = true) ||
                error.message.orEmpty().contains("caption", ignoreCase = true)
            if (embedSubtitles && subtitleFailure) {
                Log.w(TAG, "Subtitle embedding failed; retrying media without captions", error)
                return download(
                    context = context,
                    url = url,
                    parentDir = parentDir,
                    format = format,
                    processId = processId,
                    embedSubtitles = false,
                    onProgress = onProgress
                )
            }
            throw error
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
