package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StudioColorScheme = darkColorScheme(
    primary = StudioCyan,
    onPrimary = StudioBgDark,
    primaryContainer = StudioSurfaceCardHover,
    onPrimaryContainer = StudioCyan,
    secondary = StudioPurpleLight,
    onSecondary = StudioBgDark,
    secondaryContainer = StudioSurfaceCard,
    onSecondaryContainer = StudioPurpleLight,
    tertiary = StudioAmber,
    onTertiary = StudioBgDark,
    background = StudioBgDark,
    onBackground = StudioTextPrimary,
    surface = StudioSurfaceDark,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceCard,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorder,
    error = StudioRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our customized high-contrast neon studio theme
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content
    )
}
