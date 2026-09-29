package com.whats.web.scan.webscan.pdfreaderpdffileedit.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The one subscription, with the two base plans configured in Play Console. */
object Products {
    const val PRO = "pdf_pro"
    const val PLAN_MONTHLY = "monthly"
    const val PLAN_YEARLY = "yearly"
}

data class PlanOffer(
    val basePlanId: String,
    val offerToken: String,
    val formattedPrice: String,
    val billingPeriod: String,
    /** ISO-8601 period of the free trial phase, or null when this user has no trial offer. */
    val trialPeriod: String?,
)

/**
 * FR-084. Connects on demand, caches the entitlement so an offline start stays Pro, and acknowledges
 * every purchase (Play refunds anything left unacknowledged for three days).
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val entitlement: Entitlement,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _offers = MutableStateFlow<List<PlanOffer>>(emptyList())
    val offers: StateFlow<List<PlanOffer>> = _offers.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { handlePurchases(purchases) }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private fun connect(onReady: () -> Unit) {
        if (client.isReady) return onReady()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Called from the paywall and on every resume so a refund or expiry drops Pro. */
    fun refresh() = connect {
        scope.launch {
            loadOffers()
            val result = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            )
            handlePurchases(result.purchasesList)
            if (result.purchasesList.none { it.isActivePro }) entitlement.update(false)
        }
    }

    private suspend fun loadOffers() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(Products.PRO)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull() ?: return
        productDetails = details
        _offers.value = details.subscriptionOfferDetails.orEmpty().map { offer ->
            val phases = offer.pricingPhases.pricingPhaseList
            val paid = phases.lastOrNull()
            val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
            PlanOffer(
                basePlanId = offer.basePlanId,
                offerToken = offer.offerToken,
                formattedPrice = paid?.formattedPrice.orEmpty(),
                billingPeriod = paid?.billingPeriod.orEmpty(),
                trialPeriod = trial?.billingPeriod,
            )
        }
    }

    fun purchase(activity: Activity, offer: PlanOffer) = connect {
        val details = productDetails ?: return@connect
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val active = purchases.filter { it.isActivePro }
        active.filter { !it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
        }
        if (active.isNotEmpty()) entitlement.update(true)
    }

    private val Purchase.isActivePro: Boolean
        get() = products.contains(Products.PRO) && purchaseState == Purchase.PurchaseState.PURCHASED
}
