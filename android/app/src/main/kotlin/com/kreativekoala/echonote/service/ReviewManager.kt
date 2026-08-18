package com.kreativekoala.echonote.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import javax.inject.Inject

private const val PREFS_NAME = "review_prefs"
private const val KEY_TRANSCRIPTION_COUNT = "transcription_count"
private const val KEY_REVIEW_PROMPTED = "review_prompted"
private const val PROMPT_AFTER_TRANSCRIPTIONS = 3

class ReviewManager @Inject constructor(
    private val context: Context
) {
    private val reviewManager = ReviewManagerFactory.create(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Increments the transcription count and returns true if the review prompt should be shown.
     * Returns false if already prompted or threshold not yet reached.
     */
    fun shouldPromptReview(): Boolean {
        if (prefs.getBoolean(KEY_REVIEW_PROMPTED, false)) return false

        val count = prefs.getInt(KEY_TRANSCRIPTION_COUNT, 0) + 1
        prefs.edit().putInt(KEY_TRANSCRIPTION_COUNT, count).apply()

        return if (count >= PROMPT_AFTER_TRANSCRIPTIONS) {
            prefs.edit().putBoolean(KEY_REVIEW_PROMPTED, true).apply()
            true
        } else {
            false
        }
    }

    fun requestReview(activity: Activity) {
        val requestFlow = reviewManager.requestReviewFlow()
        requestFlow.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val launchFlow = reviewManager.launchReviewFlow(activity, reviewInfo)
                launchFlow.addOnCompleteListener {
                    Log.d("ReviewManager", "Review flow completed")
                }
            } else {
                Log.w("ReviewManager", "Failed to request review flow", task.exception)
            }
        }
    }
}
