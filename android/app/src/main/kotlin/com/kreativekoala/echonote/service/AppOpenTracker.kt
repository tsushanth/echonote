package com.kreativekoala.echonote.service

import android.content.Context
import com.kreativekoala.echonote.util.Constants
import javax.inject.Inject

/**
 * Tracks the number of times the app has been opened (cold starts only).
 * After [Constants.Premium.FREE_OPEN_LIMIT] opens, non-premium users
 * are shown a hard paywall.
 */
class AppOpenTracker @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "app_open_tracker"
        private const val KEY_OPEN_COUNT = "open_count"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Increments the open count. Should be called once per app session (cold start).
     */
    fun incrementOpenCount() {
        val current = prefs.getInt(KEY_OPEN_COUNT, 0)
        prefs.edit().putInt(KEY_OPEN_COUNT, current + 1).apply()
    }

    /**
     * Returns the current open count.
     */
    fun getOpenCount(): Int {
        return prefs.getInt(KEY_OPEN_COUNT, 0)
    }

    /**
     * Returns true if the user has exceeded the free open limit.
     */
    fun hasExceededFreeLimit(): Boolean {
        return getOpenCount() > Constants.Premium.FREE_OPEN_LIMIT
    }
}
