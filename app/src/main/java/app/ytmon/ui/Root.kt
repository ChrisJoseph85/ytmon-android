package app.ytmon.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

object Routes {
    const val LIBRARY = "library"
    const val ADD = "add"
    const val DOWNLOADS = "downloads"
    const val SOURCES = "sources"
    const val SETTINGS = "settings"
    const val CHANNELS = "channels"
    const val BROWSER = "browser"
    const val PLAYER = "player/{videoId}"
    fun player(videoId: String) = "player/$videoId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtMonRoot() {
    YtMonTheme {
        val nav = rememberNavController()
        val conn: ConnectivityViewModel = ytViewModel { ConnectivityViewModel(it.repo) }
        val server by conn.state.collectAsStateWithLifecycle()
        val connVm = conn

        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val showBars = route != Routes.PLAYER

        Scaffold(
            topBar = {
                if (showBars) {
                    Column {
                        TopAppBar(
                            title = { Text(titleFor(route)) },
                            actions = {
                                when (route) {
                                    Routes.LIBRARY -> {
                                        IconButton(onClick = { nav.navigate(Routes.BROWSER) }) {
                                            Icon(Icons.Default.Folder, "Browse library")
                                        }
                                        IconButton(onClick = { nav.navigate(Routes.CHANNELS) }) {
                                            Icon(Icons.Default.Subscriptions, "Channels")
                                        }
                                    }
                                    Routes.SETTINGS -> {
                                        IconButton(onClick = { nav.navigate(Routes.CHANNELS) }) {
                                            Icon(Icons.Default.Subscriptions, "Channels")
                                        }
                                        IconButton(onClick = { connVm.refreshNow() }) {
                                            Icon(Icons.Default.PlaylistPlay, "Refresh server status")
                                        }
                                    }
                                    else -> IconButton(onClick = { connVm.refreshNow() }) {
                                        Icon(Icons.Default.PlaylistPlay, "Refresh")
                                    }
                                }
                            },
                        )
                        // Connectivity indicator: banner only when the server is not answering.
                        if (server.checked && !server.reachable) {
                            UnreachableBanner()
                        } else if (server.degraded) {
                            DegradedBanner()
                        }
                    }
                }
            },
            bottomBar = {
                if (showBars) {
                    NavigationBar {
                        NavItem(nav, route, Routes.LIBRARY, "Library", Icons.Default.Home)
                        NavItem(nav, route, Routes.ADD, "Add", Icons.Default.Add)
                        NavItem(nav, route, Routes.DOWNLOADS, "Downloads", Icons.Default.Download)
                        NavItem(nav, route, Routes.SOURCES, "Sources", Icons.Default.PlaylistPlay)
                        NavItem(nav, route, Routes.SETTINGS, "Settings", Icons.Default.Settings)
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.LIBRARY,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onOpen = { nav.navigate(Routes.player(it)) },
                        onBrowse = { nav.navigate(Routes.BROWSER) },
                    )
                }
                composable(Routes.ADD) { AddLinkScreen() }
                composable(Routes.DOWNLOADS) { DownloadsScreen(onOpen = { nav.navigate(Routes.player(it)) }) }
                composable(Routes.SOURCES) { SourcesScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.CHANNELS) { ChannelsScreen() }
                composable(Routes.BROWSER) { BrowserScreen(onOpen = { nav.navigate(Routes.player(it)) }) }
                composable(Routes.PLAYER) { entry ->
                    val id = entry.arguments?.getString("videoId").orEmpty()
                    PlayerScreen(videoId = id, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/**
 * Bottom-bar item. Implemented directly on top of NavigationBar rather than
 * NavigationBarItem, which is not present in the resolved material3 artifact.
 */
@Composable
private fun RowScope.NavItem(
    nav: NavHostController,
    current: String?,
    route: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val selected = current == route
    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            // weight, not fillMaxWidth: NavigationBar lays children out in a Row,
            // so full-width children push the others off-screen.
            .weight(1f)
            .clickable {
                if (!selected) {
                    nav.navigate(route) {
                        popUpTo(Routes.LIBRARY) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else tint)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun titleFor(route: String?): String = when (route) {
    Routes.LIBRARY -> "Library"
    Routes.ADD -> "Add link"
    Routes.DOWNLOADS -> "Downloads"
    Routes.SOURCES -> "Sources"
    Routes.SETTINGS -> "Settings"
    Routes.CHANNELS -> "Channels"
    Routes.BROWSER -> "Browse library"
    else -> "YTMon"
}

/** Shown only when GET /health fails. */
@Composable
private fun UnreachableBanner() {
    Surface(color = MaterialTheme.colorScheme.error) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Can't reach the server on ${app.ytmon.data.ServerConfig.displayAddress}",
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Is Termux running ytmon? The server sleeps when the phone does.",
                color = MaterialTheme.colorScheme.onError.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun DegradedBanner() {
    Surface(color = Color(0xFF7A5B00)) {
        Text(
            "Server is running in degraded mode",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}