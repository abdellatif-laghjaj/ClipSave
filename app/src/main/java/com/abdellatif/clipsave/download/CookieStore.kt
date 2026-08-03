package com.abdellatif.clipsave.download

import android.content.Context
import android.net.Uri
import java.io.File

data class CookieStatus(
    val configured: Boolean = false,
    val cookieCount: Int = 0,
    val domainCount: Int = 0,
    val sizeBytes: Long = 0,
    val isWorking: Boolean = false
)

internal data class CookieImportResult(
    val status: CookieStatus,
    val message: String
)

/** Secure, app-private storage for an explicitly imported Netscape cookies.txt file. */
internal object CookieStore {

    fun status(context: Context): CookieStatus = inspect(activeFile(context))

    fun activeFile(context: Context): File? = cookieFile(context).takeIf {
        it.isFile && it.length() > 0
    }

    fun import(context: Context, source: Uri): CookieImportResult {
        val directory = cookieDirectory(context).apply { mkdirs() }
        val target = cookieFile(context)
        val staging = File(directory, STAGING_NAME).apply { delete() }
        val backup = File(directory, BACKUP_NAME).apply { delete() }

        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IllegalArgumentException("The selected file could not be opened.")
            input.use { sourceStream ->
                staging.outputStream().buffered().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = sourceStream.read(buffer)
                        if (read == -1) break
                        copied += read
                        if (copied > MAX_COOKIE_FILE_BYTES) {
                            throw IllegalArgumentException("Cookie files must be smaller than 5 MB.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            val imported = inspect(staging)
            if (!imported.configured) {
                throw IllegalArgumentException(
                    "Choose a Netscape-format cookies.txt file containing at least one cookie."
                )
            }

            if (target.exists() && !target.renameTo(backup)) {
                throw IllegalStateException("The existing cookie file could not be replaced.")
            }
            if (!staging.renameTo(target)) {
                backup.renameTo(target)
                throw IllegalStateException("The cookie file could not be saved.")
            }
            backup.delete()
            target.setReadable(false, false)
            target.setWritable(false, false)
            target.setReadable(true, true)
            target.setWritable(true, true)

            val saved = inspect(target)
            val siteLabel = if (saved.domainCount == 1) "site" else "sites"
            return CookieImportResult(
                status = saved,
                message = "Cookies ready for ${saved.domainCount} $siteLabel."
            )
        } catch (error: Exception) {
            staging.delete()
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    fun remove(context: Context): CookieImportResult {
        val removed = cookieFile(context).let { !it.exists() || it.delete() }
        File(cookieDirectory(context), STAGING_NAME).delete()
        File(cookieDirectory(context), BACKUP_NAME).delete()
        return if (removed) {
            CookieImportResult(CookieStatus(), "Saved cookies removed.")
        } else {
            CookieImportResult(status(context), "The cookie file could not be removed.")
        }
    }

    internal fun inspect(file: File?): CookieStatus {
        if (file == null || !file.isFile || file.length() == 0L) return CookieStatus()
        val domains = linkedSetOf<String>()
        var cookies = 0
        runCatching {
            file.useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trimEnd()
                    if (line.isBlank()) return@forEach
                    val record = if (line.startsWith(HTTP_ONLY_PREFIX)) {
                        line.removePrefix(HTTP_ONLY_PREFIX)
                    } else {
                        if (line.startsWith('#')) return@forEach
                        line
                    }
                    val fields = record.split('\t', limit = 7)
                    if (fields.size < 7 ||
                        fields[1] !in BOOLEAN_FIELDS ||
                        !fields[2].startsWith('/') ||
                        fields[3] !in BOOLEAN_FIELDS ||
                        fields[4].toLongOrNull() == null ||
                        fields[5].isBlank()
                    ) {
                        return@forEach
                    }
                    cookies += 1
                    fields[0].trimStart('.').takeIf(String::isNotBlank)?.let(domains::add)
                }
            }
        }.onFailure { return CookieStatus() }
        return CookieStatus(
            configured = cookies > 0,
            cookieCount = cookies,
            domainCount = domains.size,
            sizeBytes = file.length()
        )
    }

    private fun cookieDirectory(context: Context) = File(context.noBackupFilesDir, DIRECTORY_NAME)
    private fun cookieFile(context: Context) = File(cookieDirectory(context), FILE_NAME)

    private const val DIRECTORY_NAME = "site_access"
    private const val FILE_NAME = "cookies.txt"
    private const val STAGING_NAME = "cookies.importing"
    private const val BACKUP_NAME = "cookies.previous"
    private const val HTTP_ONLY_PREFIX = "#HttpOnly_"
    private const val MAX_COOKIE_FILE_BYTES = 5L * 1024 * 1024
    private const val COPY_BUFFER_SIZE = 64 * 1024
    private val BOOLEAN_FIELDS = setOf("TRUE", "FALSE")
}
