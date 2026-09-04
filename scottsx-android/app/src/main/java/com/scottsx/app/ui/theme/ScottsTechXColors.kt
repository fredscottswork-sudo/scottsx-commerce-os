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
 *
 * This is the merged palette: the MaterialTheme light/dark schemes plus the
 * legacy dark-mode tokens (BlueGlow, BackgroundDark, SurfacePanelDark, ...)
 * that screens still reference directly. The three `*Light` surface tokens are
 * mutable so [applyThemePalette] can re-skin direct references when the user
 * picks a theme mode; the MaterialTheme schemes below use literals so they are
 * unaffected by that swap.
 */
object ScottsTechXColors {
    // Primary blues
    val BluePrimary = Color(0xFF1E6FFF)
    val BluePrimaryLight = Color(0xFF5B9BFF)
    val BluePrimaryDark = Color(0xFF124CA8)
    /** Soft glow tint for backgrounds / logos. */
    val BlueGlow = Color(0x331680FF)

    // Light panels (default values are dark — the app defaults to DARK and
    // applyThemePalette swaps these to the light set when the user asks).
    var PanelLight = Color(0xFF0C1322)
    var BackgroundLight = Color(0xFF050912)
    var PanelInputLight = Color(0xFF11192A)
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

    // ---- Legacy dark-mode tokens (Color.kt) -------------------------------
    /** Page background — near-black navy. */
    val BackgroundDark = Color(0xFF050912)
    /** Cards & list items. */
    val SurfacePanelDark = Color(0xFF0C1322)
    /** Slightly lighter cards on top of SurfacePanelDark. */
    val SurfaceElevatedDark = Color(0xFF11192A)
    /** Inputs and dividers. */
    val SurfaceInputDark = Color(0xFF0F172A)
    /** Subtle borders / dividers. */
    val PanelBorderHint = Color(0xFFE5E7EB)

    // ---- Text -------------------------------------------------------------
    /** Primary text on dark backgrounds — white. */
    val OnDark = Color(0xFFFFFFFF)
    /** Secondary text on dark — muted blue-gray. */
    val OnDarkSecondary = Color(0xFFB7BCC8)
    /** Tertiary / hints on dark. */
    val OnDarkMuted = Color(0xFF8A91A0)

    /** Link/accent — same as BluePrimary so colors stay unified. */
    val AccentLink = Color(0xFF1680FF)

    // ---- Status colors ----------------------------------------------------
    /** Destructive (delete, error) — restrained pink/red. */
    val Danger = Color(0xFFE94B6E)
    /** Success / confirmed. */
    val Success = Color(0xFF22C55E)

    // ----------------------------------------------------------------
    // Semantic aliases (used by the rest of the app)
    // ----------------------------------------------------------------
    val Primary = BluePrimary
    val TextPrimary = OnDark  // white text on dark
    val TextSecondary = OnDarkSecondary
    val TextMuted = OnDarkMuted
    val Surface = SurfacePanelDark
    val BackgroundSubtle = Color(0xFF0A1120)
    /** Convenience: dark surface for legacy `Color.White` panel sites. */
    val SurfaceDarkCard = Color(0xFF11192A)

    /**
     * Swap the legacy "light" surface tokens at runtime so screens
     * that reference PanelLight / BackgroundLight / PanelInputLight
     * directly (rather than via MaterialTheme.colorScheme) re-skin
     * when the user picks LIGHT mode.
     *
     * Called from ThemePreference.set().
     */
    fun applyThemePalette(isLight: Boolean) {
        if (isLight) {
            PanelLight = Color(0xFFF4F6FB)
            BackgroundLight = Color(0xFFF8FAFC)
            PanelInputLight = Color(0xFFEDF1F8)
        } else {
            PanelLight = Color(0xFF0C1322)
            BackgroundLight = Color(0xFF050912)
            PanelInputLight = Color(0xFF11192A)
        }
    }
}

private val LightScheme = lightColorScheme(
    primary = ScottsTechXColors.BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDF1F8),
    onPrimaryContainer = ScottsTechXColors.OnLight,
    secondary = ScottsTechXColors.BluePrimaryLight,
    onSecondary = Color.White,
    background = ScottsTechXColors.Background,
    onBackground = ScottsTechXColors.OnLight,
    surface = Color.White,
    onSurface = ScottsTechXColors.OnLight,
    surfaceVariant = Color(0xFFF4F6FB),
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
