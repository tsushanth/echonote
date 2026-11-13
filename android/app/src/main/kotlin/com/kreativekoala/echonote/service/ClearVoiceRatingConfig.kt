package com.kreativekoala.echonote.service

import com.kreativekoala.ratingkit.RatingPolicy

/**
 * ClearVoice-only RatingKit gates. The portfolio default is 2 moments and 2 days since first open, but this app
 * loses many installs before day 2 (about 111 uninstalls against 161 installs a month), so it asks after the first
 * finished transcript from the day after install. Everything else stays at the shared defaults: at most 3 asks,
 * 60 then 120 days apart, quiet after an error, and an ask Google did not show does not use up an attempt.
 * To go back to the shared defaults, call RatingKit.init without a config.
 */
val CLEARVOICE_RATING_CONFIG = RatingPolicy.Config(
    minMoments = 1,
    minDaysSinceFirstOpen = 1,
)
