package com.kreativekoala.echonote.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the app knows about the user's subscription.
 *
 * @property billingAvailable Google Play answered the last check. Premium UI rows must be hidden when false.
 * @property isPremium an active legacy subscription was seen (kept from the last good check while Play is unreachable).
 * @property checked at least one check finished since launch (successfully or not).
 */
data class EntitlementState(
    val billingAvailable: Boolean = false,
    val isPremium: Boolean = false,
    val checked: Boolean = false,
)

/** Remembers the last good answer so a flaky Play connection does not flip a subscriber to "not premium". */
interface EntitlementStore {
    fun loadIsPremium(): Boolean
    fun saveIsPremium(value: Boolean)
}

class PrefsEntitlementStore(context: Context) : EntitlementStore {
    private val prefs = context.applicationContext.getSharedPreferences("entitlement", Context.MODE_PRIVATE)
    override fun loadIsPremium(): Boolean = prefs.getBoolean(KEY_PREMIUM, false)
    override fun saveIsPremium(value: Boolean) { prefs.edit().putBoolean(KEY_PREMIUM, value).apply() }
    private companion object { const val KEY_PREMIUM = "is_premium" }
}

/**
 * Debug builds only: lets a tester force the billing answer on a device where the side-by-side debug package is
 * not known to Google Play (so real billing can never answer there). Release builds pass null.
 * Set with: adb shell run-as com.kreativekoala.echonote.debug ... (see docs/entitlements.md).
 */
interface EntitlementOverride {
    /** Null = no override, use the real answer. */
    fun forced(): EntitlementState?
}

class PrefsEntitlementOverride(context: Context) : EntitlementOverride {
    private val prefs = context.applicationContext.getSharedPreferences("entitlement_debug", Context.MODE_PRIVATE)
    override fun forced(): EntitlementState? {
        if (!prefs.contains("billing_available")) return null
        return EntitlementState(
            billingAvailable = prefs.getBoolean("billing_available", false),
            isPremium = prefs.getBoolean("is_premium", false),
            checked = true,
        )
    }
}

/**
 * Single source of truth for "is this user a subscriber?". Read-only: it asks Play for existing purchases of the three
 * legacy ClearVoice subscription ids. It gates nothing by itself; callers decide what, if anything, to show.
 *
 * Restore-on-launch is [refresh] called from the Application; there is no purchase flow.
 */
class EntitlementRepository(
    private val gateway: BillingGateway,
    private val store: EntitlementStore,
    private val checkEnabled: Boolean,
    private val override: EntitlementOverride? = null,
) {
    private val _state = MutableStateFlow(EntitlementState(isPremium = store.loadIsPremium()))
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    private val lock = Mutex()

    suspend fun refresh() = lock.withLock {
        override?.forced()?.let { _state.value = it; return }
        if (!checkEnabled) {
            _state.value = EntitlementState(billingAvailable = false, isPremium = false, checked = true)
            return
        }
        when (val result = gateway.queryActiveSubscriptions()) {
            is BillingQueryResult.Ok -> {
                val premium = result.activeProductIds.any { it in LEGACY_SUBSCRIPTION_IDS }
                store.saveIsPremium(premium)
                _state.value = EntitlementState(billingAvailable = true, isPremium = premium, checked = true)
            }
            is BillingQueryResult.Unavailable ->
                _state.value = _state.value.copy(billingAvailable = false, checked = true)
        }
    }

    companion object {
        /** The three subscriptions that exist in Google Play. Do not add, rename or remove ids here without the Play Console. */
        val LEGACY_SUBSCRIPTION_IDS: Set<String> = setOf(
            "clearvoice_premium_weekly",
            "clearvoice_premium_monthly",
            "clearvoice_premium_yearly",
        )
    }
}
