package com.abdellatif.clipsave.data.repository

import android.content.Context
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Simple JSON-file backed store for download history. No Room / KSP required.
 *
 * All state mutations are atomic ([MutableStateFlow.update]); disk persistence is
 * debounced and always runs on an IO dispatcher so progress ticks and UI actions
 * never block or thrash the filesystem.
 */
class DownloadRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "downloads.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<Download>>(emptyList())
    val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val persistRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        scope.launch {
            val persisted = runCatching {
                if (file.exists()) json.decodeFromString<List<Download>>(file.readText())
                else emptyList()
            }.getOrDefault(emptyList())
            val loaded = persisted.recoverInterruptedDownloads()
            // Downloads may have been upserted before the load finished; they win.
            _downloads.update { current ->
                val currentIds = current.map { it.id }.toSet()
                (current + loaded.filterNot { it.id in currentIds })
                    .sortedByDescending { it.createdAt }
            }
            // Recovery runs before the debounced collector is guaranteed to be subscribed.
            // Persist the repaired snapshot directly so another process death cannot resurrect
            // stale "Downloading" rows.
            if (loaded != persisted) {
                runCatching { file.writeText(json.encodeToString(_downloads.value)) }
            }
        }
        @OptIn(FlowPreview::class)
        scope.launch {
            persistRequests.debounce(400).collect {
                val snapshot = _downloads.value
                runCatching { file.writeText(json.encodeToString(snapshot)) }
            }
        }
    }

    private fun schedulePersist() {
        persistRequests.tryEmit(Unit)
    }

    fun upsert(download: Download) {
        _downloads.update { current ->
            val existing = current.any { it.id == download.id }
            val updated =
                if (existing) current.map { if (it.id == download.id) download else it }
                else listOf(download) + current
            updated.sortedByDescending { it.createdAt }
        }
        schedulePersist()
    }

    fun get(id: String): Download? = _downloads.value.firstOrNull { it.id == id }

    fun remove(id: String) {
        _downloads.update { list -> list.filterNot { it.id == id } }
        schedulePersist()
    }

    fun clearAll() {
        _downloads.value = emptyList()
        schedulePersist()
    }

    fun clearCompleted() {
        _downloads.update { list -> list.filterNot { it.status == DownloadStatus.COMPLETED } }
        schedulePersist()
    }
}

internal fun List<Download>.recoverInterruptedDownloads(): List<Download> = map { download ->
    if (download.status == DownloadStatus.QUEUED ||
        download.status == DownloadStatus.EXTRACTING ||
        download.status == DownloadStatus.DOWNLOADING
    ) {
        download.copy(
            status = DownloadStatus.PAUSED,
            errorMessage = "Interrupted before completion. Tap Resume to continue."
        )
    } else {
        download
    }
}
