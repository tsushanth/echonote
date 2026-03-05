package com.kreativekoala.echonote.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import javax.inject.Inject

class ReviewManager @Inject constructor(
    private val context: Context
) {
    private val reviewManager = ReviewManagerFactory.create(context)

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
