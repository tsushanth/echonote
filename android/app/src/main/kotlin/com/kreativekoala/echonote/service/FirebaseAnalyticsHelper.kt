package com.kreativekoala.echonote.service

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object FirebaseAnalyticsHelper {
    private const val TAG = "FirebaseAnalytics"

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var isInitialized = false

    fun initialize(context: Context) {
        try {
            firebaseAnalytics = Firebase.analytics
            isInitialized = true
            Log.d(TAG, "Firebase Analytics initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics: ${e.message}")
        }
    }

    fun setUserId(userId: String?) {
        firebaseAnalytics?.setUserId(userId)
    }

    fun setUserProperty(name: String, value: String?) {
        firebaseAnalytics?.setUserProperty(name, value)
    }

    // Standard Events

    fun logAppOpen() {
        logEvent(FirebaseAnalytics.Event.APP_OPEN)
    }

    fun logScreenView(screenName: String, screenClass: String? = null) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            screenClass?.let { putString(FirebaseAnalytics.Param.SCREEN_CLASS, it) }
        }
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
    }

    // Subscription Events

    fun logPaywallViewed(source: String? = null) {
        val params = Bundle().apply {
            source?.let { putString("source", it) }
        }
        logEvent("paywall_viewed", params)
    }

    fun logSubscriptionStarted(productId: String, isTrial: Boolean = false) {
        val params = Bundle().apply {
            putString("product_id", productId)
            putBoolean("is_trial", isTrial)
        }
        logEvent("subscription_started", params)
    }

    fun logPurchaseCompleted(productId: String, revenue: Double? = null) {
        val params = Bundle().apply {
            putString("product_id", productId)
            revenue?.let { putDouble(FirebaseAnalytics.Param.VALUE, it) }
            putString(FirebaseAnalytics.Param.CURRENCY, "USD")
        }
        logEvent(FirebaseAnalytics.Event.PURCHASE, params)
    }

    // Recording Events

    fun logRecordingStarted(format: String? = null) {
        val params = Bundle().apply {
            format?.let { putString("format", it) } // m4a, wav
        }
        logEvent("recording_started", params)
    }

    fun logRecordingSaved(durationSeconds: Long? = null, format: String? = null) {
        val params = Bundle().apply {
            durationSeconds?.let { putLong("duration_seconds", it) }
            format?.let { putString("format", it) }
        }
        logEvent("recording_saved", params)
    }

    fun logRecordingDeleted() {
        logEvent("recording_deleted")
    }

    // Playback Events

    fun logPlaybackStarted() {
        logEvent("playback_started")
    }

    fun logSpeedChanged(speed: Float) {
        val params = Bundle().apply {
            putFloat("speed", speed)
        }
        logEvent("speed_changed", params)
    }

    // Transcription Events

    fun logTranscriptionStarted() {
        logEvent("transcription_started")
    }

    fun logTranscriptionCompleted() {
        logEvent("transcription_completed")
    }

    fun logTranscriptionFailed(error: String? = null) {
        val params = Bundle().apply {
            error?.let { putString("error", it) }
        }
        logEvent("transcription_failed", params)
    }

    // Feature Usage Events

    fun logTrimApplied() {
        logEvent("trim_applied")
    }

    fun logRecordingShared() {
        logEvent("recording_shared")
    }

    fun logFolderCreated() {
        logEvent("folder_created")
    }

    fun logFolderLimitReached() {
        logEvent("folder_limit_reached")
    }

    fun logQuotaReached(feature: String) {
        val params = Bundle().apply {
            putString("feature", feature)
        }
        logEvent("quota_reached", params)
    }

    // Core Logging

    private fun logEvent(eventName: String, params: Bundle? = null) {
        if (!isInitialized) {
            Log.w(TAG, "Firebase Analytics not initialized, skipping event: $eventName")
            return
        }
        try {
            firebaseAnalytics?.logEvent(eventName, params)
            Log.d(TAG, "Logged event: $eventName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event $eventName: ${e.message}")
        }
    }
}
