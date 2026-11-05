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
                ?: return BillingQueryResult.Unavailable("connect_timeout")
            if (setup.responseCode != BillingClient.BillingResponseCode.OK) {
                return BillingQueryResult.Unavailable("setup_${setup.responseCode}")
            }
            val result = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
                )
            } ?: return BillingQueryResult.Unavailable("query_timeout")
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return BillingQueryResult.Unavailable("query_${result.billingResult.responseCode}")
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
            return BillingQueryResult.Unavailable("exception")
        } finally {
            try { client.endConnection() } catch (_: Exception) { }
        }
    }

    private companion object {
        const val TAG = "PlayBillingGateway"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val QUERY_TIMEOUT_MS = 10_000L
    }
}
