package com.abdellatif.clipsave.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdellatif.clipsave.BuildConfig
import com.abdellatif.clipsave.data.preferences.AccessMode
import com.abdellatif.clipsave.data.preferences.ThemeMode
import com.abdellatif.clipsave.download.YtDlpEngine
import com.abdellatif.clipsave.privileged.RootHelper
import com.abdellatif.clipsave.privileged.ShizukuHelper
import com.abdellatif.clipsave.ui.AppViewModel
import com.abdellatif.clipsave.ui.components.MinimalChip
import com.abdellatif.clipsave.ui.components.SectionLabel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var engineMsg by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingsGroup("Appearance") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    MinimalChip(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        label = mode.name.lowercase(Locale.US)
                            .replaceFirstChar { it.uppercase(Locale.US) }
                    )
                }
            }
        }

        SettingsGroup("Download engine") {
            Text(
                "yt-dlp powers downloads from 1000+ sites. Keep it updated so site changes keep working.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Version · ${YtDlpEngine.ytdlpVersion ?: "initializing…"}",
                style = MaterialTheme.typography.bodySmall
            )
            PillButton(
                label = if (updating) "Updating…" else "Update engine",
                enabled = !updating,
                loading = updating,
                onClick = {
                    updating = true
                    engineMsg = ""
                    vm.updateEngine { result ->
                        engineMsg = result
                        updating = false
                    }
                }
            )
            if (engineMsg.isNotBlank()) {
                Text(
                    engineMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        SettingsGroup("Access mode") {
            Text(
                "Downloads to /Download/ need no root. These modes are for grabbing from protected locations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccessMode.entries.forEach { mode ->
                    MinimalChip(
                        selected = settings.accessMode == mode,
                        onClick = { vm.setAccessMode(mode) },
                        label = mode.name.lowercase(Locale.US)
                            .replaceFirstChar { it.uppercase(Locale.US) }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("Shizuku · ${ShizukuHelper.statusText()}", style = MaterialTheme.typography.bodySmall)
            Text("Root · ${RootHelper.statusText()}", style = MaterialTheme.typography.bodySmall)
        }

        SettingsGroup("Permissions") {
            PillButton("Notification settings") { openAppNotificationSettings(context) }
            PillButton("Accessibility (floating button)") { openAccessibilitySettings(context) }
            PillButton("Display over other apps") { openOverlaySettings(context) }
        }

        SettingsGroup("About") {
            Text(
                "ClipSave v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Free & open-source · MIT License · No ads, no telemetry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PillButton("Source code") {
                openUrl(context, "https://github.com/abdellatif-laghjaj/android-all-in-one-video-downloader")
            }
            PillButton("License") {
                openUrl(
                    context,
                    "https://github.com/abdellatif-laghjaj/android-all-in-one-video-downloader/blob/main/LICENSE"
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        SectionLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

private fun openAccessibilitySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}
