package com.kreativekoala.echonote.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions

/**
 * Paywall screen - uses RevenueCat's dashboard-configured paywall.
 */
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit = {}
) {
    BackHandler { onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Close button overlay in case RC's dismiss button doesn't work
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .zIndex(10f)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }

        Paywall(
            options = PaywallOptions.Builder(dismissRequest = { onDismiss() })
                .setShouldDisplayDismissButton(true)
                .build()
        )
    }
}
