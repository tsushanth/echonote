package com.kreativekoala.echonote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    secondary = Coral,
    onSecondary = OnPrimaryLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = OnSurfaceLight,
    background = BackgroundLight,
    error = Coral,
    onError = OnPrimaryLight
)

// The actual restyle target: fixed dark tokens chosen deliberately, not Material
// You dynamic color pulled from the device wallpaper.
private val DarkColorScheme = darkColorScheme(
    primary = Electric,
    onPrimary = Color.White,
    primaryContainer = ElectricDim,
    onPrimaryContainer = Electric,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = CoralDim,
    onSecondaryContainer = Coral,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = Lip,
    outlineVariant = TextTertiary,
    error = Coral,
    onError = Color.White,
    errorContainer = CoralDim,
    onErrorContainer = Coral
)

@Composable
fun EchoNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // This app has always been dark-first; dynamic color made every install read
    // its own device wallpaper's palette instead of a deliberate one — off by
    // default now. Still available for a future explicit "match my device" toggle.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
