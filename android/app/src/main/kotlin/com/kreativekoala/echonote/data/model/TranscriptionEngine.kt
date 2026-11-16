package com.kreativekoala.echonote.data.model

import com.kreativekoala.echonote.R
import androidx.annotation.StringRes

/** Which speech recognizer turns a recording into text. */
enum class TranscriptionEngine(
    val displayName: String,
    val summary: String,
    @StringRes val displayRes: Int,
    @StringRes val summaryRes: Int
) {
    // displayName/summary stay as English fallbacks for logs; the UI shows the string resources.
    FAST("Fast", "Small download, lower accuracy.", R.string.engine_fast, R.string.engine_fast_summary),
    ACCURATE("Accurate", "A larger model chosen for your language. 60-490 MB download depending on the language, slower.", R.string.engine_accurate, R.string.engine_accurate_summary)
}
