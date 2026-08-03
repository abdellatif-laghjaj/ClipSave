package com.abdellatif.clipsave.ui.navigation

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abdellatif.clipsave.R
import com.abdellatif.clipsave.ui.AppViewModel
import com.abdellatif.clipsave.ui.downloads.DownloadsScreen
import com.abdellatif.clipsave.ui.home.HomeScreen
import com.abdellatif.clipsave.ui.paste.PasteUrlScreen
import com.abdellatif.clipsave.ui.player.VideoPlayerScreen
import com.abdellatif.clipsave.ui.settings.SettingsScreen
import com.abdellatif.clipsave.data.model.Download
import com.abdellatif.clipsave.data.model.DownloadStatus
import com.abdellatif.clipsave.data.model.MediaType
import com.abdellatif.clipsave.media.SavedMediaManager
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val icon: Int) {
    HOME("home", "Home", R.drawable.home),
    DOWNLOADS("downloads", "Downloads", R.drawable.folder_donwloads),
    PASTE("paste", "New", R.drawable.paste),
    SETTINGS("settings", "Settings", R.drawable.settings)
}

@Composable
fun AppScaffold(
    vm: AppViewModel,
    launchRequest: AppLaunchRequest? = null,
    onLaunchRequestConsumed: (AppLaunchRequest) -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val downloadsLoaded by vm.downloadsLoaded.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // All top-level moves (tabs AND in-screen shortcuts like the Home FAB) must use this
    // single pattern. A plain navigate() would push a duplicate destination; popping it
    // later with saveState collides with the tab's saved back-stack state and wedges the
    // NavController, leaving taps on the start tab permanently ignored.
    fun navigateToTab(tab: Tab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openPlayer(downloadId: String) {
        navController.navigate("player/${android.net.Uri.encode(downloadId)}") {
            launchSingleTop = true
        }
    }

    fun shareMedia(download: Download) {
        SavedMediaManager.buildShareIntent(context, download)
            .onSuccess { shareIntent ->
                runCatching {
                    context.startActivity(Intent.createChooser(shareIntent, "Share saved media"))
                }.onFailure {
                    scope.launch { snackbarHostState.showSnackbar("No app can share this file.") }
                }
            }
            .onFailure { error ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        error.message ?: "Saved file is no longer available."
                    )
                }
            }
    }

    LaunchedEffect(vm) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(launchRequest?.key, downloadsLoaded, downloads) {
        val request = launchRequest ?: return@LaunchedEffect
        if (!downloadsLoaded) return@LaunchedEffect
        val item = request.downloadId?.let { id -> downloads.firstOrNull { it.id == id } }
        if (!request.showDownloads && item != null &&
            item.status == DownloadStatus.COMPLETED &&
            item.mediaType != MediaType.UNKNOWN &&
            !item.localUri.isNullOrBlank()
        ) {
            openPlayer(item.id)
        } else {
            navigateToTab(Tab.DOWNLOADS)
        }
        onLaunchRequestConsumed(request)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (current?.route in Tab.entries.map { it.route }) {
                MinimalNavBar(
                    selectedRoute = Tab.entries.firstOrNull { tab ->
                        current?.hierarchy?.any { it.route == tab.route } == true
                    }?.route,
                    onSelect = ::navigateToTab
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(180)) }
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    vm,
                    onGoToPaste = { navigateToTab(Tab.PASTE) },
                    onOpen = { openPlayer(it.id) },
                    onShare = ::shareMedia
                )
            }
            composable(Tab.DOWNLOADS.route) {
                DownloadsScreen(
                    vm,
                    onOpen = { openPlayer(it.id) },
                    onShare = ::shareMedia
                )
            }
            composable(Tab.PASTE.route) { PasteUrlScreen(vm) }
            composable(Tab.SETTINGS.route) { SettingsScreen(vm) }
            composable(
                route = "player/{downloadId}",
                arguments = listOf(navArgument("downloadId") { type = NavType.StringType })
            ) { entry ->
                val downloads by vm.downloads.collectAsStateWithLifecycle()
                val id = entry.arguments?.getString("downloadId")
                val item = downloads.firstOrNull { it.id == id }
                if (item != null) {
                    VideoPlayerScreen(
                        item,
                        onBack = navController::navigateUp,
                        onShare = { shareMedia(item) }
                    )
                }
            }
        }
    }
}

/** Flat bottom bar: hairline divider, no indicator pill, quiet unselected items. */
@Composable
private fun MinimalNavBar(selectedRoute: String?, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp)
            ) {
                Tab.entries.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = tab.route == selectedRoute,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(150),
        label = "navTint"
    )
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}
