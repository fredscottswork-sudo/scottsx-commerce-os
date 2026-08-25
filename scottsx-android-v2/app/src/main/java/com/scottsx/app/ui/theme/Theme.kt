package com.scottsx.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.scottsx.app.data.preferences.themeState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import android.app.Activity

/**
 * ScottsTechX uses a contextual color scheme (the login / sign-up
 * screens are light-themed because their panels are white; the
 * onboarding and alt-login screens are dark/immersive). Select via
 * [LocalColorContext].
 */
enum class ColorContext {
    /** Cinematic screens — onboarding, alt-login. */
    Dark,
    /** Login / sign-up screen with white panel. */
    Light,
}

val LocalColorContext = compositionLocalOf { ColorContext.Dark }

private val DarkColors = darkColorScheme(
    primary = ScottsTechXColors.BluePrimary,
    onPrimary = ScottsTechXColors.OnDark,
    primaryContainer = ScottsTechXColors.BluePrimaryDark,
    onPrimaryContainer = ScottsTechXColors.OnDark,
    secondary = ScottsTechXColors.BluePrimaryLight,
    onSecondary = ScottsTechXColors.OnDark,
    background = ScottsTechXColors.BackgroundDark,
    onBackground = ScottsTechXColors.OnDark,
    surface = ScottsTechXColors.SurfacePanelDark,
    onSurface = ScottsTechXColors.OnDark,
    surfaceVariant = ScottsTechXColors.SurfaceElevatedDark,
    onSurfaceVariant = ScottsTechXColors.OnDarkSecondary,
    outline = ScottsTechXColors.BlueGlow,
)

private val LightColors = lightColorScheme(
    primary = ScottsTechXColors.BluePrimary,
    onPrimary = ScottsTechXColors.OnDark,
    primaryContainer = ScottsTechXColors.PanelInputLight,
    onPrimaryContainer = ScottsTechXColors.BluePrimaryDark,
    secondary = ScottsTechXColors.BluePrimaryLight,
    onSecondary = ScottsTechXColors.OnDark,
    background = ScottsTechXColors.PanelLight,
    onBackground = ScottsTechXColors.OnLight,
    surface = ScottsTechXColors.PanelLight,
    onSurface = ScottsTechXColors.OnLight,
    surfaceVariant = ScottsTechXColors.PanelInputLight,
    onSurfaceVariant = ScottsTechXColors.OnLightSecondary,
    outline = ScottsTechXColors.PanelBorderHint,
)

@Composable
fun ScottsTechXTheme(
    context: ColorContext = LocalColorContext.current,
    themeMode: com.scottsx.app.data.preferences.ThemeMode? = null,
    themePreference: com.scottsx.app.data.preferences.ThemePreference? = null,
    content: @Composable () -> Unit,
) {
    // Resolve the effective theme mode. If a [themePreference] is
    // provided (production: real SharedPreferences-backed singleton),
    // read its current value via Compose state so a tap on the theme
    // switcher re-renders the whole tree.
    val resolvedMode = if (themePreference != null) {
        val modeState by themePreference.themeState()
        themeMode ?: modeState
    } else {
        themeMode ?: com.scottsx.app.data.preferences.ThemeMode.SYSTEM
    }
    val isDark = when (resolvedMode) {
        com.scottsx.app.data.preferences.ThemeMode.DARK -> true
        com.scottsx.app.data.preferences.ThemeMode.LIGHT -> false
        com.scottsx.app.data.preferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // The contextual ColorContext still wins for the screen that sets
    // it explicitly (splash/onboarding/alt-login). Otherwise default to
    // light when the user picked LIGHT / SYSTEM-on-light, dark when the
    // user picked DARK or SYSTEM-on-dark.
    val resolvedContext = when {
        context == ColorContext.Dark -> ColorContext.Dark
        isDark -> ColorContext.Dark
        else -> ColorContext.Light
    }
    val colors = when (resolvedContext) {
        ColorContext.Dark -> DarkColors
        ColorContext.Light -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            // Status bar follows the effective theme, not the screen's
            // local ColorContext — the user-chosen theme always wins.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ScottsTechXTypography,
        shapes = ScottsTechXShapes,
        content = content,
    )
}