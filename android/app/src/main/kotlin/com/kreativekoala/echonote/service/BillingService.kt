package com.kreativekoala.echonote.service

import android.app.Activity
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SubscriptionProduct(
    val productId: String,
    val title: String,
    val price: String,
    val description: String,
    val billingPeriod: String,
    val rcPackage: Package? = null
)

class BillingService @Inject constructor() {

    companion object {
        private const val TAG = "BillingService"
        private const val ENTITLEMENT_ID = "premium"
    }

    private val _products = MutableStateFlow<List<SubscriptionProduct>>(emptyList())
    val products: StateFlow<List<SubscriptionProduct>> = _products

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun initialize() {
        loadOfferings()
        queryExistingPurchases()
    }

    private fun loadOfferings() {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Log.w(TAG, "Error fetching offerings: ${error.message}")
            },
            onSuccess = { offerings ->
                val currentOffering = offerings.current ?: return@getOfferingsWith
                val subscriptionProducts = currentOffering.availablePackages.mapNotNull { pkg ->
                    val product = pkg.product
                    val period = product.period?.iso8601 ?: ""

                    SubscriptionProduct(
                        productId = product.id,
                        title = product.title,
                        price = product.price.formatted,
                        description = product.description,
                        billingPeriod = period,
                        rcPackage = pkg
                    )
                }.sortedBy { product ->
                    when {
                        product.billingPeriod.contains("W") -> 0
                        product.billingPeriod.contains("M") && !product.billingPeriod.contains("Y") -> 1
                        product.billingPeriod.contains("Y") -> 2
                        else -> 3
                    }
                }
                _products.value = subscriptionProducts
            }
        )
    }

    fun queryExistingPurchases() {
        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    _isSubscribed.value =
                        customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                }

                override fun onError(error: PurchasesError) {
                    Log.w(TAG, "Error checking subscription status: ${error.message}")
                }
            }
        )
    }

    fun purchase(activity: Activity, product: SubscriptionProduct) {
        val pkg = product.rcPackage ?: return

        _isLoading.value = true

        Purchases.sharedInstance.purchaseWith(
            purchaseParams = PurchaseParams.Builder(activity, pkg).build(),
            onError = { error, userCancelled ->
                _isLoading.value = false
                if (userCancelled) {
                    Log.d(TAG, "Purchase cancelled by user")
                } else {
                    Log.w(TAG, "Purchase failed: ${error.message}")
                }
            },
            onSuccess = { _, customerInfo ->
                _isLoading.value = false
                _isSubscribed.value =
                    customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true

                // Track purchase events for ad attribution
                val productId = pkg.product.id
                val price = pkg.product.price.amountMicros / 1_000_000.0
                val currency = pkg.product.price.currencyCode
                FirebaseAnalyticsHelper.logPurchaseCompleted(productId, price)
                TikTokHelper.trackEvent("purchase_success")
            }
        )
    }

    fun restorePurchases() {
        _isLoading.value = true

        Purchases.sharedInstance.restorePurchasesWith(
            onError = { error ->
                _isLoading.value = false
                Log.w(TAG, "Restore failed: ${error.message}")
            },
            onSuccess = { customerInfo ->
                _isLoading.value = false
                _isSubscribed.value =
                    customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
            }
        )
    }
}
