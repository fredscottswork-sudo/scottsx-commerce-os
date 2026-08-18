package com.scottsx.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.scottsx.app.UserPrefs

/**
 * ScottsTechX brand palette.
 *
 * These values are a 1:1 mirror of the CSS custom properties in
 * `web/src/styles/globals.css`. The app and the website must look identical,
 * so any change here has to be made in both places.
 *
 *   dark  --bg #05070d  --surface #0b1020  --surface-2 #121a2f
 *         --border #1e2a45  --text #eef2fb  --primary #2b7cff
 *   light --bg #f5f7fc  --surface #ffffff  --text #08122a  --primary #1447c4
 */
object ScottsTechXColors {

    // ── Brand blues ─────────────────────────────────────────────────────────
    val BluePrimary = Color(0xFF2B7CFF)        // web --primary (dark theme)
    val BluePrimaryLight = Color(0xFF5B9BFF)
    val BluePrimaryDark = Color(0xFF1447C4)    // web --primary (light theme)
    val BlueDeep = Color(0xFF0D2F7A)           // gradient start

    // ── Dark theme surfaces (primary experience) ────────────────────────────
    val DarkBackground = Color(0xFF05070D)     // near-black
    val DarkPanel = Color(0xFF0B1020)          // --surface
    val DarkPanelRaised = Color(0xFF121A2F)    // --surface-2
    val DarkPanelHover = Color(0xFF16203A)     // --surface-hover
    val DarkBorder = Color(0xFF1E2A45)         // --border
    val DarkOn = Color(0xFFEEF2FB)             // --text
    val DarkOnSecondary = Color(0xFFA9B6D4)    // --text-2
    val DarkOnTertiary = Color(0xFF6F7FA0)     // --text-3

    // ── Light theme surfaces (black -> white switch) ────────────────────────
    val Background = Color(0xFFF5F7FC)         // --bg
    val PanelLight = Color(0xFFFFFFFF)         // --surface
    val PanelInputLight = Color(0xFFEEF3FB)    // --surface-2 / hover
    val OnLight = Color(0xFF08122A)            // --text
    val OnLightSecondary = Color(0xFF41506E)   // --text-2
    val OnLightTertiary = Color(0xFF71809B)    // --text-3
    val Divider = Color(0xFFDCE3F0)            // --border

    // ── Accents (identical in both themes) ──────────────────────────────────
    val CyanAccent = Color(0xFF22D3EE)
    val PurpleAccent = Color(0xFF8B5CF6)
    val PinkAccent = Color(0xFFEC4899)
    val WarningAmber = Color(0xFFF59E0B)
    val SuccessGreen = Color(0xFF10B981)
    val ErrorRed = Color(0xFFEF4444)
    val White = Color.White

    /** Matches the web `--gradient-brand`. */
    val BrandGradient = Brush.linearGradient(
        listOf(BlueDeep, Color(0xFF1E6FFF), CyanAccent)
    )
}

private val LightScheme = lightColorScheme(
    primary = ScottsTechXColors.BluePrimaryDark,
    onPrimary = Color.White,
    primaryContainer = ScottsTechXColors.PanelInputLight,
    onPrimaryContainer = ScottsTechXColors.OnLight,
    secondary = ScottsTechXColors.BluePrimary,
    onSecondary = Color.White,
    tertiary = ScottsTechXColors.PurpleAccent,
    onTertiary = Color.White,
    background = ScottsTechXColors.Background,
    onBackground = ScottsTechXColors.OnLight,
    surface = ScottsTechXColors.PanelLight,
    onSurface = ScottsTechXColors.OnLight,
    surfaceVariant = ScottsTechXColors.PanelInputLight,
    onSurfaceVariant = ScottsTechXColors.OnLightSecondary,
    error = ScottsTechXColors.ErrorRed,
    onError = Color.White,
    outline = ScottsTechXColors.Divider,
    outlineVariant = ScottsTechXColors.Divider,
)

private val DarkScheme = darkColorScheme(
    primary = ScottsTechXColors.BluePrimary,
    onPrimary = Color.White,
    primaryContainer = ScottsTechXColors.DarkPanelRaised,
    onPrimaryContainer = ScottsTechXColors.DarkOn,
    secondary = ScottsTechXColors.BluePrimaryLight,
    onSecondary = ScottsTechXColors.DarkBackground,
    tertiary = ScottsTechXColors.PurpleAccent,
    onTertiary = Color.White,
    background = ScottsTechXColors.DarkBackground,
    onBackground = ScottsTechXColors.DarkOn,
    surface = ScottsTechXColors.DarkPanel,
    onSurface = ScottsTechXColors.DarkOn,
    surfaceVariant = ScottsTechXColors.DarkPanelRaised,
    onSurfaceVariant = ScottsTechXColors.DarkOnSecondary,
    error = ScottsTechXColors.ErrorRed,
    onError = Color.White,
    outline = ScottsTechXColors.DarkBorder,
    outlineVariant = ScottsTechXColors.DarkBorder,
)

@Composable
fun ScottsTechXTheme(content: @Composable () -> Unit) {
    val mode = UserPrefs.themeMode
    val darkTheme = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ScottsTypography,
        content = content,
    )
}
