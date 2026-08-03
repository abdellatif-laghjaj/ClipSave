package com.abdellatif.clipsave.download

import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.DownloadStatus
import java.net.URI

/** Stable identity used to coalesce only requests that would produce the same transfer. */
internal data class DownloadRequestIdentity(
    val url: String,
    val format: DownloadFormat
) {
    companion object {
        fun from(url: String, format: DownloadFormat): DownloadRequestIdentity =
            DownloadRequestIdentity(normalizeUrl(url), format)

        private fun normalizeUrl(input: String): String {
            val withoutFragment = input.trim().substringBefore('#')
            val uri = runCatching { URI(withoutFragment).normalize() }.getOrNull()
                ?: return withoutFragment
            val scheme = uri.scheme?.lowercase() ?: return withoutFragment
            val host = uri.host?.trimEnd('.')?.lowercase() ?: return withoutFragment
            val port = when {
                uri.port == -1 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
            val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
            val path = uri.rawPath.orEmpty().ifEmpty { "/" }
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            return "$scheme://$userInfo$host$port$path$query"
        }
    }
}

internal fun Download.matchesActiveRequest(url: String, format: DownloadFormat): Boolean =
    status.isActiveTransfer() &&
        DownloadRequestIdentity.from(this.url, this.format) ==
        DownloadRequestIdentity.from(url, format)

internal fun DownloadStatus.isActiveTransfer(): Boolean = when (this) {
    DownloadStatus.QUEUED,
    DownloadStatus.WAITING_FOR_NETWORK,
    DownloadStatus.EXTRACTING,
    DownloadStatus.DOWNLOADING -> true
    DownloadStatus.PAUSED,
    DownloadStatus.COMPLETED,
    DownloadStatus.FAILED -> false
}
