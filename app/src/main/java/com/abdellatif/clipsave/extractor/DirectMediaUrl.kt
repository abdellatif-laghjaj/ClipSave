package com.abdellatif.clipsave.extractor

import com.abdellatif.clipsave.data.model.MediaType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Classifies direct media URLs without invoking yt-dlp.
 *
 * Reddit's share flow sometimes redirects image posts through `/media?url=...`; unwrapping that
 * URL here keeps the actual image out of the video pipeline and works for both shared post links
 * and copied direct-media links.
 */
object DirectMediaUrl {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "heif")

    fun image(url: String, contentType: String? = null, title: String = ""): MediaInfo? {
        val directUrl = unwrap(url)
        val extension = extensionOf(directUrl)
            ?.takeIf(imageExtensions::contains)
            ?: extensionForContentType(contentType)
            ?: return null

        return MediaInfo(
            downloadUrl = directUrl,
            mediaType = MediaType.IMAGE,
            title = title.ifBlank { titleFromUrl(directUrl) },
            thumbnailUrl = directUrl,
            suggestedExtension = normalizeExtension(extension)
        )
    }

    fun isImage(url: String): Boolean = image(url) != null

    /** Recovers a direct image URL embedded in an extractor error or redirect message. */
    fun imageFromText(text: String, title: String = ""): MediaInfo? =
        URL_IN_TEXT.findAll(text).mapNotNull { match ->
            image(match.value.trimEnd('.', ',', ';', ')', ']', '}'), title = title)
        }.firstOrNull()

    internal fun unwrap(url: String): String {
        var current = url.trim()
        repeat(MAX_UNWRAP_DEPTH) {
            val parsed = current.toHttpUrlOrNull() ?: return current
            val host = parsed.host.removePrefix("www.").lowercase(Locale.US)
            val isRedditMediaWrapper =
                (host == "reddit.com" || host.endsWith(".reddit.com")) &&
                    parsed.encodedPath.trimEnd('/') == "/media"
            if (!isRedditMediaWrapper) return current

            val nested = parsed.queryParameter("url")?.trim().orEmpty()
            if (nested.isBlank() || nested == current) return current
            current = decodeRepeatedly(nested)
        }
        return current
    }

    internal fun extensionOf(url: String): String? {
        val path = url.toHttpUrlOrNull()?.encodedPath ?: url.substringBefore('?').substringBefore('#')
        val fileName = path.substringAfterLast('/', missingDelimiterValue = path)
        return fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf(String::isNotBlank)
    }

    private fun extensionForContentType(contentType: String?): String? = when (
        contentType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
    ) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> null
    }

    private fun normalizeExtension(extension: String): String =
        if (extension == "jpeg") "jpg" else extension

    private fun titleFromUrl(url: String): String {
        val encodedName = url.toHttpUrlOrNull()?.encodedPath
            ?.substringAfterLast('/')
            .orEmpty()
        val decodedName = runCatching {
            URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
        }.getOrDefault(encodedName)
        return decodedName
            .substringBeforeLast('.', missingDelimiterValue = decodedName)
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
    }

    private fun decodeRepeatedly(value: String): String {
        var decoded = value
        repeat(MAX_UNWRAP_DEPTH) {
            val next = runCatching {
                URLDecoder.decode(decoded, StandardCharsets.UTF_8.name())
            }.getOrDefault(decoded)
            if (next == decoded) return decoded
            decoded = next
        }
        return decoded
    }

    private const val MAX_UNWRAP_DEPTH = 3
    private val URL_IN_TEXT = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
}
