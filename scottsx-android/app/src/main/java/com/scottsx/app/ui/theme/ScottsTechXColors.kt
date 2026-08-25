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
 * ScottsTechX brand palette. Screens reference these directly for accents and
 * gradients; MaterialTheme carries the light/dark scheme.
 */
object ScottsTechXColors {
    // Primary blues
    val BluePrimary = Color(0xFF1E6FFF)
    val BluePrimaryLight = Color(0xFF5B9BFF)
    val BluePrimaryDark = Color(0xFF124CA8)

    // Light panels
    val PanelLight = Color(0xFFF4F6FB)
    val PanelInputLight = Color(0xFFEDF1F8)
    val OnLight = Color(0xFF121826)
    val OnLightSecondary = Color(0xFF5A6478)
    val Background = Color(0xFFFAFBFF)
    val Divider = Color(0xFFE4E8F0)

    // Dark panels
    val DarkBackground = Color(0xFF0E1420)
    val DarkPanel = Color(0xFF1A2233)
    val DarkOn = Color(0xFFE9EDF5)
    val DarkOnSecondary = Color(0xFF98A2B8)

    // Semantic
    val SuccessGreen = Color(0xFF16A34A)
    val ErrorRed = Color(0xFFDC2626)
    val WarningAmber = Color(0xFFF59E0B)
    val White = Color.White
    val PurpleAccent = Color(0xFF8B5CF6)
    val PinkAccent = Color(0xFFEC4899)

    // ── Added for newer screens (cart, nearby, messaging, scaffolding) ──────
    // These are ADDITIONS ONLY. Every value above is the original palette,
    // untouched. Each tone below is derived from those originals so the app
    // keeps the exact look it had, while satisfying code that references
    // these names.
    val BlueDeep = Color(0xFF0D2F7A)                 // deep end of the brand gradient
    val CyanAccent = Color(0xFF22D3EE)               // accent used by newer cards
    val DarkPanelRaised = Color(0xFF222C40)          // one step above DarkPanel
    val DarkPanelHover = Color(0xFF283348)           // pressed/hover state
    val DarkBorder = Color(0xFF2A3550)               // hairline on dark surfaces
    val DarkOnTertiary = Color(0xFF6F7FA0)           // lowest-emphasis dark text
    val OnLightTertiary = Color(0xFF71809B)          // lowest-emphasis light text

    /** Brand gradient, anchored on the original BluePrimary. */
    val BrandGradientColors = listOf(BlueDeep, BluePrimary, CyanAccent)
    val BrandGradient = Brush.linearGradient(BrandGradientColors)

    /** Blue-only hero gradient (deep -> action blue) for large surfaces. */
    val BlueHeroColors = listOf(BlueDeep, BluePrimary, BluePrimaryLight)
}

private val LightScheme = lightColorScheme(
    primary = ScottsTechXColors.BluePrimary,
    onPrimary = Color.White,
    primaryContainer = ScottsTechXColors.PanelInputLight,
    onPrimaryContainer = ScottsTechXColors.OnLight,
    secondary = ScottsTechXColors.BluePrimaryLight,
    onSecondary = Color.White,
    background = ScottsTechXColors.Background,
    onBackground = ScottsTechXColors.OnLight,
    surface = Color.White,
    onSurface = ScottsTechXColors.OnLight,
    surfaceVariant = ScottsTechXColors.PanelLight,
    onSurfaceVariant = ScottsTechXColors.OnLightSecondary,
    error = ScottsTechXColors.ErrorRed,
    onError = Color.White,
    outline = ScottsTechXColors.Divider,
)

private val DarkScheme = darkColorScheme(
    primary = ScottsTechXColors.BluePrimaryLight,
    onPrimary = ScottsTechXColors.DarkBackground,
    primaryContainer = ScottsTechXColors.DarkPanel,
    onPrimaryContainer = ScottsTechXColors.DarkOn,
    secondary = ScottsTechXColors.BluePrimaryLight,
    onSecondary = ScottsTechXColors.DarkBackground,
    background = ScottsTechXColors.DarkBackground,
    onBackground = ScottsTechXColors.DarkOn,
    surface = ScottsTechXColors.DarkPanel,
    onSurface = ScottsTechXColors.DarkOn,
    surfaceVariant = ScottsTechXColors.DarkPanel,
    onSurfaceVariant = ScottsTechXColors.DarkOnSecondary,
    error = ScottsTechXColors.ErrorRed,
    onError = Color.White,
    outline = ScottsTechXColors.DarkOnSecondary.copy(alpha = 0.3f),
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
