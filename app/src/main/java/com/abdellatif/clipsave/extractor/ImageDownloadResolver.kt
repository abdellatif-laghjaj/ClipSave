package com.abdellatif.clipsave.extractor

import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.network.HttpClient

/** Resolves images before yt-dlp so image jobs are never mislabeled or processed as videos. */
object ImageDownloadResolver {
    fun resolve(url: String, platform: Platform = Platform.fromUrl(url)): MediaInfo? {
        DirectMediaUrl.image(url)?.let { return it }

        val probe = HttpClient.probe(url)
        DirectMediaUrl.image(probe?.finalUrl.orEmpty(), probe?.contentType)?.let { return it }

        if (platform != Platform.REDDIT) return null
        return runCatching {
            ExtractorRegistry.extract(url).firstOrNull { it.mediaType == MediaType.IMAGE }
        }.getOrNull()
    }
}
