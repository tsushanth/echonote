package com.kreativekoala.echonote.billing

/** Outcome of asking Google Play which subscriptions the signed-in Play account holds right now. */
sealed interface BillingQueryResult {
    /** Play answered. [activeProductIds] are the product ids of purchases in the PURCHASED state (pending ones are left out). */
    data class Ok(val activeProductIds: Set<String>) : BillingQueryResult

    /** Play could not be asked (no Play Store, no network to Play, billing not supported, timeout). */
    data class Unavailable(val reason: String) : BillingQueryResult
}

/**
 * The only thing the app needs from Play Billing today: a read-only look at existing subscription purchases.
 * Kept behind an interface so the entitlement rules can be unit tested with a fake.
 */
interface BillingGateway {
    suspend fun queryActiveSubscriptions(): BillingQueryResult
}
