package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.billing.EntitlementRepository
import com.kreativekoala.echonote.billing.EntitlementState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Entitlement check. [isPremium] reflects the real Google Play answer for the three legacy subscriptions, but it
 * gates nothing that exists today: every existing feature stays free and unlocked for everyone, and the `can*`
 * checks below are kept as the single place to ask "may the user do X?" so call sites stay unchanged.
 *
 * The only thing premium will ever unlock is a feature that needs our servers (written answers about recordings,
 * cloud live transcript, server voices). None exists in this release.
 */
class PremiumManager @Inject constructor(private val entitlements: EntitlementRepository) {

    val state: StateFlow<EntitlementState> get() = entitlements.state

    /** True when Google Play reports an active legacy subscription. Never used to limit a free feature. */
    val isPremium: Boolean get() = entitlements.state.value.isPremium

    /** False when Google Play cannot be reached or the check is off; premium rows must be hidden then. */
    val isBillingAvailable: Boolean get() = entitlements.state.value.billingAvailable

    suspend fun refresh() = entitlements.refresh()

    @Suppress("UNUSED_PARAMETER")
    fun canCreateRecording(currentCount: Int): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    fun canCreateFolder(currentCount: Int): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    fun canCreateBookmark(currentCount: Int): Boolean = true

    fun canUseTranscription(): Boolean = true
}
