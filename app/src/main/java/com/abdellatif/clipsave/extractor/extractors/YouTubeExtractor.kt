package com.abdellatif.clipsave.extractor.extractors

import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.extractor.ExtractionException
import com.abdellatif.clipsave.extractor.Extractor
import com.abdellatif.clipsave.extractor.MediaInfo

/**
 * YouTube full-stream extraction requires signature de-ciphering (effectively yt-dlp).
 * The Open Graph video URL is an embed page rather than a media stream, so falling back to it
 * would save HTML with an .mp4 extension. YouTube must succeed through yt-dlp or fail clearly.
 */
class YouTubeExtractor : Extractor {
    override val platform = Platform.YOUTUBE
    override fun canHandle(url: String) = Platform.fromUrl(url) == Platform.YOUTUBE
    override fun extract(url: String): List<MediaInfo> {
        throw ExtractionException(
            "YouTube could not provide a downloadable stream. Update the download engine and retry."
        )
    }
}
