package com.abdellatif.clipsave.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.abdellatif.clipsave.ClipSaveApp
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.data.preferences.AccentColor
import com.abdellatif.clipsave.data.preferences.AccessMode
import com.abdellatif.clipsave.data.preferences.Settings
import com.abdellatif.clipsave.data.preferences.ThemeMode
import com.abdellatif.clipsave.download.DownloadService
import com.abdellatif.clipsave.download.YtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as ClipSaveApp).container
    private val repo = container.downloadRepository
    private val prefs = container.userPreferences

    val downloads = repo.downloads
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun download(url: String, format: DownloadFormat = DownloadFormat.BEST) {
        val clean = url.trim()
        if (clean.isBlank()) return
        DownloadService.start(getApplication(), clean, format)
    }

    fun retry(id: String) {
        val item = repo.get(id) ?: return
        val format =
            if (item.mediaType == MediaType.AUDIO) DownloadFormat.AUDIO_M4A
            else DownloadFormat.BEST
        DownloadService.start(getApplication(), item.url, format, retryId = id)
    }

    fun updateEngine(onResult: (String) -> Unit) = viewModelScope.launch {
        val msg = withContext(Dispatchers.IO) {
            YtDlpEngine.update(getApplication(), force = true)
        }
        onResult(msg)
    }

    fun delete(id: String) = repo.remove(id)
    fun clearCompleted() = repo.clearCompleted()
    fun clearAll() = repo.clearAll()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setTheme(mode) }
    fun setAccentColor(color: AccentColor) = viewModelScope.launch { prefs.setAccentColor(color) }
    fun setAccessMode(mode: AccessMode) = viewModelScope.launch { prefs.setAccessMode(mode) }
    fun completeOnboarding() = viewModelScope.launch { prefs.setOnboardingDone(true) }

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
