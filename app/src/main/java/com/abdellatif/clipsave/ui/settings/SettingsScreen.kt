package com.abdellatif.clipsave.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdellatif.clipsave.BuildConfig
import com.abdellatif.clipsave.data.preferences.AccentColor
import com.abdellatif.clipsave.data.preferences.AccessMode
import com.abdellatif.clipsave.data.preferences.NetworkPolicy
import com.abdellatif.clipsave.data.preferences.ThemeMode
import com.abdellatif.clipsave.download.YtDlpEngine
import com.abdellatif.clipsave.privileged.RootHelper
import com.abdellatif.clipsave.privileged.ShizukuHelper
import com.abdellatif.clipsave.ui.AppViewModel
import com.abdellatif.clipsave.ui.components.MinimalChip
import com.abdellatif.clipsave.ui.components.SectionLabel
import com.abdellatif.clipsave.ui.theme.accentSwatch
import java.util.Locale
import androidx.core.net.toUri

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cookieStatus by vm.cookieStatus.collectAsStateWithLifecycle()
    val cookiePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importCookies(uri)
    }
    var engineMsg by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    val currentLocale = LocalConfiguration.current.locales[0]
    val captionLanguage = currentLocale.displayLanguage.takeIf(String::isNotBlank) ?: "English"

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
            Text(
                "Theme",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            Text(
                "Color",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val darkSwatches = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentColor.entries.forEach { color ->
                    AccentColorOption(
                        color = color,
                        darkTheme = darkSwatches,
                        selected = settings.accentColor == color,
                        onClick = { vm.setAccentColor(color) }
                    )
                }
            }
        }

        SettingsGroup("Downloads") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.networkPolicy == NetworkPolicy.UNMETERED_ONLY,
                        role = Role.Switch,
                        onValueChange = { enabled ->
                            vm.setNetworkPolicy(
                                if (enabled) NetworkPolicy.UNMETERED_ONLY else NetworkPolicy.ANY
                            )
                        }
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        "Unmetered downloads only",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Waits on mobile data or metered Wi-Fi, then resumes automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.networkPolicy == NetworkPolicy.UNMETERED_ONLY,
                    onCheckedChange = null
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.embedSubtitles,
                        role = Role.Switch,
                        onValueChange = vm::setEmbedSubtitles
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        "Embed available subtitles",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (currentLocale.language.equals("en", ignoreCase = true)) {
                            "Adds available English captions to downloaded videos."
                        } else {
                            "Adds available $captionLanguage captions, with English fallback."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.embedSubtitles,
                    onCheckedChange = null
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "ClipSave runs up to two downloads at once and uses four efficient segments when supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

        SettingsGroup("Site access") {
            Text(
                "Import cookies only for media you are allowed to access. The file stays in ClipSave's private storage and is excluded from backups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (cookieStatus.configured) {
                    val siteLabel = if (cookieStatus.domainCount == 1) "site" else "sites"
                    "Ready · ${cookieStatus.cookieCount} cookies across ${cookieStatus.domainCount} $siteLabel"
                } else {
                    "No cookies imported"
                },
                style = MaterialTheme.typography.titleSmall
            )
            PillButton(
                label = if (cookieStatus.configured) "Replace cookies.txt" else "Import cookies.txt",
                enabled = !cookieStatus.isWorking,
                loading = cookieStatus.isWorking,
                onClick = { cookiePicker.launch(arrayOf("text/plain", "*/*")) }
            )
            if (cookieStatus.configured) {
                PillButton(
                    label = "Remove saved cookies",
                    enabled = !cookieStatus.isWorking,
                    danger = true,
                    onClick = vm::removeCookies
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
private fun AccentColorOption(
    color: AccentColor,
    darkTheme: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "accentPressScale"
    )
    val outerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(durationMillis = 160),
        label = "accentOuterColor"
    )
    val outerWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 160),
        label = "accentOuterWidth"
    )
    val label = color.name.lowercase(Locale.US)
        .replaceFirstChar { it.uppercase(Locale.US) }

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .border(outerWidth, outerColor, CircleShape)
            .padding(4.dp)
            .semantics { contentDescription = "$label color" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = CircleShape
                )
                .padding(2.dp)
                .clip(CircleShape)
                .background(accentSwatch(color, darkTheme))
        )
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
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (danger) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface
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
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
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
        "package:${context.packageName}".toUri()
    )
    runCatching { context.startActivity(intent) }
}
