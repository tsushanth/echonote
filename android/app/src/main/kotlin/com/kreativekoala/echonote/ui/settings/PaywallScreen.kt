package com.kreativekoala.echonote.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions

/**
 * Paywall screen - uses RevenueCat's dashboard-configured paywall.
 */
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Paywall(
            options = PaywallOptions.Builder(dismissRequest = { onDismiss() })
                .setShouldDisplayDismissButton(true)
                .build()
        )
    }
}
