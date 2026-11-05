package com.kreativekoala.echonote.billing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGateway(var result: BillingQueryResult) : BillingGateway {
    var calls = 0
    override suspend fun queryActiveSubscriptions(): BillingQueryResult { calls++; return result }
}

private class FakeStore(var premium: Boolean = false) : EntitlementStore {
    override fun loadIsPremium() = premium
    override fun saveIsPremium(value: Boolean) { premium = value }
}

class EntitlementRepositoryTest {

    private fun repo(gateway: FakeGateway, store: FakeStore = FakeStore(), enabled: Boolean = true, override: EntitlementOverride? = null) =
        EntitlementRepository(gateway, store, enabled, override)

    @Test
    fun beforeAnyCheckNobodyIsPremiumAndBillingIsNotAvailable() {
        val state = repo(FakeGateway(BillingQueryResult.Ok(emptySet()))).state.value
        assertFalse(state.isPremium); assertFalse(state.billingAvailable); assertFalse(state.checked)
    }

    @Test
    fun eachOfTheThreeLegacySubscriptionsMakesTheUserPremium() = runTest {
        for (id in listOf("clearvoice_premium_weekly", "clearvoice_premium_monthly", "clearvoice_premium_yearly")) {
            val r = repo(FakeGateway(BillingQueryResult.Ok(setOf(id))))
            r.refresh()
            assertTrue(id, r.state.value.isPremium)
            assertTrue(id, r.state.value.billingAvailable)
        }
    }

    @Test
    fun anUnrelatedProductDoesNotMakeTheUserPremium() = runTest {
        val r = repo(FakeGateway(BillingQueryResult.Ok(setOf("some_other_app_sub"))))
        r.refresh()
        assertFalse(r.state.value.isPremium)
        assertTrue(r.state.value.billingAvailable)
    }

    @Test
    fun noPurchasesMeansNotPremiumButBillingIsAvailable() = runTest {
        val r = repo(FakeGateway(BillingQueryResult.Ok(emptySet())))
        r.refresh()
        assertFalse(r.state.value.isPremium)
        assertTrue(r.state.value.billingAvailable)
        assertTrue(r.state.value.checked)
    }

    @Test
    fun whenPlayCannotBeReachedBillingIsUnavailableSoPremiumRowsStayHidden() = runTest {
        val r = repo(FakeGateway(BillingQueryResult.Unavailable("setup_3")))
        r.refresh()
        assertFalse(r.state.value.billingAvailable)
        assertTrue(r.state.value.checked)
    }

    @Test
    fun aSubscriberIsNotDowngradedByAFailedCheckButBillingStaysUnavailable() = runTest {
        val gateway = FakeGateway(BillingQueryResult.Ok(setOf("clearvoice_premium_yearly")))
        val store = FakeStore()
        val r = repo(gateway, store)
        r.refresh()
        assertTrue(r.state.value.isPremium)
        gateway.result = BillingQueryResult.Unavailable("connect_timeout")
        r.refresh()
        assertTrue("last good answer is kept", r.state.value.isPremium)
        assertFalse("but premium UI is hidden", r.state.value.billingAvailable)
    }

    @Test
    fun theLastGoodAnswerSurvivesARestart() = runTest {
        val store = FakeStore()
        repo(FakeGateway(BillingQueryResult.Ok(setOf("clearvoice_premium_monthly"))), store).refresh()
        val restarted = repo(FakeGateway(BillingQueryResult.Unavailable("x")), store)
        assertTrue(restarted.state.value.isPremium)
    }

    @Test
    fun aCancelledSubscriptionStopsBeingPremiumOnTheNextGoodCheck() = runTest {
        val gateway = FakeGateway(BillingQueryResult.Ok(setOf("clearvoice_premium_monthly")))
        val r = repo(gateway)
        r.refresh()
        gateway.result = BillingQueryResult.Ok(emptySet())
        r.refresh()
        assertFalse(r.state.value.isPremium)
    }

    @Test
    fun withTheCheckSwitchedOffPlayIsNeverAskedAndBillingIsUnavailable() = runTest {
        val gateway = FakeGateway(BillingQueryResult.Ok(setOf("clearvoice_premium_monthly")))
        val r = repo(gateway, enabled = false)
        r.refresh()
        assertEquals(0, gateway.calls)
        assertFalse(r.state.value.isPremium)
        assertFalse(r.state.value.billingAvailable)
    }

    @Test
    fun theDebugOverrideWinsOverTheRealAnswer() = runTest {
        val gateway = FakeGateway(BillingQueryResult.Unavailable("setup_3"))
        val r = repo(gateway, override = object : EntitlementOverride {
            override fun forced() = EntitlementState(billingAvailable = true, isPremium = false, checked = true)
        })
        r.refresh()
        assertEquals(0, gateway.calls)
        assertTrue(r.state.value.billingAvailable)
    }

    @Test
    fun theLegacyIdsAreExactlyTheThreeThatExistInPlay() {
        assertEquals(
            setOf("clearvoice_premium_weekly", "clearvoice_premium_monthly", "clearvoice_premium_yearly"),
            EntitlementRepository.LEGACY_SUBSCRIPTION_IDS
        )
    }
}
