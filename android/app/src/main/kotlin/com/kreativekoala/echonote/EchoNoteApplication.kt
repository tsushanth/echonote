package com.kreativekoala.echonote

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kreativekoala.echonote.service.AppOpenTracker
import com.kreativekoala.echonote.service.CLEARVOICE_RATING_CONFIG
import com.kreativekoala.echonote.service.FacebookSDKHelper
import com.kreativekoala.echonote.service.FirebaseAnalyticsHelper
import com.kreativekoala.echonote.service.Funnel
import com.kreativekoala.echonote.service.TikTokHelper
import com.kreativekoala.ratingkit.RatingKit
import dagger.hilt.android.HiltAndroidApp
import com.kreativekoala.echonote.billing.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EchoNoteApplication : Application(), Configuration.Provider {

    @Inject lateinit var appOpenTracker: AppOpenTracker
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var entitlements: EntitlementRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FirebaseAnalyticsHelper.initialize(this)
        Funnel.init(this)
        TikTokHelper.initialize(this)
        FacebookSDKHelper.initialize(this)
        RatingKit.init(this, appId = "clearvoice", config = CLEARVOICE_RATING_CONFIG)
        appOpenTracker.incrementOpenCount()
        // Restore on launch: ask Google Play which legacy subscriptions are active. Read-only; gates nothing.
        appScope.launch { entitlements.refresh() }
    }
}
