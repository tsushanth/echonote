package com.kreativekoala.echonote.ui.theme

import androidx.compose.ui.graphics.Color

// Fixed identity, not Material You dynamic color — the app read the same generic
// wallpaper-driven blue every competitor's default screen does. Two deliberate
// accents, one job each: Coral = recording is live / destructive, Electric = the
// one premium CTA color.
val Ink = Color(0xFF0B0B0F)
val Surface = Color(0xFF17171D)
val SurfaceRaised = Color(0xFF1F1F27)
val Lip = Color(0xFF2A2A34)

val Coral = Color(0xFFFF4B3E)
val CoralDim = Color(0xFF4A1A16)
val Electric = Color(0xFF5B6EF5)
val ElectricDim = Color(0xFF1C2166)
val Gold = Color(0xFFFFCC00)

val TextPrimary = Color(0xFFF5F3F0)
val TextSecondary = Color(0xFF9A98A3)
val TextTertiary = Color(0xFF5C5A64)

// Kept for existing call sites; now points at the refined Coral so record/stop/
// destructive states share one color throughout instead of drifting between the
// old #FF3B30 and whatever else crept in.
val RecordingRed = Coral
val WaveformBlue = Electric
val WaveformGray = TextTertiary

// Light theme — kept for ThemeMode.LIGHT; the dark scheme below is the real
// restyle target since the app has always been dark-first.
val PrimaryLight = Electric
val OnPrimaryLight = Color.White
val SurfaceLight = Color(0xFFF2F2F7)
val OnSurfaceLight = Color(0xFF1C1C1E)
val BackgroundLight = Color.White

val PrimaryDark = Electric
val OnPrimaryDark = Color.White
val SurfaceDark = Surface
val OnSurfaceDark = TextPrimary
val BackgroundDark = Ink
