package com.kreativekoala.echonote

import android.app.Application
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.kreativekoala.echonote.service.FirebaseAnalyticsHelper
import com.kreativekoala.echonote.service.TikTokHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EchoNoteApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        configureRevenueCat()
        FirebaseAnalyticsHelper.initialize(this)
        TikTokHelper.initialize(this)
    }

    private fun configureRevenueCat() {
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(this, "goog_FfUybskvLQgXNrnpKkIaZZyQmvI")
                .build()
        )
    }
}
