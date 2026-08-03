package com.abdellatif.clipsave.data.model

import java.net.URI

/**
 * Identifies links that are likely to expand to more than one downloadable item.
 *
 * Normal media links deliberately return false so they retain the instant one-tap queue flow.
 * Collection links are inspected before queueing to prevent accidental bulk downloads.
 */
object CollectionUrlDetector {
    private val collectionSegments = setOf(
        "album",
        "albums",
        "channel",
        "collection",
        "collections",
        "playlist",
        "playlists",
        "sets",
        "showcase"
    )

    fun isLikelyCollection(input: String): Boolean {
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false

        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        val queryKeys = uri.rawQuery
            .orEmpty()
            .split('&')
            .mapNotNull { part -> part.substringBefore('=').takeIf(String::isNotBlank) }
            .map(String::lowercase)
            .toSet()

        val pathSegments = uri.path
            .orEmpty()
            .split('/')
            .filter(String::isNotBlank)
            .map(String::lowercase)

        if (host == "youtube.com" || host.endsWith(".youtube.com")) {
            if ("list" in queryKeys) return true
            val firstSegment = pathSegments.firstOrNull()
            if (firstSegment?.startsWith('@') == true ||
                firstSegment in setOf("c", "channel", "user")
            ) {
                return true
            }
        }

        return pathSegments.any(collectionSegments::contains)
    }
}
