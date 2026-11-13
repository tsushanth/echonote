package com.kreativekoala.echonote.service

import com.kreativekoala.ratingkit.RatingPolicy
import com.kreativekoala.ratingkit.RatingPolicy.Decision
import com.kreativekoala.ratingkit.RatingPolicy.Trigger
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearVoiceRatingConfigTest {
    private val config = CLEARVOICE_RATING_CONFIG
    private val day = TimeUnit.DAYS.toMillis(1)
    private val t0 = 1_000_000_000_000L

    @Test
    fun onlyTheMomentAndDayGatesAreLoosened() {
        val default = RatingPolicy.Config()
        assertEquals(1, config.minMoments)
        assertEquals(1, config.minDaysSinceFirstOpen)
        assertEquals(default.maxAttempts, config.maxAttempts)
        assertEquals(listOf(60, 120), config.gapsDays)
        assertEquals(default.errorQuietHours, config.errorQuietHours)
        assertEquals(default.retryAfterNotShownDays, config.retryAfterNotShownDays)
        assertEquals(default.shownMinMillis, config.shownMinMillis)
    }

    @Test
    fun asksAfterTheFirstMomentOnDayOneButNotOnDayZero() {
        val state = RatingPolicy.State(firstOpenAt = t0, moments = 1)
        assertEquals(Decision.Wait("too_early"), RatingPolicy.decide(state, t0 + day - 1, config, Trigger.MOMENT))
        assertEquals(Decision.Ask, RatingPolicy.decide(state, t0 + day, config, Trigger.MOMENT))
        // the shared defaults would still be waiting here
        assertTrue(RatingPolicy.decide(state, t0 + day, RatingPolicy.Config(), Trigger.MOMENT) is Decision.Wait)
    }

    @Test
    fun stillCapsAtThreeAsksWithSixtyAndOneTwentyDayGaps() {
        val first = RatingPolicy.State(firstOpenAt = t0, moments = 1, attempts = 1, lastAttemptAt = t0 + day)
        assertEquals(Decision.Wait("gap"), RatingPolicy.decide(first, t0 + day + 59 * day, config, Trigger.MOMENT))
        assertEquals(Decision.Ask, RatingPolicy.decide(first, t0 + day + 60 * day, config, Trigger.MOMENT))
        val second = first.copy(attempts = 2)
        assertEquals(Decision.Wait("gap"), RatingPolicy.decide(second, t0 + day + 119 * day, config, Trigger.MOMENT))
        assertEquals(Decision.Ask, RatingPolicy.decide(second, t0 + day + 120 * day, config, Trigger.MOMENT))
        val third = first.copy(attempts = 3)
        assertEquals(Decision.Wait("max_attempts"), RatingPolicy.decide(third, t0 + 1000 * day, config, Trigger.MOMENT))
    }

    @Test
    fun theApplicationPassesThisConfigToRatingKit() {
        val app = File("src/main/kotlin/com/kreativekoala/echonote/EchoNoteApplication.kt").readText()
        assertTrue(app.contains("RatingKit.init(this, appId = \"clearvoice\", config = CLEARVOICE_RATING_CONFIG)"))
    }
}
