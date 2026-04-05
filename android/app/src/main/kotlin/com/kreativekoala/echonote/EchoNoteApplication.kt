package com.kreativekoala.echonote

import android.app.Application
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.kreativekoala.echonote.service.AppOpenTracker
import com.kreativekoala.echonote.service.FirebaseAnalyticsHelper
import com.kreativekoala.echonote.service.TikTokHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EchoNoteApplication : Application() {

    @Inject lateinit var appOpenTracker: AppOpenTracker

    override fun onCreate() {
        super.onCreate()
        configureRevenueCat()
        FirebaseAnalyticsHelper.initialize(this)
        TikTokHelper.initialize(this)
        com.kreativekoala.paywallkit.manager.ExperimentManager.init(this)
        appOpenTracker.incrementOpenCount()
    }

    private fun configureRevenueCat() {
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(this, "goog_FfUybskvLQgXNrnpKkIaZZyQmvI")
                .build()
        )
    }
}
