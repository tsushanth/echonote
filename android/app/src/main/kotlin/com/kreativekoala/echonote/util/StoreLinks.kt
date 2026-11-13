package com.kreativekoala.echonote.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Plain links for the Settings "Rate ClearVoice" and "Send feedback" rows. These are user-initiated and
 * separate from the automatic prompt, which is owned by RatingKit alone.
 */
object StoreLinks {
    /** The release listing; the debug build has a different applicationId and no listing. */
    const val PLAY_PACKAGE = "com.kreativekoala.echonote"
    const val SUPPORT_EMAIL = "support@kreativekoala.com"

    fun marketUrl(pkg: String = PLAY_PACKAGE) = "market://details?id=$pkg"
    fun webUrl(pkg: String = PLAY_PACKAGE) = "https://play.google.com/store/apps/details?id=$pkg"
    fun feedbackUrl() = "mailto:$SUPPORT_EMAIL?subject=ClearVoice%20Feedback"

    /** Candidate intents in the order they should be tried. */
    fun rateUrls(pkg: String = PLAY_PACKAGE) = listOf(marketUrl(pkg), webUrl(pkg))

    fun openRateListing(context: Context): Boolean = openFirst(context, rateUrls().map { Intent(Intent.ACTION_VIEW, Uri.parse(it)) })

    fun openFeedback(context: Context): Boolean =
        openFirst(context, listOf(Intent(Intent.ACTION_SENDTO, Uri.parse(feedbackUrl()))))

    private fun openFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { /* no handler for this scheme, try the next */ }
        }
        return false
    }
}
