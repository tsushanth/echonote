package com.kreativekoala.echonote.ui.navigation

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.echonote.R
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.service.AppOpenTracker
import com.kreativekoala.echonote.ui.consent.ConsentScreen
import com.kreativekoala.echonote.ui.organization.FolderDetailScreen
import com.kreativekoala.echonote.ui.organization.FolderViewModel
import com.kreativekoala.echonote.ui.organization.RecordingsListScreen
import com.kreativekoala.echonote.ui.organization.FoldersScreen
import com.kreativekoala.echonote.ui.organization.FavoritesScreen
import com.kreativekoala.echonote.ui.organization.RecycleBinScreen
import com.kreativekoala.echonote.ui.playback.PlaybackScreen
import com.kreativekoala.echonote.ui.playback.PlayerViewModel
import com.kreativekoala.echonote.ui.recording.RecordingScreen
import com.kreativekoala.echonote.ui.settings.SettingsScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

private const val CONSENT_PREFS = "consent_prefs"
private const val KEY_CONTRIBUTION_CONSENT_SHOWN = "contribution_consent_shown"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavigationEntryPoint {
    fun appOpenTracker(): AppOpenTracker
    fun settingsRepository(): SettingsRepository
}

sealed class BottomNavItem(val route: String, val labelResId: Int, val icon: ImageVector) {
    data object Recordings : BottomNavItem("recordings", R.string.nav_recordings, Icons.Default.Mic)
    data object Folders : BottomNavItem("folders", R.string.nav_folders, Icons.Default.Folder)
    data object Favorites : BottomNavItem("favorites", R.string.nav_favorites, Icons.Default.Favorite)
    data object Settings : BottomNavItem("settings", R.string.nav_settings, Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoNoteNavigation() {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NavigationEntryPoint::class.java
        )
    }
    val appOpenTracker = entryPoint.appOpenTracker()
    val settingsRepository = entryPoint.settingsRepository()
    val coroutineScope = rememberCoroutineScope()

    // Voice-data contribution consent: shown once ever, on the SECOND app open. Gated on
    // its own SharedPreferences flag.
    val consentPrefs = remember {
        context.getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE)
    }
    val contributionConsentShown = remember {
        consentPrefs.getBoolean(KEY_CONTRIBUTION_CONSENT_SHOWN, false)
    }
    var showConsentScreen by remember {
        mutableStateOf(!contributionConsentShown && appOpenTracker.getOpenCount() == 2)
    }

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
    var showRecycleBin by remember { mutableStateOf(false) }

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

    // Recycle Bin as a full-screen overlay
    if (showRecycleBin) {
        RecycleBinScreen(onBack = { showRecycleBin = false })
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = stringResource(item.labelResId)) },
                            label = { Text(stringResource(item.labelResId)) },
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
                        },
                        onRecycleBinClick = { showRecycleBin = true }
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

        // Voice-data contribution consent: shown once ever, on the second app open.
        if (showConsentScreen) {
            ConsentScreen(
                onAccept = {
                    coroutineScope.launch { settingsRepository.setContributeVoiceData(true) }
                    consentPrefs.edit().putBoolean(KEY_CONTRIBUTION_CONSENT_SHOWN, true).apply()
                    showConsentScreen = false
                },
                onDecline = {
                    consentPrefs.edit().putBoolean(KEY_CONTRIBUTION_CONSENT_SHOWN, true).apply()
                    showConsentScreen = false
                }
            )
        }
    }
}
