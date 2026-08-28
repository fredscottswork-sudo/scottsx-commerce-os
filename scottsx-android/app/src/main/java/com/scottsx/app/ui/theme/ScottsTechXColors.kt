package com.scottsx.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * ScottsTechX brand palette — the single source of truth.
 *
 * This file used to have a twin (theme/Color.kt) declaring a second
 * `object ScottsTechXColors` in the same package — a redeclaration the
 * Gradle build could never survive. The two are merged here:
 *
 *   - Brand blues match the website tokens 1:1 (web/src/styles/globals.css:
 *     --brand-400 #1E6FFF, gradient #0D2F7A → #1E6FFF → #22D3EE).
 *   - The deep-navy surface set from the v0.23 design brief (Color.kt) is
 *     kept for the screens built against it, verbatim.
 *   - The "legacy light" trio (PanelLight / BackgroundLight /
 *     PanelInputLight) is mutable on purpose: [applyThemePalette] swaps
 *     them when the user changes theme, so screens referencing them
 *     directly re-skin without going through MaterialTheme.
 */
object ScottsTechXColors {
    // ---- Primary palette (electric blue, web-matched) --------------------
    /** Main brand blue — primary buttons, links, active nav. */
    val BluePrimary = Color(0xFF1E6FFF)
    /** Lighter blue for highlights, gradient tops, accents. */
    val BluePrimaryLight = Color(0xFF5B9BFF)
    /** Deeper blue for gradient bottoms, pressed states. */
    val BluePrimaryDark = Color(0xFF124CA8)
    /** Soft glow tint for backgrounds. */
    val BlueGlow = Color(0x331E6FFF)
    /** Link/accent — same as BluePrimary so colors stay unified. */
    val AccentLink = BluePrimary

    // ---- Legacy "light" surfaces — MUTABLE (see applyThemePalette) -------
    // Despite the names these default to the dark-app palette and are
    // swapped to true light values when the user picks LIGHT/SYSTEM mode.
    var PanelLight = Color(0xFF0C1322)
    var BackgroundLight = Color(0xFF050912)
    var PanelInputLight = Color(0xFF11192A)
    val PanelBorderHint = Color(0xFFE5E7EB)

    // ---- v0.23 dark surfaces ---------------------------------------------
    /** Page background — near-black navy. */
    val BackgroundDark = Color(0xFF050912)
    /** Cards & list items. */
    val SurfacePanelDark = Color(0xFF0C1322)
    /** Slightly lighter cards on top of SurfacePanelDark. */
    val SurfaceElevatedDark = Color(0xFF11192A)
    /** Inputs and dividers on dark. */
    val SurfaceInputDark = Color(0xFF0F172A)
    /** Page background behind cards. */
    val BackgroundSubtle = Color(0xFF0A1120)
    /** Convenience: dark surface for legacy `Color.White` panel sites. */
    val SurfaceDarkCard = Color(0xFF11192A)

    // ---- Panels (fixed light/dark pairs) -----------------------------------
    val Background = Color(0xFFFAFBFF)
    val Divider = Color(0xFFE4E8F0)
    val DarkBackground = Color(0xFF0E1420)
    val DarkPanel = Color(0xFF1A2233)
    val DarkPanelRaised = Color(0xFF222C40)          // one step above DarkPanel
    val DarkPanelHover = Color(0xFF283348)           // pressed/hover state
    val DarkBorder = Color(0xFF2A3550)               // hairline on dark surfaces

    // ---- Text --------------------------------------------------------------
    /** Primary text on dark backgrounds — white. */
    val OnDark = Color(0xFFFFFFFF)
    /** Secondary text on dark — muted blue-gray. */
    val OnDarkSecondary = Color(0xFFB7BCC8)
    /** Tertiary / hints on dark. */
    val OnDarkMuted = Color(0xFF8A91A0)
    val DarkOn = Color(0xFFE9EDF5)
    val DarkOnSecondary = Color(0xFF98A2B8)
    val DarkOnTertiary = Color(0xFF6F7FA0)           // lowest-emphasis dark text
    /** Primary text on light backgrounds. */
    val OnLight = Color(0xFF121826)
    val OnLightSecondary = Color(0xFF5A6478)
    val OnLightTertiary = Color(0xFF71809B)          // lowest-emphasis light.text

    // ---- Status / semantic --------------------------------------------------
    val SuccessGreen = Color(0xFF16A34A)
    val ErrorRed = Color(0xFFDC2626)
    val WarningAmber = Color(0xFFF59E0B)
    /** Destructive (delete, error) — restrained pink/red. */
    val Danger = Color(0xFFE94B6E)
    /** Success / confirmed. */
    val Success = Color(0xFF22C55E)
    val White = Color.White
    val PurpleAccent = Color(0xFF8B5CF6)
    val PinkAccent = Color(0xFFEC4899)
    val CyanAccent = Color(0xFF22D3EE)               // accent used by newer cards

    // ---- Semantic aliases (used by the rest of the app) ---------------------
    val Primary = BluePrimary
    val TextPrimary = OnDark  // white text on dark
    val TextSecondary = OnDarkSecondary
    val TextMuted = OnDarkMuted
    val Surface = SurfacePanelDark

    // ---- Gradients ------------------------------------------------------------
    val BlueDeep = Color(0xFF0D2F7A)                 // deep end of the brand gradient

    /** Brand gradient, anchored on the web token trio. */
    val BrandGradientColors = listOf(BlueDeep, BluePrimary, CyanAccent)
    val BrandGradient = Brush.linearGradient(BrandGradientColors)

    /** Blue-only hero gradient (deep -> action blue) for large surfaces. */
    val BlueHeroColors = listOf(BlueDeep, BluePrimary, BluePrimaryLight)

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
            PanelLight = Color(0xFFFFFFFF)
            BackgroundLight = Color(0xFFF8FAFC)
            PanelInputLight = Color(0xFFF1F3F7)
        } else {
            PanelLight = Color(0xFF0C1322)
            BackgroundLight = Color(0xFF050912)
            PanelInputLight = Color(0xFF11192A)
        }
    }
}
