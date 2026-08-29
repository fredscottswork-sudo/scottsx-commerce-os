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
    val BluePrimaryDark = Color(0xFF0D2F7A)  // web --brand-600 (gradient deep end)
    /** Soft glow tint for backgrounds. */
    val BlueGlow = Color(0x331E6FFF)
    /** Link/accent — same as BluePrimary so colors stay unified. */
    val AccentLink = BluePrimary

    // ---- Legacy "light" surfaces — MUTABLE (see applyThemePalette) -------
    // Despite the names these default to the dark-app palette and are
    // swapped to true light values when the user picks LIGHT/SYSTEM mode.
    var PanelLight = Color(0xFF0B1020)      // web --surface (dark)
    var BackgroundLight = Color(0xFF05070D)  // web --bg (dark)
    var PanelInputLight = Color(0xFF121A2F)    // web --surface-2 (dark)

    // ---- Card surfaces + inks — MUTABLE (see applyThemePalette) ---------
    // The web styles every card with --surface/--surface-2 and its body ink
    // with --text/--text-2/--text-3, all of which flip under [data-theme].
    // Screens hardcoded those cards as Color.White and their ink as the
    // constant OnLight family, so in dark mode they stayed white-on-dark
    // (and, wherever ink leaked onto a swapping panel, dark-on-dark =
    // invisible). These tokens mirror the web ones: light mode renders
    // pixel-identical to the old hardcodes, dark mode follows the web's
    // dark palette exactly.
    var CardSurface = Color(0xFF0B1020)        // web --surface (dark)
    var CardSurfaceAlt = Color(0xFF121A2F)     // web --surface-2 (dark)
    var OnCard = Color(0xFFEEF2FB)             // web --text (dark)
    var OnCardSecondary = Color(0xFF94A3C4)    // web --text-2 (dark)
    var OnCardTertiary = Color(0xFF64748B)     // web --text-3 (dark)
    /** "Selected" tinted card — light: pale brand blue, dark: --surface-hover. */
    var CardTintSelected = Color(0xFF16203A)   // web --surface-hover (dark)
    val PanelBorderHint = Color(0xFFDFE6F2)  // web --border (light)

    // ---- Text ON the swappable panels (the "black icons" fix) -------
    // OnLight is a constant dark ink meant for real LIGHT surfaces only;
    // anything drawn on PanelLight/BackgroundLight/PanelInputLight must
    // use these two, which swap in applyThemePalette alongside the panels
    // themselves. Otherwise dark mode renders #08122A ink on #0B1020.
    var OnPanel = Color(0xFFEEF2FB)          // web --text (dark)
    var OnPanelSecondary = Color(0xFF94A3C4) // web --text-2 (dark)

    // ---- v0.23 dark surfaces ---------------------------------------------
    /** Page background — near-black navy. */
    val BackgroundDark = Color(0xFF05070D)  // web --bg (dark)
    /** Cards & list items. */
    val SurfacePanelDark = Color(0xFF0B1020)  // web --surface (dark)
    /** Slightly lighter cards on top of SurfacePanelDark. */
    val SurfaceElevatedDark = Color(0xFF121A2F)  // web --surface-2 (dark)
    /** Inputs and dividers on dark. */
    val SurfaceInputDark = Color(0xFF121A2F)  // web --surface-2: inputs on dark
    /** Page background behind cards. */
    val BackgroundSubtle = Color(0xFF02040A)  // web --bg-deep (dark)
    /** Convenience: dark surface for legacy `Color.White` panel sites. */
    val SurfaceDarkCard = Color(0xFF121A2F)  // web --surface-2 (dark)

    // ---- Panels (fixed light/dark pairs) -----------------------------------
    val Background = Color(0xFFF5F7FC)  // web --bg (light)
    val Divider = Color(0xFFDFE6F2)  // web --border (light)
    val DarkBackground = Color(0xFF0E1420)
    val DarkPanel = Color(0xFF0B1020)           // web --surface
    val DarkPanelRaised = Color(0xFF121A2F)     // web --surface-2          // one step above DarkPanel
    val DarkPanelHover = Color(0xFF1A2440)      // web --surface-3           // pressed/hover state
    val DarkBorder = Color(0xFF1E2A45)          // web --border (dark)               // hairline on dark surfaces

    // ---- Text --------------------------------------------------------------
    /** Primary text on dark backgrounds — white. */
    val OnDark = Color(0xFFFFFFFF)
    /** Secondary text on dark — muted blue-gray. */
    val OnDarkSecondary = Color(0xFF94A3C4)  // web --text-2 (dark)
    /** Tertiary / hints on dark. */
    val OnDarkMuted = Color(0xFF64748B)  // web --text-3 (dark)
    val DarkOn = Color(0xFFEEF2FB)  // web --text (dark)
    val DarkOnSecondary = Color(0xFF94A3C4)  // web --text-2 (dark)
    val DarkOnTertiary = Color(0xFF64748B)   // web --text-3 (dark)           // lowest-emphasis dark text
    /** Primary text on light backgrounds. */
    val OnLight = Color(0xFF08122A)  // web --text (light)
    val OnLightSecondary = Color(0xFF55627D)  // web --text-2 (light)
    val OnLightTertiary = Color(0xFF8492AD)   // web --text-3 (light)          // lowest-emphasis light.text

    // ---- Status / semantic --------------------------------------------------
    val SuccessGreen = Color(0xFF16A34A)
    val ErrorRed = Color(0xFFEF4444)  // web --danger
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
            PanelLight = Color(0xFFFFFFFF)        // web --surface (light)
            BackgroundLight = Color(0xFFF5F7FC)   // web --bg (light)
            PanelInputLight = Color(0xFFF3F6FC)   // web --surface-2 (light)
            OnPanel = Color(0xFF08122A)           // web --text (light)
            OnPanelSecondary = Color(0xFF55627D)  // web --text-2 (light)
            CardSurface = Color(0xFFFFFFFF)       // web --surface (light)
            CardSurfaceAlt = Color(0xFFF3F6FC)    // web --surface-2 (light)
            OnCard = Color(0xFF08122A)            // web --text (light)
            OnCardSecondary = Color(0xFF55627D)   // web --text-2 (light)
            OnCardTertiary = Color(0xFF8492AD)    // web --text-3 (light)
            CardTintSelected = Color(0xFFE3F2FD)  // pale brand blue (light)
        } else {
            PanelLight = Color(0xFF0B1020)        // web --surface (dark)
            BackgroundLight = Color(0xFF05070D)   // web --bg (dark)
            PanelInputLight = Color(0xFF121A2F)   // web --surface-2 (dark)
            OnPanel = Color(0xFFEEF2FB)           // web --text (dark)
            OnPanelSecondary = Color(0xFF94A3C4)  // web --text-2 (dark)
            CardSurface = Color(0xFF0B1020)       // web --surface (dark)
            CardSurfaceAlt = Color(0xFF121A2F)    // web --surface-2 (dark)
            OnCard = Color(0xFFEEF2FB)            // web --text (dark)
            OnCardSecondary = Color(0xFF94A3C4)   // web --text-2 (dark)
            OnCardTertiary = Color(0xFF64748B)    // web --text-3 (dark)
            CardTintSelected = Color(0xFF16203A)  // web --surface-hover (dark)
        }
    }
}
