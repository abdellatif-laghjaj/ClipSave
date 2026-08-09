package com.abdellatif.clipsave.extractor.extractors

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.extractor.DirectMediaUrl
import com.abdellatif.clipsave.extractor.ExtractionException
import com.abdellatif.clipsave.extractor.Extractor
import com.abdellatif.clipsave.extractor.MediaInfo
import com.abdellatif.clipsave.extractor.MetaScraper
import com.abdellatif.clipsave.network.HttpClient
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Resolves Reddit media from direct URLs, page metadata, and the public JSON response. */
class RedditExtractor : Extractor {
    override val platform = Platform.REDDIT
    private val json = Json { ignoreUnknownKeys = true }

    override fun canHandle(url: String) = Platform.fromUrl(url) == Platform.REDDIT

    override fun extract(url: String): List<MediaInfo> {
        DirectMediaUrl.image(url, title = directTitle(url))?.let { return listOf(it) }

        val page = HttpClient.getResource(url, mobile = true)
        DirectMediaUrl.image(
            page?.finalUrl.orEmpty(),
            page?.contentType,
            titleFromPostUrl(page?.finalUrl.orEmpty())
        )?.let { return listOf(it) }
        val pageImage = page?.body?.let { body ->
            runCatching { MetaScraper.fromHtml(body) }.getOrNull()
                ?.firstOrNull { media ->
                    media.mediaType == MediaType.IMAGE &&
                        DirectMediaUrl.image(media.downloadUrl) != null
                }
        }
        if (pageImage != null) return listOf(pageImage)

        val canonicalUrl = page?.finalUrl ?: HttpClient.resolveFinalUrl(url)
        DirectMediaUrl.image(canonicalUrl, title = directTitle(canonicalUrl))?.let {
            return listOf(it)
        }
        val titleHint = titleFromPostUrl(canonicalUrl)
        val candidates = listOf(url, canonicalUrl)
            .map { it.substringBefore("?").trimEnd('/') }
            .distinct()
        var reachedReddit = false

        for (candidate in candidates) {
            val jsonUrl = if (candidate.endsWith(".json")) candidate else "$candidate.json"
            val resource = HttpClient.getResource(jsonUrl) ?: continue
            reachedReddit = true

            DirectMediaUrl.image(resource.finalUrl, title = titleHint)?.let {
                return listOf(it)
            }
            parseJson(resource.body)?.let { return it }
        }

        if (reachedReddit) throw ExtractionException("Invalid Reddit response.")
        throw ExtractionException("Reddit unreachable.")
    }

    internal fun parseJson(body: String): List<MediaInfo>? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null

        val listing = (root as? JsonArray)?.firstOrNull()?.jsonObject
            ?: return null
        val post = listing["data"]?.jsonObject
            ?.get("children")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("data")?.jsonObject
            ?: return null

        val title = post["title"]?.jsonPrimitive?.contentOrNull ?: "Reddit post"
        val results = mutableListOf<MediaInfo>()

        val galleryItems = post["gallery_data"]?.jsonObject?.get("items") as? JsonArray
        val galleryMetadata = post["media_metadata"]?.jsonObject
        galleryItems?.forEach { item ->
            val mediaId = item.jsonObject["media_id"]?.jsonPrimitive?.contentOrNull
                ?: return@forEach
            val source = galleryMetadata?.get(mediaId)?.jsonObject?.get("s")?.jsonObject
                ?: return@forEach
            val imageUrl = source["u"]?.jsonPrimitive?.contentOrNull
                ?: source["gif"]?.jsonPrimitive?.contentOrNull
                ?: return@forEach
            val decodedUrl = imageUrl.replace("&amp;", "&")
            DirectMediaUrl.image(decodedUrl, title = title)?.let(results::add)
        }

        if (results.isEmpty()) {
            post["media"]?.jsonObject?.get("reddit_video")?.jsonObject
                ?.get("fallback_url")?.jsonPrimitive?.contentOrNull?.let {
                    results += MediaInfo(it.substringBefore("?"), MediaType.VIDEO, title)
                }
        }

        if (results.isEmpty()) {
            val direct = post["url_overridden_by_dest"]?.jsonPrimitive?.contentOrNull
                ?: post["url"]?.jsonPrimitive?.contentOrNull
            if (direct != null) {
                DirectMediaUrl.image(direct, title = title)?.let(results::add)
                    ?: direct.takeIf { it.contains(".mp4", ignoreCase = true) }?.let {
                        results += MediaInfo(it, MediaType.VIDEO, title)
                    }
            }
        }

        if (results.isEmpty()) {
            post["preview"]?.jsonObject?.get("images")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("source")?.jsonObject?.get("url")?.jsonPrimitive
                ?.contentOrNull?.let {
                    DirectMediaUrl.image(it.replace("&amp;", "&"), title = title)
                        ?.let(results::add)
                }
        }

        if (results.isEmpty()) throw ExtractionException("No downloadable media in this Reddit post.")
        return results
    }

    private fun titleFromPostUrl(url: String): String {
        val cleanPath = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
        val parts = cleanPath.split('/').filter(String::isNotBlank)
        val commentsIndex = parts.indexOf("comments")
        val slug = parts.getOrNull(commentsIndex + 2).orEmpty()
        if (slug.isBlank()) return "Reddit image"
        return URLDecoder.decode(slug, StandardCharsets.UTF_8.name())
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replaceFirstChar { it.titlecase() }
    }

    private fun directTitle(url: String): String {
        val name = runCatching { java.net.URI(DirectMediaUrl.unwrap(url)).path.orEmpty() }
            .getOrDefault("")
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        return name.ifBlank { "Reddit image" }
    }
}
