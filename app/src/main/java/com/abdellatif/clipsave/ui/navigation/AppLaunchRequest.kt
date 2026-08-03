package com.abdellatif.clipsave.ui.navigation

/** One-shot navigation request produced by a notification tap. */
data class AppLaunchRequest(
    val key: Long,
    val downloadId: String? = null,
    val showDownloads: Boolean = false,
    val newDownloadUrl: String? = null
)
