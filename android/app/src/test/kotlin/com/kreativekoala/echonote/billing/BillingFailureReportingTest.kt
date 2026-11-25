package com.kreativekoala.echonote.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryPurchasesParams
import com.kreativekoala.echonote.diagnostics.FailureReporter
import com.kreativekoala.echonote.diagnostics.FakeTransport
import com.kreativekoala.echonote.diagnostics.ctx
import com.kreativekoala.echonote.diagnostics.testEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BillingFailureReportingTest {
    private val transport = FakeTransport()
    private val client = mockk<BillingClient>(relaxed = true)

    @Before
    fun setUp() {
        FailureReporter.replaceEngineForTest(testEngine(transport))
        mockkStatic(BillingClient::class)
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        val builder = mockk<BillingClient.Builder>()
        every { BillingClient.newBuilder(any()) } returns builder
        every { builder.setListener(any()) } returns builder
        every { builder.enablePendingPurchases(any()) } returns builder
        every { builder.build() } returns client
    }

    @After
    fun tearDown() {
        FailureReporter.replaceEngineForTest(null)
        unmockkAll()
    }

    private fun result(code: Int) = BillingResult.newBuilder().setResponseCode(code).build()

    private fun play(setup: Int, query: Int = BillingResponseCode.OK) {
        every { client.startConnection(any()) } answers {
            firstArg<BillingClientStateListener>().onBillingSetupFinished(result(setup))
        }
        every { client.queryPurchasesAsync(any<QueryPurchasesParams>(), any<PurchasesResponseListener>()) } answers {
            secondArg<PurchasesResponseListener>().onQueryPurchasesResponse(result(query), mutableListOf())
        }
    }

    private suspend fun ask() = PlayBillingGateway(mockk<Context>(relaxed = true)).queryActiveSubscriptions()

    @Test
    fun aPhoneWithoutPlayBillingIsNotAFailure() = runTest {
        play(setup = BillingResponseCode.BILLING_UNAVAILABLE)
        assertEquals(BillingQueryResult.Unavailable("setup_3"), ask())
        assertEquals(emptyList<String>(), transport.bodies)
    }

    @Test
    fun aSuccessfulCheckReportsNothing() = runTest {
        play(setup = BillingResponseCode.OK)
        assertEquals(BillingQueryResult.Ok(emptySet()), ask())
        assertEquals(emptyList<String>(), transport.bodies)
    }

    @Test
    fun aSetupErrorIsReported() = runTest {
        play(setup = BillingResponseCode.DEVELOPER_ERROR)
        assertEquals(BillingQueryResult.Unavailable("setup_5"), ask())
        val report = transport.reports.single()
        assertEquals("billing.entitlement", report.getString("flow"))
        assertEquals("setup_failed", report.getString("message"))
        assertEquals("5", report.ctx("code"))
    }

    @Test
    fun aQueryErrorIsReported() = runTest {
        play(setup = BillingResponseCode.OK, query = BillingResponseCode.ERROR)
        assertEquals(BillingQueryResult.Unavailable("query_6"), ask())
        assertEquals("query_failed", transport.reports.single().getString("message"))
    }

    @Test
    fun anExceptionIsReported() = runTest {
        every { client.startConnection(any()) } throws SecurityException("nope")
        assertEquals(BillingQueryResult.Unavailable("exception"), ask())
        assertEquals("exception | SecurityException", transport.reports.single().getString("message"))
    }

    @Test
    fun cancelsMissingBillingAndNetworkTroubleAreExpected() {
        listOf(
            BillingResponseCode.OK, BillingResponseCode.USER_CANCELED, BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.FEATURE_NOT_SUPPORTED, BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.NETWORK_ERROR
        ).forEach { assertFalse("code $it", BillingFailurePolicy.shouldReport(it)) }
        assertTrue(BillingFailurePolicy.shouldReport(BillingResponseCode.DEVELOPER_ERROR))
    }
}
