package com.abdellatif.clipsave.extractor.extractors

import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.extractor.DirectMediaUrl
import com.abdellatif.clipsave.extractor.Extractor
import com.abdellatif.clipsave.extractor.MediaInfo
import com.abdellatif.clipsave.extractor.MetaScraper

/** Fallback for any URL: direct media links handled inline, otherwise og:/twitter: meta. */
class GenericExtractor : Extractor {
    override val platform = Platform.GENERIC
    override fun canHandle(url: String) = true

    override fun extract(url: String): List<MediaInfo> {
        DirectMediaUrl.image(url)?.let { return listOf(it) }

        val direct = Regex(".*\\.(mp4|webm|mkv|mov|mp3|m4a|aac|wav|ogg)(\\?.*)?$",
            RegexOption.IGNORE_CASE)
        if (direct.matches(url)) {
            val lower = url.lowercase()
            val type = when {
                Regex("\\.(mp4|webm|mkv|mov)").containsMatchIn(lower) -> MediaType.VIDEO
                else -> MediaType.AUDIO
            }
            return listOf(MediaInfo(url, type))
        }
        return MetaScraper.scrape(url, mobile = false)
    }
}
