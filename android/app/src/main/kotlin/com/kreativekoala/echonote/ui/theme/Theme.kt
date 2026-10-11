package com.kreativekoala.echonote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealWash,
    onPrimaryContainer = SpruceInk,
    secondary = CoralLight,
    onSecondary = Color.White,
    secondaryContainer = CoralWashLight,
    onSecondaryContainer = CoralLight,
    background = Fog,
    onBackground = SpruceInk,
    surface = Page,
    onSurface = SpruceInk,
    surfaceVariant = PageSunk,
    onSurfaceVariant = SpruceMuted,
    outline = SpruceFaint,
    outlineVariant = PageSunk,
    error = CoralLight,
    onError = Color.White,
    errorContainer = CoralWashLight,
    onErrorContainer = CoralLight
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = SpruceDeep,
    primaryContainer = TealDeep,
    onPrimaryContainer = TealLight,
    secondary = CoralDark,
    onSecondary = Color.White,
    secondaryContainer = CoralWashDark,
    onSecondaryContainer = CoralDark,
    background = SpruceDeep,
    onBackground = ChalkInk,
    surface = PageDark,
    onSurface = ChalkInk,
    surfaceVariant = PageRaisedDark,
    onSurfaceVariant = ChalkMuted,
    outline = ChalkFaint,
    outlineVariant = PageRaisedDark,
    error = CoralDark,
    onError = Color.White,
    errorContainer = CoralWashDark,
    onErrorContainer = CoralDark
)

object EchoTheme {
    val colors: EchoColors
        @Composable get() = LocalEchoColors.current
}

@Composable
fun EchoNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalEchoColors provides if (darkTheme) DarkEchoColors else LightEchoColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
