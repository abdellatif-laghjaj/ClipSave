package com.abdellatif.clipsave.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.abdellatif.clipsave.data.model.MediaType
import java.io.File
import java.io.FileInputStream

/** Saves media into /storage/emulated/0/Download/ClipSave/ using MediaStore (API 29+) or legacy IO. */
object FileSaver {

    const val SUBDIR = "ClipSave"

    fun needsLegacyStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED

    fun mimeFor(mediaType: MediaType, ext: String): String {
        val e = ext.lowercase().removePrefix(".")
        return when (mediaType) {
            MediaType.VIDEO -> when (e) {
                "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; "mov" -> "video/quicktime"
                else -> "video/mp4"
            }

            MediaType.AUDIO -> when (e) {
                "mp3" -> "audio/mpeg"; "m4a", "aac" -> "audio/mp4"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"
                else -> "audio/mpeg"
            }

            MediaType.IMAGE -> when (e) {
                "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
                "avif" -> "image/avif"; "heic" -> "image/heic"; "heif" -> "image/heif"
                else -> "image/jpeg"
            }

            MediaType.UNKNOWN -> "application/octet-stream"
        }
    }

    /** Copies [source] into the public Download/ClipSave folder; returns the saved Uri/path string. */
    fun saveFile(
        context: Context,
        source: File,
        displayName: String,
        mediaType: MediaType
    ): String {
        check(!needsLegacyStoragePermission(context)) {
            "Storage access is required to save files on Android 8 and 9."
        }
        val ext = source.extension.ifBlank { defaultExt(mediaType) }
        val safeName = safeDisplayName(displayName, ext)
        val mime = mimeFor(mediaType, ext)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, safeName, mime)
        } else {
            saveLegacy(source, safeName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(
        context: Context,
        source: File,
        name: String,
        mime: String
    ): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create MediaStore entry.")
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(source).use { it.copyTo(out, 64 * 1024) }
            } ?: throw IllegalStateException("Could not open output stream.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0) {
                "Could not publish the saved file."
            }
            uri.toString()
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun saveLegacy(source: File, name: String): String {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            SUBDIR
        )
        if (!dir.exists()) dir.mkdirs()
        val dest = uniqueFile(dir, name)
        source.inputStream()
            .use { input -> dest.outputStream().use { input.copyTo(it, 64 * 1024) } }
        return Uri.fromFile(dest).toString()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isBlank()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    private fun defaultExt(mediaType: MediaType): String = when (mediaType) {
        MediaType.VIDEO -> "mp4"; MediaType.AUDIO -> "m4a"; MediaType.IMAGE -> "jpg"; MediaType.UNKNOWN -> "bin"
    }

    internal fun safeDisplayName(displayName: String, ext: String): String {
        val suffix = ext.trim().removePrefix(".")
        val base = if (suffix.isNotBlank() && displayName.endsWith(".$suffix", ignoreCase = true)) {
            displayName.dropLast(suffix.length + 1)
        } else {
            displayName
        }
        val cleanBase = base
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .trimEnd('.', ' ')
            .ifBlank { "clipsave_${System.currentTimeMillis()}" }
        if (suffix.isBlank()) return cleanBase.take(180)
        val maxBaseLength = (180 - suffix.length - 1).coerceAtLeast(1)
        return "${cleanBase.take(maxBaseLength).trimEnd('.', ' ')}.$suffix"
    }
}
