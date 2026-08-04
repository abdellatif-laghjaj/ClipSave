package com.abdellatif.clipsave.download

import com.abdellatif.clipsave.data.model.PlaylistItem
import com.abdellatif.clipsave.data.model.PlaylistPreview
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object PlaylistParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(output: String, previewLimit: Int): PlaylistPreview? {
        require(previewLimit > 0) { "previewLimit must be positive" }
        val root = json.parseToJsonElement(output.trim()).jsonObject
        val entries = root["entries"] as? JsonArray ?: return null

        val items = entries.take(previewLimit).mapIndexedNotNull { index, element ->
            val entry = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = entry.string("id")
            val url = entry.canonicalUrl(id) ?: return@mapIndexedNotNull null
            val title = entry.string("title")?.takeIf(String::isNotBlank) ?: "Untitled item"
            PlaylistItem(
                key = listOfNotNull(id, url).joinToString(":").ifBlank { "item-$index" },
                title = title,
                url = url,
                durationSeconds = entry["duration"]?.jsonPrimitive?.longOrNull,
                thumbnailUrl = entry.string("thumbnail")
                    ?: entry["thumbnails"]?.jsonArray?.lastOrNull()
                        ?.let { it as? JsonObject }?.string("url"),
                uploader = entry.string("uploader") ?: entry.string("channel")
            )
        }.distinctBy(PlaylistItem::url)

        val explicitReportedCount = listOfNotNull(
            root["playlist_count"]?.jsonPrimitive?.intOrNull,
            root["n_entries"]?.jsonPrimitive?.intOrNull
        ).maxOrNull()
        val reportedCount = maxOf(explicitReportedCount ?: entries.size, items.size)

        return PlaylistPreview(
            title = root.string("title")?.takeIf(String::isNotBlank) ?: "Playlist",
            uploader = root.string("uploader") ?: root.string("channel"),
            items = items,
            reportedItemCount = maxOf(reportedCount, items.size),
            hasMoreItems = explicitReportedCount?.let { it > items.size }
                ?: (entries.size >= previewLimit)
        )
    }

    private fun JsonObject.canonicalUrl(id: String?): String? {
        val direct = listOf("webpage_url", "original_url", "url")
            .asSequence()
            .mapNotNull { name -> string(name) }
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
        if (direct != null) return direct

        val extractor = listOfNotNull(string("ie_key"), string("extractor_key"), string("extractor"))
            .joinToString(" ")
            .lowercase()
        return id?.takeIf { "youtube" in extractor }?.let { "https://youtu.be/$it" }
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull
}
