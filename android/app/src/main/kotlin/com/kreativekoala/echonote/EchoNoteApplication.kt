package com.kreativekoala.echonote

import android.app.Application
import com.kreativekoala.echonote.service.AppOpenTracker
import com.kreativekoala.echonote.service.FacebookSDKHelper
import com.kreativekoala.echonote.service.FirebaseAnalyticsHelper
import com.kreativekoala.echonote.service.TikTokHelper
import com.kreativekoala.ratingkit.RatingKit
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EchoNoteApplication : Application() {

    @Inject lateinit var appOpenTracker: AppOpenTracker

    override fun onCreate() {
        super.onCreate()
        FirebaseAnalyticsHelper.initialize(this)
        TikTokHelper.initialize(this)
        FacebookSDKHelper.initialize(this)
        com.kreativekoala.paywallkit.manager.ExperimentManager.init(this)
        com.kreativekoala.paywallkit.manager.PromoCodeManager.init(this)
        RatingKit.init(this, appId = "clearvoice")
        appOpenTracker.incrementOpenCount()
    }
}
