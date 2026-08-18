package com.kreativekoala.echonote.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.kreativekoala.echonote.service.AppOpenTracker
import com.kreativekoala.echonote.service.BillingService
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
import com.kreativekoala.paywallkit.models.PaywallFeature
import com.kreativekoala.paywallkit.models.PaywallProduct
import com.kreativekoala.paywallkit.models.PaywallTheme
import com.kreativekoala.paywallkit.manager.PromoCodeManager
import com.kreativekoala.paywallkit.view.PaywallView
import com.kreativekoala.ratingkit.RatingKit
import androidx.compose.ui.graphics.Color
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val PAYWALL_PREFS = "paywall_prefs"
private const val KEY_ONBOARDING_PAYWALL_SHOWN = "onboarding_paywall_shown"

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

    // Notify RatingKit only on the *transition* to subscribed during this
    // session. Skipping the initial emission avoids re-prompting already-
    // subscribed users on every cold start.
    var firstSubscriptionEmission by remember { mutableStateOf(true) }
    LaunchedEffect(isSubscribed) {
        if (isSubscribed && !firstSubscriptionEmission) {
            (context as? Activity)?.let { RatingKit.trackPurchase(it) }
        }
        firstSubscriptionEmission = false
    }

    val paywallPrefs = remember {
        context.getSharedPreferences(PAYWALL_PREFS, Context.MODE_PRIVATE)
    }

    // In-memory only: hard paywall dismissal is intentionally NOT persisted.
    // Each cold-start the hard gate will re-trigger when the open limit is exceeded,
    // but within the same session it only shows once.
    var hardPaywallDismissed by remember { mutableStateOf(false) }

    // Onboarding paywall: show once ever on first launch (session 1), soft dismiss.
    val onboardingPaywallShown = remember {
        paywallPrefs.getBoolean(KEY_ONBOARDING_PAYWALL_SHOWN, false)
    }
    var showOnboardingPaywall by remember {
        mutableStateOf(!onboardingPaywallShown && !isSubscribed && appOpenTracker.getOpenCount() == 1)
    }

    val shouldShowHardPaywall = appOpenTracker.hasExceededFreeLimit() && !isSubscribed && !hardPaywallDismissed

    if (shouldShowHardPaywall) {
        val products by billingService.products.collectAsState()
        val activity = context as? Activity

        // Wait for products to load
        val billingFailed by billingService.connectionFailed.collectAsState()

        if (products.isEmpty() && !billingFailed) {
            // Still loading — show spinner with timeout safety net
            var timedOut by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(10000)
                timedOut = true
            }
            if (!timedOut) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F)),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6C63FF))
                }
                return
            }
        }

        if (products.isEmpty()) {
            // Billing failed after retries or timed out — show retry screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
                        )
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Unable to load subscription options",
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please check your internet connection and try again.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { billingService.initialize() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
                    ) {
                        Text("Try Again")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { hardPaywallDismissed = true }) {
                        Text("Continue without premium", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                    }
                }
            }
            return
        }

        // Map billing products to PaywallKit products
        val paywallProducts = products.map { product ->
            PaywallProduct(
                id = product.productId,
                localizedPrice = product.price,
                price = product.priceAmountMicros / 1_000_000.0,
                currencyCode = product.currencyCode,
                trialDays = product.trialDays ?: 3,
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
            placement = if (PromoCodeManager.activeCode != null) "promo_code_onboarding" else "onboarding",
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
            onRedeemCode = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem?code=promo-1month-free"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { activity?.startActivity(intent) } catch (_: Exception) {}
            },
            onDismiss = {
                hardPaywallDismissed = true
            }
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

        // Onboarding paywall: shown once ever on first launch as a full-screen overlay.
        // Soft paywall — dismissing lets the user in. Subscribing marks premium.
        if (showOnboardingPaywall) {
            val products by billingService.products.collectAsState()
            val activity = context as? Activity

            if (products.isNotEmpty()) {
                val paywallProducts = products.map { product ->
                    PaywallProduct(
                        id = product.productId,
                        localizedPrice = product.price,
                        price = product.priceAmountMicros / 1_000_000.0,
                        currencyCode = product.currencyCode,
                        trialDays = product.trialDays ?: 3,
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
            placement = if (PromoCodeManager.activeCode != null) "promo_code_onboarding" else "onboarding",
                    appName = "ClearVoice",
                    features = features,
                    products = paywallProducts,
                    theme = PaywallTheme(accent = Color(0xFF6C63FF), accent2 = Color(0xFF9C27B0)),
                    showWinback = false,
                    isDismissible = true,
                    onPurchase = { productId ->
                        val product = products.firstOrNull { it.productId == productId }
                        if (product != null && activity != null) {
                            billingService.purchase(activity, product)
                        }
                    },
                    onRestore = { billingService.restorePurchases() },
                    onRedeemCode = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem?code=promo-1month-free"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { activity?.startActivity(intent) } catch (_: Exception) {}
                    },
                    onDismiss = {
                        paywallPrefs.edit()
                            .putBoolean(KEY_ONBOARDING_PAYWALL_SHOWN, true)
                            .apply()
                        showOnboardingPaywall = false
                    }
                )
            }

            // Once subscribed, close onboarding paywall and mark shown
            LaunchedEffect(isSubscribed) {
                if (isSubscribed) {
                    paywallPrefs.edit()
                        .putBoolean(KEY_ONBOARDING_PAYWALL_SHOWN, true)
                        .apply()
                    showOnboardingPaywall = false
                }
            }
        }
    }
}
