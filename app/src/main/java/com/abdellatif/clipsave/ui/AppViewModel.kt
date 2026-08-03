package com.abdellatif.clipsave.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.abdellatif.clipsave.ClipSaveApp
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.DownloadStatus
import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.data.model.PlaylistInspectionState
import com.abdellatif.clipsave.data.preferences.AccentColor
import com.abdellatif.clipsave.data.preferences.AccessMode
import com.abdellatif.clipsave.data.preferences.NetworkPolicy
import com.abdellatif.clipsave.data.preferences.Settings
import com.abdellatif.clipsave.data.preferences.ThemeMode
import com.abdellatif.clipsave.download.DownloadService
import com.abdellatif.clipsave.download.YtDlpEngine
import com.abdellatif.clipsave.media.SavedMediaManager
import com.abdellatif.clipsave.notif.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as ClipSaveApp).container
    private val repo = container.downloadRepository
    private val prefs = container.userPreferences

    val downloads = repo.downloads
    val downloadsLoaded = repo.loaded
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _playlistInspection = MutableStateFlow<PlaylistInspectionState>(
        PlaylistInspectionState.Idle
    )
    val playlistInspection = _playlistInspection.asStateFlow()
    private val _pendingDownloadUrl = MutableStateFlow<String?>(null)
    val pendingDownloadUrl = _pendingDownloadUrl.asStateFlow()
    private var playlistInspectionJob: Job? = null
    @Volatile
    private var playlistProcessId: String? = null

    fun download(url: String, format: DownloadFormat = DownloadFormat.BEST) {
        downloadAll(listOf(url), format)
    }

    fun downloadAll(urls: List<String>, format: DownloadFormat = DownloadFormat.BEST) {
        urls.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { DownloadService.start(getApplication(), it, format) }
    }

    fun retry(id: String) {
        val item = repo.get(id) ?: return
        val format = when {
            item.format.isAudio -> item.format
            item.mediaType == MediaType.AUDIO -> DownloadFormat.AUDIO_M4A
            else -> item.format
        }
        DownloadService.start(getApplication(), item.url, format, retryId = id)
    }

    fun pause(id: String) = DownloadService.pause(getApplication(), id)

    fun openNewDownload(url: String) {
        _pendingDownloadUrl.value = url.trim()
    }

    fun consumeNewDownload(url: String) {
        if (_pendingDownloadUrl.value == url) _pendingDownloadUrl.value = null
    }

    fun inspectPlaylist(url: String) {
        dismissPlaylistInspection()
        val processId = "playlist_${UUID.randomUUID()}"
        playlistProcessId = processId
        _playlistInspection.value = PlaylistInspectionState.Loading(url)
        playlistInspectionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val preview = YtDlpEngine.inspectPlaylist(getApplication(), url, processId)
                if (playlistProcessId == processId) {
                    _playlistInspection.value = PlaylistInspectionState.Ready(preview)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Exception) {
                Log.w("AppViewModel", "Playlist inspection failed", t)
                if (playlistProcessId == processId) {
                    _playlistInspection.value = PlaylistInspectionState.Error(
                        if (t is IllegalArgumentException) {
                            t.message ?: "No downloadable items were found in this playlist."
                        } else {
                            playlistErrorMessage(t.message)
                        }
                    )
                }
            } finally {
                if (playlistProcessId == processId) playlistProcessId = null
            }
        }
    }

    fun dismissPlaylistInspection() {
        playlistInspectionJob?.cancel()
        playlistInspectionJob = null
        playlistProcessId?.let(YtDlpEngine::cancel)
        playlistProcessId = null
        _playlistInspection.value = PlaylistInspectionState.Idle
    }

    fun updateEngine(onResult: (String) -> Unit) = viewModelScope.launch {
        val msg = withContext(Dispatchers.IO) {
            YtDlpEngine.update(getApplication(), force = true)
        }
        onResult(msg)
    }

    fun delete(id: String) {
        val item = repo.get(id) ?: return
        if (item.status == DownloadStatus.QUEUED ||
            item.status == DownloadStatus.EXTRACTING ||
            item.status == DownloadStatus.DOWNLOADING
        ) {
            DownloadService.remove(getApplication(), id)
        } else if (item.status == DownloadStatus.COMPLETED) {
            viewModelScope.launch(Dispatchers.IO) {
                val result = SavedMediaManager.delete(getApplication(), item)
                if (result.removeHistory) {
                    repo.remove(id)
                    NotificationHelper.cancelDone(getApplication(), id)
                    YtDlpEngine.cleanup(getApplication(), id)
                }
                _messages.emit(result.message)
            }
        } else {
            repo.remove(id)
            viewModelScope.launch(Dispatchers.IO) {
                YtDlpEngine.cleanup(getApplication(), id)
            }
        }
    }
    fun clearCompleted() = repo.clearCompleted()
    fun clearAll() = DownloadService.clearAll(getApplication())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setTheme(mode) }
    fun setAccentColor(color: AccentColor) = viewModelScope.launch { prefs.setAccentColor(color) }
    fun setAccessMode(mode: AccessMode) = viewModelScope.launch { prefs.setAccessMode(mode) }
    fun setNetworkPolicy(policy: NetworkPolicy) =
        viewModelScope.launch { prefs.setNetworkPolicy(policy) }
    fun setEmbedSubtitles(enabled: Boolean) =
        viewModelScope.launch { prefs.setEmbedSubtitles(enabled) }
    fun completeOnboarding() = viewModelScope.launch { prefs.setOnboardingDone(true) }

    override fun onCleared() {
        dismissPlaylistInspection()
        super.onCleared()
    }

    private fun playlistErrorMessage(rawMessage: String?): String {
        val message = rawMessage.orEmpty().lowercase()
        return when {
            "404" in message || "not found" in message ->
                "This playlist is unavailable or was removed."
            "private" in message || "sign in" in message || "login" in message ->
                "This playlist is private or requires an account."
            "unsupported url" in message ->
                "This site doesn’t expose playlist items for this link."
            else ->
                "Couldn’t read this playlist. Check the link and connection, then try again."
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app =
                    extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return AppViewModel(app) as T
            }
        }
    }
}
