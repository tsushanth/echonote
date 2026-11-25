package com.kreativekoala.echonote.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode

/** Which Play Billing answers are worth a failure report. */
object BillingFailurePolicy {
    /** Normal on real phones: the user cancelled, there is no Play billing here, or the network or Play service blinked. */
    private val EXPECTED = setOf(
        BillingResponseCode.USER_CANCELED,
        BillingResponseCode.BILLING_UNAVAILABLE,
        BillingResponseCode.FEATURE_NOT_SUPPORTED,
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.SERVICE_DISCONNECTED,
        BillingResponseCode.NETWORK_ERROR,
        @Suppress("DEPRECATION") BillingResponseCode.SERVICE_TIMEOUT
    )

    fun shouldReport(responseCode: Int): Boolean = responseCode != BillingResponseCode.OK && responseCode !in EXPECTED
}
