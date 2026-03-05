package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.util.Constants
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class PremiumManager @Inject constructor(
    private val billingService: BillingService
) {
    val isPremium: StateFlow<Boolean> = billingService.isSubscribed

    fun canCreateRecording(currentCount: Int): Boolean {
        return isPremium.value || currentCount < Constants.Premium.FREE_RECORDING_LIMIT
    }

    fun canCreateFolder(currentCount: Int): Boolean {
        return isPremium.value || currentCount < Constants.Premium.FREE_FOLDER_LIMIT
    }

    fun canCreateBookmark(currentCount: Int): Boolean {
        return isPremium.value || currentCount < Constants.Premium.FREE_BOOKMARK_LIMIT
    }

    fun canUseTranscription(): Boolean = isPremium.value

    fun refreshPremiumStatus() {
        billingService.queryExistingPurchases()
    }
}
