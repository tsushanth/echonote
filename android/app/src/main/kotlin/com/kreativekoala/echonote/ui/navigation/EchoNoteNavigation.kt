package com.kreativekoala.echonote.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kreativekoala.echonote.ui.organization.FolderDetailScreen
import com.kreativekoala.echonote.ui.organization.FolderViewModel
import com.kreativekoala.echonote.ui.organization.RecordingsListScreen
import com.kreativekoala.echonote.ui.organization.FoldersScreen
import com.kreativekoala.echonote.ui.organization.FavoritesScreen
import com.kreativekoala.echonote.ui.playback.PlaybackScreen
import com.kreativekoala.echonote.ui.playback.PlayerViewModel
import com.kreativekoala.echonote.ui.recording.RecordingScreen
import com.kreativekoala.echonote.ui.settings.SettingsScreen

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    data object Recordings : BottomNavItem("recordings", "Recordings", Icons.Default.Mic)
    data object Folders : BottomNavItem("folders", "Folders", Icons.Default.Folder)
    data object Favorites : BottomNavItem("favorites", "Favorites", Icons.Default.Favorite)
    data object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoNoteNavigation() {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Recordings,
        BottomNavItem.Folders,
        BottomNavItem.Favorites,
        BottomNavItem.Settings
    )

    // Shared view models scoped to navigation host
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val folderViewModel: FolderViewModel = hiltViewModel()
    var showRecordingSheet by remember { mutableStateOf(false) }
    var showPlaybackScreen by remember { mutableStateOf(false) }
    var showFolderDetail by remember { mutableStateOf(false) }

    val folders by folderViewModel.folders.collectAsState()
    val folderRecordings by folderViewModel.folderRecordings.collectAsState()
    val selectedFolderName by folderViewModel.selectedFolderName.collectAsState()

    // Recording screen as a full-screen overlay
    if (showRecordingSheet) {
        RecordingScreen(
            onDismiss = { showRecordingSheet = false }
        )
        return
    }

    // Playback screen as a full-screen overlay
    if (showPlaybackScreen) {
        PlaybackScreen(
            viewModel = playerViewModel,
            onDismiss = { showPlaybackScreen = false }
        )
        return
    }

    // Folder detail screen as a full-screen overlay
    if (showFolderDetail) {
        FolderDetailScreen(
            folderName = selectedFolderName,
            recordings = folderRecordings,
            onBack = {
                showFolderDetail = false
                folderViewModel.clearSelectedFolder()
            },
            onRecordingClick = { recording ->
                playerViewModel.loadRecording(recording)
                showPlaybackScreen = true
            },
            onToggleFavorite = { recording ->
                folderViewModel.toggleFavorite(recording)
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Recordings.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Recordings.route) {
                RecordingsListScreen(
                    onRecordClick = { showRecordingSheet = true },
                    onRecordingClick = { recording ->
                        playerViewModel.loadRecording(recording)
                        showPlaybackScreen = true
                    },
                    folders = folders,
                    onMoveToFolder = { recording, folderId ->
                        folderViewModel.moveRecordingToFolder(recording.id, folderId)
                    }
                )
            }
            composable(BottomNavItem.Folders.route) {
                FoldersScreen(
                    onFolderClick = { folder ->
                        folderViewModel.selectFolder(folder)
                        showFolderDetail = true
                    }
                )
            }
            composable(BottomNavItem.Favorites.route) {
                FavoritesScreen(
                    onRecordingClick = { recording ->
                        playerViewModel.loadRecording(recording)
                        showPlaybackScreen = true
                    }
                )
            }
            composable(BottomNavItem.Settings.route) { SettingsScreen() }
        }
    }
}
