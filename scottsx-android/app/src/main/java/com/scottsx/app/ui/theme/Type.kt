package com.scottsx.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.unit.sp
import com.scottsx.app.R

/**
 * Brand typography — matches the web exactly:
 *   Display / headlines → Sora   (web --font-display)
 *   Body / labels       → Inter  (web --font-body)
 *
 * Both load through the Play Services downloadable-fonts provider, so the
 * APK carries no font binaries. `bestEffort = true` means a device with no
 * GMS (or an offline first launch) silently falls back to the system sans
 * instead of crashing — brand typography is additive, never a hard dep.
 */
private val gmsFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

// One GoogleFont INSTANCE per face — the API requires all weights of a
// family to reference the same instance.
private val soraGoogleFont = GoogleFont("Sora")
private val interGoogleFont = GoogleFont("Inter")

// NOTE: no `bestEffort` param — that flag was added in Compose 1.7 and
// this module pins BOM 2023.10.01. The 1.5.x overload already resolves
// asynchronously with the system sans as its initial/failed-load face.
private fun gmsFont(face: GoogleFont, weight: FontWeight) = Font(
    googleFont = face,
    fontProvider = gmsFontProvider,
    weight = weight,
    style = FontStyle.Normal,
)

/** Sora — the brand display face (hero words, panel titles, prices). */
val SoraFamily = FontFamily(
    gmsFont(soraGoogleFont, FontWeight.Medium),
    gmsFont(soraGoogleFont, FontWeight.SemiBold),
    gmsFont(soraGoogleFont, FontWeight.Bold),
    gmsFont(soraGoogleFont, FontWeight.ExtraBold),
)

/** Inter — body copy, labels, buttons (identical to the web's --font-body). */
val InterFamily = FontFamily(
    gmsFont(interGoogleFont, FontWeight.Normal),
    gmsFont(interGoogleFont, FontWeight.Medium),
    gmsFont(interGoogleFont, FontWeight.SemiBold),
    gmsFont(interGoogleFont, FontWeight.Bold),
)

val ScottsTechXTypography: Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
