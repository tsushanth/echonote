package com.kreativekoala.echonote.ui

import com.kreativekoala.echonote.util.StoreLinks
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RatingKit is the only automatic rating prompt; Settings carries a plain "Rate" row and a feedback row. */
class SingleRatingSystemTest {
    private val main = File("src/main")

    @Test
    fun legacyReviewCardStringsAreGoneFromEveryLocale() {
        val dirs = File(main, "res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        for (dir in dirs) {
            val text = File(dir, "strings.xml").takeIf { it.exists() }?.readText() ?: continue
            assertFalse("${dir.name} still has review_prompt strings", text.contains("review_prompt_"))
        }
    }

    @Test
    fun noKotlinSourceReferencesTheLegacyReviewCard() {
        val offenders = File(main, "kotlin").walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readText().let { "showReviewCard" in it || "dismissReviewCard" in it || "ReviewPromptCard" in it || "R.string.review_prompt" in it || "review_prompt_shown" in it } }
            .map { it.name }.toList()
        assertTrue("legacy review card still referenced in $offenders", offenders.isEmpty())
    }

    @Test
    fun ratingKitIsInitialisedExactlyOnce() {
        val inits = File(main, "kotlin").walkTopDown().filter { it.extension == "kt" }
            .sumOf { Regex("RatingKit\\.init\\(").findAll(it.readText()).count() }
        assertEquals(1, inits)
    }

    @Test
    fun settingsHasRateAndFeedbackRowsWithEnglishStrings() {
        val strings = File(main, "res/values/strings.xml").readText()
        assertTrue(strings.contains("name=\"settings_rate_app\">Rate ClearVoice<"))
        assertTrue(strings.contains("name=\"settings_send_feedback\">Send feedback<"))
        val settings = File(main, "kotlin/com/kreativekoala/echonote/ui/settings/SettingsScreen.kt").readText()
        assertTrue(settings.contains("StoreLinks.openRateListing"))
        assertTrue(settings.contains("StoreLinks.openFeedback"))
    }

    @Test
    fun rateTriesPlayStoreAppThenWebListingForTheReleasePackage() {
        assertEquals(
            listOf(
                "market://details?id=com.kreativekoala.echonote",
                "https://play.google.com/store/apps/details?id=com.kreativekoala.echonote",
            ),
            StoreLinks.rateUrls(),
        )
        assertEquals("com.kreativekoala.echonote", StoreLinks.PLAY_PACKAGE)
    }

    @Test
    fun feedbackIsAMailtoToSupport() {
        assertEquals("mailto:support@kreativekoala.com?subject=ClearVoice%20Feedback", StoreLinks.feedbackUrl())
    }
}
