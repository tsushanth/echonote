package com.kreativekoala.echonote.service

import javax.inject.Inject

/**
 * Entitlement check. The app is completely free: every feature is unlocked for every
 * user (including anyone who previously subscribed), so nothing here depends on
 * billing state. Kept as the single place to ask "may the user do X?" so call sites
 * stay unchanged.
 */
class PremiumManager @Inject constructor() {

    @Suppress("UNUSED_PARAMETER")
    fun canCreateRecording(currentCount: Int): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    fun canCreateFolder(currentCount: Int): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    fun canCreateBookmark(currentCount: Int): Boolean = true

    fun canUseTranscription(): Boolean = true
}
