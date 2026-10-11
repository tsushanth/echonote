package com.kreativekoala.echonote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kreativekoala.echonote.R

// Variable fonts (minSdk 26 supports variation settings). Figtree carries the UI;
// Source Serif 4 carries anything meant to be read: titles and the transcript.
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Figtree = FontFamily(
    variable(R.font.figtree_variable, 400),
    variable(R.font.figtree_variable, 500),
    variable(R.font.figtree_variable, 600),
    variable(R.font.figtree_variable, 700)
)

val SourceSerif = FontFamily(
    variable(R.font.source_serif4_variable, 400),
    variable(R.font.source_serif4_variable, 500),
    variable(R.font.source_serif4_variable, 600),
    variable(R.font.source_serif4_variable, 700)
)

private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Figtree),
    displayMedium = base.displayMedium.copy(fontFamily = Figtree),
    displaySmall = base.displaySmall.copy(fontFamily = Figtree),
    headlineLarge = TextStyle(
        fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.1).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp
    ),
    titleMedium = base.titleMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = base.bodySmall.copy(fontFamily = Figtree),
    labelLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp
    ),
    labelMedium = base.labelMedium.copy(fontFamily = Figtree),
    labelSmall = base.labelSmall.copy(fontFamily = Figtree)
)

/** Transcript reading text: serif gets extra leading, and the line stays under ~60 characters on phones. */
val TranscriptTextStyle = TextStyle(
    fontFamily = SourceSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 29.sp
)

/** Timers and timestamps: tabular figures so digits don't jitter while counting. */
val TimerTextStyle = TextStyle(
    fontFamily = Figtree,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum"
)
