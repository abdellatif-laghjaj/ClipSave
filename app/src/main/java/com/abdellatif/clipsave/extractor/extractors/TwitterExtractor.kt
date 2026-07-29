package com.abdellatif.clipsave.extractor.extractors

import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.extractor.ExtractionException
import com.abdellatif.clipsave.extractor.Extractor
import com.abdellatif.clipsave.extractor.MediaInfo

/**
 * X's public Open Graph image is a tweet-card preview, not necessarily the post media.
 * Saving it after an engine failure can turn a video post into a misleading image download,
 * so X extraction must succeed through yt-dlp.
 */
class TwitterExtractor : Extractor {
    override val platform = Platform.TWITTER
    override fun canHandle(url: String) = Platform.fromUrl(url) == Platform.TWITTER
    override fun extract(url: String): List<MediaInfo> {
        throw ExtractionException(
            "X could not provide downloadable media. Update the download engine and retry."
        )
    }
}
