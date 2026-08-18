package com.kreativekoala.echonote.service

import android.app.Activity
import com.kreativekoala.paywallkit.manager.PromoCodeManager
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SubscriptionProduct(
    val productId: String,
    val title: String,
    val price: String,
    val description: String,
    val billingPeriod: String,
    // Native billing fields (replaces rcPackage)
    val priceAmountMicros: Long = 0L,
    val currencyCode: String = "USD",
    val trialDays: Int? = null,
    val productDetails: ProductDetails? = null,
    val offerToken: String = ""
)

@Singleton
class BillingService @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingService"
        private val PRODUCT_IDS = listOf(
            "clearvoice_premium_weekly",
            "clearvoice_premium_monthly",
            "clearvoice_premium_yearly"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _products = MutableStateFlow<List<SubscriptionProduct>>(emptyList())
    val products: StateFlow<List<SubscriptionProduct>> = _products

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private val _connectionFailed = MutableStateFlow(false)
    val connectionFailed: StateFlow<Boolean> = _connectionFailed

    private var retryCount = 0
    private val maxRetries = 3

    fun initialize() {
        _connectionFailed.value = false
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    retryCount = 0
                    scope.launch {
                        queryProducts()
                        queryExistingPurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    retryOnFailure()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                retryOnFailure()
            }
        })
    }

    private fun retryOnFailure() {
        if (retryCount < maxRetries) {
            retryCount++
            val delayMs = (1000L * retryCount) // 1s, 2s, 3s
            Log.d(TAG, "Retrying billing connection (attempt $retryCount/$maxRetries) in ${delayMs}ms")
            scope.launch {
                kotlinx.coroutines.delay(delayMs)
                initialize()
            }
        } else {
            Log.w(TAG, "Billing connection failed after $maxRetries retries")
            _connectionFailed.value = true
        }
    }

    private suspend fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCT_IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val subscriptionProducts = result.productDetailsList?.mapNotNull { details ->
                val subOffer = details.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
                val offerToken = subOffer.offerToken

                // Detect free trial from pricing phases
                val trialDays = subOffer.pricingPhases.pricingPhaseList
                    .firstOrNull { it.priceAmountMicros == 0L }
                    ?.let { phase ->
                        val bp = phase.billingPeriod
                        when {
                            bp.contains("D") -> bp.filter { it.isDigit() }.toIntOrNull()
                            bp.contains("W") -> (bp.filter { it.isDigit() }.toIntOrNull() ?: 1) * 7
                            else -> null
                        }
                    }

                // Get the paid pricing phase
                val paidPhase = subOffer.pricingPhases.pricingPhaseList
                    .firstOrNull { it.priceAmountMicros > 0 } ?: return@mapNotNull null

                // Map billing period to ISO 8601-ish string used by rest of codebase
                val billingPeriod = paidPhase.billingPeriod // e.g. P1W, P1M, P1Y

                SubscriptionProduct(
                    productId = details.productId,
                    title = details.title,
                    price = paidPhase.formattedPrice,
                    description = details.description,
                    billingPeriod = billingPeriod,
                    priceAmountMicros = paidPhase.priceAmountMicros,
                    currencyCode = paidPhase.priceCurrencyCode,
                    trialDays = trialDays,
                    productDetails = details,
                    offerToken = offerToken
                )
            }?.sortedBy { product ->
                when {
                    product.billingPeriod.contains("W") -> 0
                    product.billingPeriod == "P1M" || (product.billingPeriod.contains("M") && !product.billingPeriod.contains("Y")) -> 1
                    product.billingPeriod.contains("Y") -> 2
                    else -> 3
                }
            } ?: emptyList()

            _products.value = subscriptionProducts
            Log.d(TAG, "Loaded ${subscriptionProducts.size} products")
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
        }
    }

    fun queryExistingPurchases() {
        scope.launch {
            val result = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            val hasActive = result.purchasesList.any { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            _isSubscribed.value = hasActive
            Log.d(TAG, "Existing purchases check — subscribed: $hasActive")

            // Acknowledge any unacknowledged purchases
            result.purchasesList.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
        }
    }

    fun purchase(activity: Activity, product: SubscriptionProduct) {
        val details = product.productDetails ?: return
        val offerToken = product.offerToken.ifEmpty { return }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        _isLoading.value = true
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _isLoading.value = false
            Log.w(TAG, "launchBillingFlow failed: ${result.debugMessage}")
        }
    }

    fun restorePurchases() {
        _isLoading.value = true
        scope.launch {
            queryExistingPurchases()
            _isLoading.value = false
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        scope.launch {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = billingClient.acknowledgePurchase(params)
            Log.d(TAG, "Acknowledge result: ${result.responseCode}")
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _isLoading.value = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        _isSubscribed.value = true
                        PromoCodeManager.clearAfterConversion()
                        if (!purchase.isAcknowledged) acknowledgePurchase(purchase)
                        val productId = purchase.products.firstOrNull() ?: ""
                        val price = _products.value.firstOrNull { it.productId == productId }
                            ?.priceAmountMicros?.let { it / 1_000_000.0 } ?: 0.0
                        FirebaseAnalyticsHelper.logPurchaseCompleted(productId, price)
                        TikTokHelper.trackEvent("purchase_success")
                        // PaywallKit conversion telemetry — Supabase paywall_events row so
                        // we can reconcile Android purchases against Play sales (parity with
                        // VibeBuild Android tracking).
                        com.kreativekoala.paywallkit.manager.PaywallManager.trackEvent(
                            appId = "clearvoice",
                            placement = "play_billing_confirmed",
                            templateId = "default",
                            event = "purchased",
                            productId = productId,
                        )
                        Log.d(TAG, "Purchase successful: ${purchase.products}")
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                Log.d(TAG, "Purchase cancelled by user")
            else ->
                Log.w(TAG, "Purchase failed: ${result.debugMessage}")
        }
    }
}
