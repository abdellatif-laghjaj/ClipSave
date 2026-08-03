package com.abdellatif.clipsave.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.CollectionUrlDetector
import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.data.preferences.ThemeMode
import com.abdellatif.clipsave.download.DownloadService
import com.abdellatif.clipsave.download.FileSaver
import com.abdellatif.clipsave.ui.navigation.AppScaffold
import com.abdellatif.clipsave.ui.navigation.AppLaunchRequest
import com.abdellatif.clipsave.ui.onboarding.OnboardingScreen
import com.abdellatif.clipsave.ui.theme.ClipSaveTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val requestLegacyStorage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val url = pendingLegacyQuickGrab
            pendingLegacyQuickGrab = null
            if (granted && url != null) {
                queueQuickGrab(url)
            } else if (!granted) {
                Toast.makeText(
                    this,
                    "Storage access is required to save files on Android 8 and 9.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private var pendingQuickGrab = false
    private var pendingLegacyQuickGrab: String? = null
    private val launchRequest = MutableStateFlow<AppLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotifications()
        setContent {
            val request by launchRequest.collectAsState()
            ClipSaveRoot(
                launchRequest = request,
                onLaunchRequestConsumed = { consumed ->
                    if (launchRequest.value?.key == consumed.key) launchRequest.value = null
                }
            )
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * "Quick grab": triggered by the floating bubble — download whatever link is on the
     * clipboard. On Android 10+ the clipboard is only readable while our window has focus,
     * so on a cold start we defer the grab until focus arrives.
     */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_QUICK_GRAB -> {
                if (hasWindowFocus()) performQuickGrab() else pendingQuickGrab = true
            }

            ACTION_OPEN_DOWNLOAD -> {
                launchRequest.value = AppLaunchRequest(
                    key = System.nanoTime(),
                    downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                )
            }

            ACTION_SHOW_DOWNLOADS -> {
                launchRequest.value = AppLaunchRequest(
                    key = System.nanoTime(),
                    showDownloads = true
                )
            }

            ACTION_REVIEW_URL -> {
                intent.getStringExtra(EXTRA_URL)?.takeIf(String::isNotBlank)?.let { url ->
                    launchRequest.value = AppLaunchRequest(
                        key = System.nanoTime(),
                        newDownloadUrl = url
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingQuickGrab) {
            pendingQuickGrab = false
            performQuickGrab()
        }
    }

    private fun performQuickGrab() {
        val clip = readClipboardUrl()
        if (clip != null) {
            if (CollectionUrlDetector.isLikelyCollection(clip)) {
                queueQuickGrab(clip)
            } else if (FileSaver.needsLegacyStoragePermission(this)) {
                pendingLegacyQuickGrab = clip
                requestLegacyStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                queueQuickGrab(clip)
            }
        } else {
            Toast.makeText(
                this,
                "No link on clipboard. In the app tap ••• → Copy link, then tap the bubble again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun queueQuickGrab(url: String) {
        if (CollectionUrlDetector.isLikelyCollection(url)) {
            launchRequest.value = AppLaunchRequest(
                key = System.nanoTime(),
                newDownloadUrl = url
            )
            Toast.makeText(this, "Choose the playlist items to download.", Toast.LENGTH_LONG).show()
            return
        }
        DownloadService.start(this, url, DownloadFormat.BEST)
        Toast.makeText(
            this,
            "Downloading from ${Platform.fromUrl(url).displayName}…",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun readClipboardUrl(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
        return Regex("https?://[^\\s]+").find(text)?.value
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val ACTION_QUICK_GRAB = "com.abdellatif.clipsave.action.QUICK_GRAB"
        const val ACTION_OPEN_DOWNLOAD = "com.abdellatif.clipsave.action.OPEN_DOWNLOAD"
        const val ACTION_SHOW_DOWNLOADS = "com.abdellatif.clipsave.action.SHOW_DOWNLOADS"
        const val ACTION_REVIEW_URL = "com.abdellatif.clipsave.action.REVIEW_URL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_URL = "url"
    }
}

@Composable
fun ClipSaveRoot(
    launchRequest: AppLaunchRequest? = null,
    onLaunchRequestConsumed: (AppLaunchRequest) -> Unit = {}
) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val settings by vm.settings.collectAsState()

    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    ClipSaveTheme(darkTheme = dark, accentColor = settings.accentColor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!settings.onboardingDone) {
                OnboardingScreen(onDone = { vm.completeOnboarding() })
            } else {
                AppScaffold(
                    vm = vm,
                    launchRequest = launchRequest,
                    onLaunchRequestConsumed = onLaunchRequestConsumed
                )
            }
        }
    }
}
