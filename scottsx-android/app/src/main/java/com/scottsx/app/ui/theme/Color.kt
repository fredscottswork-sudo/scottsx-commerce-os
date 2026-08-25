package com.scottsx.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ScottsTechX brand palette — premium dark-mode marketplace.
 *
 * Inspired by the v0.23 design brief:
 *   - Deep navy/black foundation
 *   - Electric-blue interaction system
 *   - White text on dark surfaces
 *   - Subtle borders, no heavy shadows
 *
 * Primary accent:    electric blue (#1680FF)
 * Surfaces:          #050912 (background), #0C1322 (cards)
 * Borders:           #1B2233
 * Text:              white / off-white on dark, secondary muted
 */
object ScottsTechXColors {
    // ---- Primary palette (electric blue) ------------------------------
    /** Main brand blue — primary buttons, links, active nav. */
    val BluePrimary = Color(0xFF1680FF)
    /** Lighter blue for highlights, gradient tops, accents. */
    val BluePrimaryLight = Color(0xFF2D8CFF)
    /** Deeper blue for gradient bottoms, pressed states. */
    val BluePrimaryDark = Color(0xFF0A5FE5)
    /** Soft glow tint for backgrounds. */
    val BlueGlow = Color(0x331680FF)

    // ---- Dark surfaces ------------------------------------------------
    /** Page background — near-black navy. */
    val BackgroundDark = Color(0xFF050912)
    /** Cards & list items. */
    val SurfacePanelDark = Color(0xFF0C1322)
    /** Slightly lighter cards on top of SurfacePanelDark. */
    val SurfaceElevatedDark = Color(0xFF11192A)
    /** Inputs and dividers. */
    val SurfaceInputDark = Color(0xFF0F172A)
    /** Subtle borders / dividers. */
    val Divider = Color(0xFF1B2233)

    // ---- Light surfaces (LIGHT theme mode + select legacy screens) --
    var PanelLight = Color(0xFF0C1322)
    var BackgroundLight = Color(0xFF050912)
    var PanelInputLight = Color(0xFF11192A)
    val PanelBorderHint = Color(0xFFE5E7EB)

    // ---- Text ---------------------------------------------------------
    /** Primary text on dark backgrounds — white. */
    val OnDark = Color(0xFFFFFFFF)
    /** Secondary text on dark — muted blue-gray. */
    val OnDarkSecondary = Color(0xFFB7BCC8)
    /** Tertiary / hints on dark. */
    val OnDarkMuted = Color(0xFF8A91A0)
    /** Primary text on light backgrounds. */
    val OnLight = Color(0xFF0F172A)
    val OnLightSecondary = Color(0xFF6B7280)

    /** Link/accent — same as BluePrimary so colors stay unified. */
    val AccentLink = Color(0xFF1680FF)

    // ---- Status colors ------------------------------------------------
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
    val Background = BackgroundDark
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