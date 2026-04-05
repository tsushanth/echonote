package com.kreativekoala.echonote.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.kreativekoala.echonote.service.AppOpenTracker
import com.kreativekoala.echonote.service.BillingService
import com.kreativekoala.echonote.ui.organization.FolderDetailScreen
import com.kreativekoala.echonote.ui.organization.FolderViewModel
import com.kreativekoala.echonote.ui.organization.RecordingsListScreen
import com.kreativekoala.echonote.ui.organization.FoldersScreen
import com.kreativekoala.echonote.ui.organization.FavoritesScreen
import com.kreativekoala.echonote.ui.playback.PlaybackScreen
import com.kreativekoala.echonote.ui.playback.PlayerViewModel
import com.kreativekoala.echonote.ui.recording.RecordingScreen
import com.kreativekoala.echonote.ui.settings.SettingsScreen
import com.kreativekoala.paywallkit.models.PaywallFeature
import com.kreativekoala.paywallkit.models.PaywallProduct
import com.kreativekoala.paywallkit.models.PaywallTheme
import com.kreativekoala.paywallkit.view.PaywallView
import androidx.compose.ui.graphics.Color
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavigationEntryPoint {
    fun appOpenTracker(): AppOpenTracker
    fun billingService(): BillingService
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
    val billingService = entryPoint.billingService()

    val isSubscribed by billingService.isSubscribed.collectAsState()
    var paywallDismissed by remember { mutableStateOf(false) }
    val shouldShowPaywall = appOpenTracker.hasExceededFreeLimit() && !isSubscribed && !paywallDismissed

    if (shouldShowPaywall) {
        val products by billingService.products.collectAsState()
        val activity = context as? Activity

        // Wait for products to load
        if (products.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F)),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF6C63FF))
            }
            return
        }

        // Map RevenueCat products to PaywallKit products
        val paywallProducts = products.map { product ->
            PaywallProduct(
                id = product.productId,
                localizedPrice = product.price,
                price = product.rcPackage?.product?.price?.amountMicros?.let { it / 1_000_000.0 } ?: 0.0,
                currencyCode = product.rcPackage?.product?.price?.currencyCode ?: "USD",
                trialDays = 3,
                period = when {
                    product.billingPeriod.contains("W") -> PaywallProduct.Period.WEEKLY
                    product.billingPeriod.contains("M") && !product.billingPeriod.contains("Y") -> PaywallProduct.Period.MONTHLY
                    product.billingPeriod.contains("Y") -> PaywallProduct.Period.YEARLY
                    else -> PaywallProduct.Period.MONTHLY
                }
            )
        }

        val features = listOf(
            PaywallFeature("🎙️", "Unlimited Recordings", "Record without limits"),
            PaywallFeature("📝", "Transcription", "Convert speech to text"),
            PaywallFeature("✂️", "Audio Editing", "Trim and enhance recordings"),
            PaywallFeature("⚡", "Playback Controls", "Speed adjustment & skip silence"),
            PaywallFeature("📁", "Unlimited Folders", "Organize everything")
        )

        PaywallView(
            appId = "clearvoice",
            appName = "ClearVoice",
            features = features,
            products = paywallProducts,
            theme = PaywallTheme(accent = Color(0xFF6C63FF), accent2 = Color(0xFF9C27B0)),
            showWinback = true,
            isDismissible = true,
            onPurchase = { productId ->
                val product = products.firstOrNull { it.productId == productId }
                if (product != null && activity != null) {
                    billingService.purchase(activity, product)
                }
            },
            onRestore = { billingService.restorePurchases() },
            onDismiss = { paywallDismissed = true }
        )
        return
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
