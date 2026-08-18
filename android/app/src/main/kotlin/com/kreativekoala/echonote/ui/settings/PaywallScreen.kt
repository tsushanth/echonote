package com.kreativekoala.echonote.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.paywallkit.models.PaywallFeature
import com.kreativekoala.paywallkit.models.PaywallProduct
import com.kreativekoala.paywallkit.models.PaywallTheme
import com.kreativekoala.paywallkit.manager.PromoCodeManager
import com.kreativekoala.paywallkit.view.PaywallView

/**
 * Paywall screen — uses PaywallKit with A/B tested templates.
 */
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit = {}
) {
    BackHandler { onDismiss() }

    val viewModel: PaywallViewModel = hiltViewModel()
    val products by viewModel.products.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

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
                viewModel.purchase(activity, product)
            }
        },
        onRestore = { viewModel.restorePurchases() },
        onRedeemCode = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem?code=promo-1month-free"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { activity?.startActivity(intent) } catch (_: Exception) {}
        },
        onDismiss = onDismiss
    )
}
