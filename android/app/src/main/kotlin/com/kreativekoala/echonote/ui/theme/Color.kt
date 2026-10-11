package com.kreativekoala.echonote.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Palette is built around the thing the app produces: a transcript you read like a
// page. Cool "fog" grounds and spruce ink instead of a neutral black; a highlighter
// yellow marks the word being spoken, the way you'd mark a printed page. Coral is
// reserved for "recording is live" and destructive actions only.

// Light: fog ground, page surfaces, spruce ink
val Fog = Color(0xFFE6ECEC)
val Page = Color(0xFFF7F9F8)
val PageSunk = Color(0xFFDCE4E4)
val SpruceInk = Color(0xFF10262B)
val SpruceMuted = Color(0xFF51666B)
val SpruceFaint = Color(0xFF8A9C9F)
val Teal = Color(0xFF1F6F78)
val TealWash = Color(0xFFCFE4E6)

// Dark: deep spruce ground
val SpruceDeep = Color(0xFF0C1A1D)
val PageDark = Color(0xFF14282C)
val PageRaisedDark = Color(0xFF1C3338)
val ChalkInk = Color(0xFFEAF1F0)
val ChalkMuted = Color(0xFFA2B6B8)
val ChalkFaint = Color(0xFF647B7E)
val TealLight = Color(0xFF7CC8CF)
val TealDeep = Color(0xFF173F45)

val CoralLight = Color(0xFFD93C2F)
val CoralDark = Color(0xFFFF5A4D)
val CoralWashLight = Color(0xFFF7D6D2)
val CoralWashDark = Color(0xFF4A1A16)

val HighlighterLight = Color(0xFFF2C94C)
val HighlighterDark = Color(0xFFE0B83A)

/** Speaker identity colors, muted enough to sit behind serif text in either theme. */
val SpeakerColorsLight = listOf(
    Color(0xFF1F6F78), Color(0xFFB4532A), Color(0xFF5B5BA6),
    Color(0xFF7A8F2B), Color(0xFFA33B6B), Color(0xFF2F7DB5)
)
val SpeakerColorsDark = listOf(
    Color(0xFF7CC8CF), Color(0xFFE59A72), Color(0xFFA9A9E8),
    Color(0xFFC3D470), Color(0xFFE69AC0), Color(0xFF8CC3EC)
)

/** Colors the Material scheme has no slot for. Read with [EchoTheme.colors]. */
@Immutable
data class EchoColors(
    val record: Color,
    val recordWash: Color,
    val highlighter: Color,
    val onHighlighter: Color,
    val faint: Color,
    val sunk: Color,
    val speakers: List<Color>
)

val LightEchoColors = EchoColors(
    record = CoralLight,
    recordWash = CoralWashLight,
    highlighter = HighlighterLight,
    onHighlighter = SpruceInk,
    faint = SpruceFaint,
    sunk = PageSunk,
    speakers = SpeakerColorsLight
)

val DarkEchoColors = EchoColors(
    record = CoralDark,
    recordWash = CoralWashDark,
    highlighter = HighlighterDark,
    onHighlighter = SpruceDeep,
    faint = ChalkFaint,
    sunk = SpruceDeep,
    speakers = SpeakerColorsDark
)

val LocalEchoColors = staticCompositionLocalOf { LightEchoColors }
