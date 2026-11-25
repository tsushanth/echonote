package com.kreativekoala.echonote.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.kreativekoala.echonote.diagnostics.FailureReporter
import com.kreativekoala.echonote.diagnostics.ReportFlow
import com.kreativekoala.echonote.diagnostics.ReportReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real Play Billing client, used read-only: connect, list existing subscriptions, disconnect.
 * It never launches a purchase flow, never acknowledges anything and sends nothing to our own servers
 * (Play Billing talks to Google Play, not to us).
 */
class PlayBillingGateway(private val context: Context) : BillingGateway {

    override suspend fun queryActiveSubscriptions(): BillingQueryResult {
        val client = BillingClient.newBuilder(context.applicationContext)
            // Play requires a listener and the pending-purchases declaration; neither is used: no purchase flow exists.
            .setListener { _, _ -> }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        try {
            val connected = CompletableDeferred<BillingResult>()
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connected.complete(result)
                }

                override fun onBillingServiceDisconnected() {
                    connected.complete(
                        BillingResult.newBuilder()
                            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                            .build()
                    )
                }
            })
            val setup = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() }
                ?: return unavailable("connect_timeout", report = ReportReason.CONNECT_TIMEOUT)
            if (setup.responseCode != BillingClient.BillingResponseCode.OK) {
                return unavailable(
                    "setup_${setup.responseCode}",
                    report = ReportReason.SETUP_FAILED.takeIf { BillingFailurePolicy.shouldReport(setup.responseCode) },
                    code = setup.responseCode
                )
            }
            val result = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
                )
            } ?: return unavailable("query_timeout", report = ReportReason.QUERY_TIMEOUT)
            val queryCode = result.billingResult.responseCode
            if (queryCode != BillingClient.BillingResponseCode.OK) {
                return unavailable(
                    "query_$queryCode",
                    report = ReportReason.QUERY_FAILED.takeIf { BillingFailurePolicy.shouldReport(queryCode) },
                    code = queryCode
                )
            }
            val active = result.purchasesList
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap { it.products }
                .toSet()
            return BillingQueryResult.Ok(active)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "billing query failed", e)
            return unavailable("exception", report = ReportReason.EXCEPTION, error = e)
        } finally {
            try { client.endConnection() } catch (_: Exception) { }
        }
    }

    /** The answer for the caller, plus a failure report when [report] is set (null = an expected condition). */
    private fun unavailable(
        reason: String,
        report: ReportReason?,
        code: Int? = null,
        error: Throwable? = null
    ): BillingQueryResult.Unavailable {
        if (report != null) {
            val context = if (code != null) mapOf("code" to code.toString()) else emptyMap()
            FailureReporter.failure(ReportFlow.BILLING_ENTITLEMENT, error, report, context)
        }
        return BillingQueryResult.Unavailable(reason)
    }

    private companion object {
        const val TAG = "PlayBillingGateway"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val QUERY_TIMEOUT_MS = 10_000L
    }
}
