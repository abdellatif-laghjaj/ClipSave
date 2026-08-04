package com.abdellatif.clipsave.data.model

import java.net.URI

data class ParsedUrlInput(
    val urls: List<String>,
    val totalDetected: Int
) {
    val omittedCount: Int get() = (totalDetected - urls.size).coerceAtLeast(0)
}

/** Extracts a safe, deduplicated batch of web links from pasted or shared text. */
object UrlInputParser {

    const val MAX_URLS = 50

    private val urlPattern = Regex(
        pattern = """(?i)(?<![@/\w])(?:(?:https?://|www\.)[^\s<>\"']+|(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,}(?:/[^\s<>\"']*)?)"""
    )
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

    fun parse(input: String, limit: Int = MAX_URLS): ParsedUrlInput {
        require(limit > 0) { "limit must be positive" }

        val unique = LinkedHashMap<String, String>()
        urlPattern.findAll(input).forEach { match ->
            val candidate = match.value.trimEnd(*trailingPunctuation)
            val normalized = normalize(candidate) ?: return@forEach
            val uri = runCatching { URI(normalized) }.getOrNull() ?: return@forEach
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return@forEach
            if (host.isBlank() || '.' !in host) return@forEach

            val port = if (uri.port >= 0) ":${uri.port}" else ""
            val suffix = buildString {
                append(uri.rawPath.orEmpty())
                if (uri.rawQuery != null) append('?').append(uri.rawQuery)
                if (uri.rawFragment != null) append('#').append(uri.rawFragment)
            }
            val key = "${uri.scheme.lowercase()}://$host$port$suffix"
            unique.putIfAbsent(key, normalized)
        }

        return ParsedUrlInput(
            urls = unique.values.take(limit),
            totalDetected = unique.size
        )
    }

    private fun normalize(candidate: String): String {
        val value = candidate.trim()
        if (value.isBlank()) return ""
        return when {
            value.startsWith("http://", ignoreCase = true) -> value
            value.startsWith("https://", ignoreCase = true) -> value
            else -> "https://$value"
        }
    }
}
