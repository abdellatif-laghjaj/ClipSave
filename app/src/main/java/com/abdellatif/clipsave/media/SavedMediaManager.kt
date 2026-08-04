package com.abdellatif.clipsave.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.download.FileSaver
import java.io.File

data class MediaDeleteResult(
    val removeHistory: Boolean,
    val message: String
)

/**
 * Owns the saved-media lifecycle after a download completes.
 *
 * MediaStore already returns secure content URIs on Android 10+. Android 8/9 downloads are
 * legacy file URIs, so those are converted to short-lived FileProvider URIs before another app
 * receives them. No broad storage access is exposed.
 */
object SavedMediaManager {

    fun buildShareIntent(context: Context, download: Download): Result<Intent> = runCatching {
        val uri = readableShareUri(context, download)
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType(context, uri, download)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TITLE,
                download.title.ifBlank { download.fileName.ifBlank { "ClipSave media" } }
            )
            clipData = ClipData.newRawUri("ClipSave media", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun delete(context: Context, download: Download): MediaDeleteResult {
        val value = download.localUri
        if (value.isNullOrBlank()) {
            return MediaDeleteResult(true, "Missing download removed from history.")
        }

        return runCatching {
            val uri = value.toUri()
            when (uri.scheme?.lowercase()) {
                "content" -> deleteContentUri(context, uri)
                "file" -> deleteFile(uri.path?.let(::File))
                null, "" -> deleteFile(File(value))
                else -> MediaDeleteResult(
                    removeHistory = false,
                    message = "This saved location cannot be deleted safely."
                )
            }
        }.getOrElse {
            MediaDeleteResult(
                removeHistory = false,
                message = "Could not delete the saved file. It remains on your device."
            )
        }
    }

    private fun deleteContentUri(context: Context, uri: Uri): MediaDeleteResult {
        val existed = canRead(context, uri)
        if (!existed) {
            return MediaDeleteResult(true, "File was already missing. Removed from history.")
        }
        return if (context.contentResolver.delete(uri, null, null) > 0) {
            MediaDeleteResult(true, "Saved file deleted.")
        } else {
            MediaDeleteResult(false, "Android did not allow this saved file to be deleted.")
        }
    }

    private fun deleteFile(file: File?): MediaDeleteResult {
        if (file == null || !file.exists()) {
            return MediaDeleteResult(true, "File was already missing. Removed from history.")
        }
        if (!file.isFile) {
            return MediaDeleteResult(false, "The saved location is not a media file.")
        }
        return if (file.delete()) {
            MediaDeleteResult(true, "Saved file deleted.")
        } else {
            MediaDeleteResult(false, "Could not delete the saved file. It remains on your device.")
        }
    }

    private fun readableShareUri(context: Context, download: Download): Uri {
        val value = download.localUri
            ?.takeIf(String::isNotBlank)
            ?: error("Saved file is no longer available.")
        val original = value.toUri()
        val shareUri = when (original.scheme?.lowercase()) {
            "content" -> original
            "file" -> providerUri(context, original.path?.let(::File))
            null, "" -> providerUri(context, File(value))
            else -> error("This saved location cannot be opened safely.")
        }
        check(canRead(context, shareUri)) { "Saved file is no longer available." }
        return shareUri
    }

    private fun providerUri(context: Context, file: File?): Uri {
        check(file != null && file.exists() && file.isFile) {
            "Saved file is no longer available."
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file
        )
    }

    private fun canRead(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun mimeType(context: Context, uri: Uri, download: Download): String {
        val resolved = context.contentResolver.getType(uri)
        if (!resolved.isNullOrBlank() && resolved != "application/octet-stream") return resolved
        val extension = download.fileName.substringAfterLast('.', "")
            .ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('.', "") }
        return FileSaver.mimeFor(download.mediaType, extension)
    }
}
