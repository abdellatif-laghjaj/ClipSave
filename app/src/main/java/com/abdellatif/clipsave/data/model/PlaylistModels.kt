package com.abdellatif.clipsave.data.model

data class PlaylistItem(
    val key: String,
    val title: String,
    val url: String,
    val durationSeconds: Long? = null,
    val thumbnailUrl: String? = null,
    val uploader: String? = null
)

data class PlaylistPreview(
    val title: String,
    val uploader: String? = null,
    val items: List<PlaylistItem>,
    val reportedItemCount: Int,
    val hasMoreItems: Boolean
)

sealed interface PlaylistInspectionState {
    data object Idle : PlaylistInspectionState
    data class Loading(val url: String) : PlaylistInspectionState
    data class Ready(val preview: PlaylistPreview) : PlaylistInspectionState
    data class Error(val message: String) : PlaylistInspectionState
}
