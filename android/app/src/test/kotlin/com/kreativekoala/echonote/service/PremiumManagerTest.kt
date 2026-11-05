package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.billing.BillingGateway
import com.kreativekoala.echonote.billing.BillingQueryResult
import com.kreativekoala.echonote.billing.EntitlementRepository
import com.kreativekoala.echonote.billing.EntitlementStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumManagerTest {

    private class Gateway(var result: BillingQueryResult) : BillingGateway {
        override suspend fun queryActiveSubscriptions() = result
    }

    private class Store : EntitlementStore {
        var v = false
        override fun loadIsPremium() = v
        override fun saveIsPremium(value: Boolean) { v = value }
    }

    private fun manager(result: BillingQueryResult) =
        PremiumManager(EntitlementRepository(Gateway(result), Store(), checkEnabled = true))

    private val free = manager(BillingQueryResult.Ok(emptySet()))
    private val subscriber = manager(BillingQueryResult.Ok(setOf("clearvoice_premium_yearly")))

    @Test
    fun nothingThatExistsTodayIsEverLimitedForAnyone() = runTest {
        subscriber.refresh()
        for (m in listOf(free, subscriber, manager(BillingQueryResult.Unavailable("x")))) {
            listOf(0, 3, 4, 100, Int.MAX_VALUE).forEach { assertTrue(m.canCreateRecording(it)) }
            listOf(0, 1, 2, 100, Int.MAX_VALUE).forEach { assertTrue(m.canCreateFolder(it)) }
            listOf(0, 2, 3, 100, Int.MAX_VALUE).forEach { assertTrue(m.canCreateBookmark(it)) }
            assertTrue(m.canUseTranscription())
        }
    }

    @Test
    fun isPremiumReportsTheRealEntitlementNotAConstant() = runTest {
        free.refresh(); subscriber.refresh()
        assertFalse(free.isPremium)
        assertTrue(subscriber.isPremium)
        assertTrue(subscriber.isBillingAvailable)
    }

    @Test
    fun billingIsReportedUnavailableWhenPlayCannotBeReached() = runTest {
        val m = manager(BillingQueryResult.Unavailable("setup_3"))
        m.refresh()
        assertFalse(m.isBillingAvailable)
        assertEquals(false, m.isPremium)
    }
}
