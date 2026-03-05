package com.kreativekoala.echonote.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.kreativekoala.echonote.service.BillingService
import com.kreativekoala.echonote.service.SubscriptionProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingService: BillingService
) : ViewModel() {

    val products = billingService.products
    val isSubscribed = billingService.isSubscribed
    val isLoading = billingService.isLoading

    fun purchase(activity: Activity, product: SubscriptionProduct) {
        billingService.purchase(activity, product)
    }

    fun restorePurchases() {
        billingService.restorePurchases()
    }
}
