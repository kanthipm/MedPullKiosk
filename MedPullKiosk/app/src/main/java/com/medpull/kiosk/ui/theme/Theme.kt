package com.medpull.kiosk.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MedPullBlue,
    onPrimary = TextOnPrimary,
    primaryContainer = MedPullBlueDark,
    onPrimaryContainer = TextOnPrimary,
    secondary = MedPullTeal,
    onSecondary = TextOnPrimary,
    secondaryContainer = MedPullTealDark,
    onSecondaryContainer = TextOnPrimary,
    background = BackgroundDark,
    onBackground = TextOnPrimary,
    surface = SurfaceDark,
    onSurface = TextOnPrimary,
    error = Error,
    onError = TextOnPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = MedPullBlue,
    onPrimary = TextOnPrimary,
    primaryContainer = MedPullBlueLight,
    onPrimaryContainer = TextPrimary,
    secondary = MedPullTeal,
    onSecondary = TextOnPrimary,
    secondaryContainer = MedPullTealLight,
    onSecondaryContainer = TextPrimary,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    error = Error,
    onError = TextOnPrimary
)

@Composable
fun MedPullKioskTheme(
    darkTheme: Boolean = false, // Kiosk always uses light theme for readability
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
