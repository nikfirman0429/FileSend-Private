package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurpleLight,
    onPrimary = PrimaryPurpleDark,
    primaryContainer = PrimaryPurpleDark,
    onPrimaryContainer = PrimaryPurpleLight,
    secondary = SecondaryLilacLight,
    onSecondary = OnSecondaryLilacContainer,
    secondaryContainer = SecondaryLilac,
    onSecondaryContainer = SecondaryLilacContainer,
    tertiary = TertiaryRoseContainer,
    onTertiary = OnTertiaryRoseContainer,
    background = SleekDarkBackground,
    onBackground = SleekDarkTextPrimary,
    surface = SleekDarkSurface,
    onSurface = SleekDarkTextPrimary,
    surfaceVariant = SleekDarkSurfaceVariant,
    onSurfaceVariant = SleekDarkTextSecondary,
    outline = SleekDarkOutline,
    outlineVariant = SleekDarkOutlineVariant,
    error = SleekRedLight
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = SleekLightSurface,
    primaryContainer = PrimaryPurpleContainer,
    onPrimaryContainer = OnPrimaryPurpleContainer,
    secondary = SecondaryLilac,
    onSecondary = SleekLightSurface,
    secondaryContainer = SecondaryLilacContainer,
    onSecondaryContainer = OnSecondaryLilacContainer,
    tertiary = TertiaryRose,
    onTertiary = SleekLightSurface,
    tertiaryContainer = TertiaryRoseContainer,
    onTertiaryContainer = OnTertiaryRoseContainer,
    background = SleekLightBackground,
    onBackground = SleekLightTextPrimary,
    surface = SleekLightSurface,
    onSurface = SleekLightTextPrimary,
    surfaceVariant = SleekLightSurfaceVariant,
    onSurfaceVariant = SleekLightTextSecondary,
    outline = SleekLightOutline,
    outlineVariant = SleekLightOutlineVariant,
    error = SleekRed
)

@Composable
fun FileSendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surfaceVariant.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
