package com.abdellatif.clipsave.data.model

import kotlinx.serialization.Serializable

enum class MediaType { IMAGE, VIDEO, AUDIO, UNKNOWN }

enum class DownloadStatus {
    QUEUED,
    WAITING_FOR_NETWORK,
    EXTRACTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

/** User-selectable quality / container. */
enum class DownloadFormat(val label: String) {
    BEST("Best quality"),
    Q1080("Up to 1080p"),
    Q720("Up to 720p"),
    Q480("Up to 480p"),
    AUDIO_M4A("Audio · M4A"),
    AUDIO_MP3("Audio · MP3");

    val isAudio: Boolean get() = this == AUDIO_M4A || this == AUDIO_MP3
}

/**
 * Known platforms for nice labelling. The yt-dlp engine supports 1000+ sites, so any URL
 * not listed here still works through [GENERIC]; this enum only controls display + fallbacks.
 */
enum class Platform(val displayName: String, val hosts: List<String>) {
    YOUTUBE("YouTube", listOf("youtube.com", "youtu.be", "music.youtube.com", "m.youtube.com")),
    INSTAGRAM("Instagram", listOf("instagram.com", "instagr.am", "ddinstagram.com")),
    TIKTOK("TikTok", listOf("tiktok.com", "vm.tiktok.com", "vt.tiktok.com")),
    TWITTER(
        "X / Twitter",
        listOf("twitter.com", "x.com", "t.co", "fxtwitter.com", "vxtwitter.com")
    ),
    REDDIT("Reddit", listOf("reddit.com", "redd.it", "old.reddit.com")),
    FACEBOOK("Facebook", listOf("facebook.com", "fb.watch", "fb.com", "m.facebook.com")),
    PINTEREST("Pinterest", listOf("pinterest.com", "pin.it")),
    TELEGRAM("Telegram", listOf("t.me", "telegram.me")),
    TWITCH("Twitch", listOf("twitch.tv", "clips.twitch.tv")),
    VIMEO("Vimeo", listOf("vimeo.com", "player.vimeo.com")),
    DAILYMOTION("Dailymotion", listOf("dailymotion.com", "dai.ly")),
    SOUNDCLOUD("SoundCloud", listOf("soundcloud.com")),
    BANDCAMP("Bandcamp", listOf("bandcamp.com")),
    MIXCLOUD("Mixcloud", listOf("mixcloud.com")),
    AUDIOMACK("Audiomack", listOf("audiomack.com")),
    BILIBILI("Bilibili", listOf("bilibili.com", "b23.tv")),
    NICONICO("Niconico", listOf("nicovideo.jp", "nico.ms")),
    VK("VK", listOf("vk.com", "vkvideo.ru")),
    SNAPCHAT("Snapchat", listOf("snapchat.com")),
    THREADS("Threads", listOf("threads.net", "threads.com")),
    TUMBLR("Tumblr", listOf("tumblr.com")),
    LINKEDIN("LinkedIn", listOf("linkedin.com")),
    RUMBLE("Rumble", listOf("rumble.com")),
    ODYSEE("Odysee", listOf("odysee.com")),
    KICK("Kick", listOf("kick.com")),
    STREAMABLE("Streamable", listOf("streamable.com")),
    IMGUR("Imgur", listOf("imgur.com", "i.imgur.com")),
    GIPHY("Giphy", listOf("giphy.com", "gph.is")),
    NINEGAG("9GAG", listOf("9gag.com")),
    BITCHUTE("BitChute", listOf("bitchute.com")),
    LIKEE("Likee", listOf("likee.video", "likee.com")),
    GENERIC("Web", emptyList());

    companion object {
        private val urlInText = Regex("""(?:https?://)?(?:www\.)?[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+(?:/[^\s]*)?""")

        /**
         * Detects the source from a full URL, a bare domain, or text containing a shared link.
         * Unknown hosts deliberately resolve to [GENERIC] so yt-dlp can still handle them.
         */
        fun fromUrl(input: String): Platform {
            val candidate = urlInText.find(input.trim())?.value ?: return GENERIC
            val normalized = if ("://" in candidate) candidate else "https://$candidate"
            val host = runCatching {
                java.net.URI(normalized)
                    .host
                    ?.trimEnd('.')
                    ?.removePrefix("www.")
                    ?.lowercase()
            }.getOrNull() ?: return GENERIC
            return entries.firstOrNull { p -> p.hosts.any { host == it || host.endsWith(".$it") } }
                ?: GENERIC
        }
    }
}

@Serializable
data class Download(
    val id: String,
    val url: String,
    val platform: Platform = Platform.GENERIC,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val format: DownloadFormat = DownloadFormat.BEST,
    val title: String = "",
    val fileName: String = "",
    val localUri: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSecond: Long = 0,
    val etaSeconds: Long = -1,
    val thumbnailUrl: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
