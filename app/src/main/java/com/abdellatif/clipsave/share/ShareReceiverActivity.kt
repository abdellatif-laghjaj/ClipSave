package com.abdellatif.clipsave.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.abdellatif.clipsave.data.model.DownloadFormat
import com.abdellatif.clipsave.data.model.Platform
import com.abdellatif.clipsave.data.model.UrlInputParser
import com.abdellatif.clipsave.data.preferences.Settings
import com.abdellatif.clipsave.data.preferences.ThemeMode
import com.abdellatif.clipsave.data.preferences.UserPreferences
import com.abdellatif.clipsave.download.DownloadService
import com.abdellatif.clipsave.ui.components.PlatformIcon
import com.abdellatif.clipsave.ui.theme.ClipSaveTheme

class ShareReceiverActivity : ComponentActivity() {

    private val userPreferences by lazy { UserPreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = extractUrls(intent)
        if (shared.isEmpty()) {
            Toast.makeText(this, "No link found in shared text.", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        setContent {
            val settings by userPreferences.settings.collectAsState(initial = Settings())
            val dark = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ClipSaveTheme(darkTheme = dark, accentColor = settings.accentColor) {
                ConfirmDialog(
                    urls = shared,
                    onDownload = { format ->
                        shared.forEach { DownloadService.start(this, it, format) }
                        val message = if (shared.size == 1) {
                            "ClipSave: download queued"
                        } else {
                            "ClipSave: ${shared.size} downloads queued"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun extractUrls(intent: Intent?): List<String> {
        if (intent?.action != Intent.ACTION_SEND) return emptyList()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return emptyList()
        return UrlInputParser.parse(text).urls
    }
}

@Composable
private fun ConfirmDialog(
    urls: List<String>,
    onDownload: (DownloadFormat) -> Unit,
    onCancel: () -> Unit
) {
    val platform = urls.singleOrNull()?.let(Platform::fromUrl)
    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (platform != null) {
                    PlatformIcon(platform, containerSize = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    if (platform != null) "Download from ${platform.displayName}?"
                    else "Queue ${urls.size} downloads?"
                )
            }
            val preview = urls.take(3).joinToString("\n")
            Text(
                if (urls.size > 3) "$preview\n+${urls.size - 3} more" else preview,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onDownload(DownloadFormat.BEST) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("  Download")
            }
            FilledTonalButton(
                onClick = { onDownload(DownloadFormat.AUDIO_M4A) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
                Text("  Audio only")
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
            }
        }
    }
}
