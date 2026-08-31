package com.scottsx.app.ui.screens

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.scottsx.app.R
import com.scottsx.app.ui.components.CinematicBackground
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * Splash / Launch screen — the STX opening.
 *
 * The launch WINDOW background (launch_stx_bg) already shows the
 * chrome-blue STX monogram at 45% opacity from the instant the app is
 * tapped, so there is never a black/blank screen. The Compose splash
 * picks up that exact state (same art, same 210 dp width, same screen
 * centre — the hand-off is invisible) and plays:
 *
 *   FORM (~0.5 s) — the STX monogram brightens from its launch-window
 *   state to full strength and settles to rest size while a chrome
 *   light band sweeps through the letters.
 *
 *   HOLD (~1.2 s) — the emblem breathes, the engine-glow blooms and
 *   pulses behind it, and a chrome sheen sweeps the letters every
 *   ~1.9 s.
 *
 *   EXIT (~0.35 s) — quick fade, then hand-over to the flow.
 *
 * The catalogue + cart warm-ups are fired at frame zero and race the
 * brand beat — the splash NEVER waits on the network.
 *
 * Deliberately bulletproof: standard Compose animation APIs only (the
 * same ones BrandLogo / PrimaryButton already use), one delay-driven
 * LaunchedEffect, no platform splash screen, no splash-screen API, no
 * manual frame loops — the exact launch mechanism of the build that
 * works on real devices.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    var forming by remember { mutableStateOf(false) }   // form: brighten + settle
    var leaving by remember { mutableStateOf(false) }   // exit

    // FORM — from the launch-window state (45%) to full strength.
    val formProgress by animateFloatAsState(
        targetValue = if (forming) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "stx-form",
    )
    // EXIT — quick fade before the hand-over.
    val exit by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        label = "stx-exit",
    )
    // Continuous brand motion — glow breathing + chrome sheen sweep.
    val motion = rememberInfiniteTransition(label = "stx-motion")
    val breathe by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stx-breathe",
    )
    val sheen by motion.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "stx-sheen",
    )

    LaunchedEffect(Unit) {
        // The brand beat doubles as the marketplace's warm-up window:
        // while the opening plays we pull the live catalog + cart so
        // the home screen renders instantly. Fire-and-forget warm —
        // the splash NEVER waits on the network; a slow connection
        // shows the normal per-screen shimmers instead of a stuck
        // launch.
        com.scottsx.app.data.LiveMarketplace.warm()
        com.scottsx.app.data.CartStore.warm()
        forming = true
        delay(1700)
        leaving = true
        delay(360)
        onFinished()
    }

    val logoAlpha = (0.45f + 0.55f * formProgress) * (1f - exit)
    val logoScale = (0.97f + 0.03f * formProgress) * (1f - 0.10f * exit) *
        (0.995f + 0.010f * breathe)

    // NOTE: no systemBarsPadding — the splash centres in the FULL
    // window so the STX sits exactly where the launch window's STX
    // already is (a seamless pick-up).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark),
        contentAlignment = Alignment.Center,
    ) {
        CinematicBackground()

        // Engine glow blooming BEHIND the emblem — breathes with the
        // brand motion and blooms in as the monogram forms.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1.45f)
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = min(size.width, size.height) * (0.62f + 0.16f * breathe)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ScottsTechXColors.BlueGlow.copy(
                                    alpha = (0.10f + 0.26f * formProgress) *
                                        (0.55f + 0.45f * breathe) * logoAlpha,
                                ),
                                Color.Transparent,
                            ),
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            radius = r,
                        ),
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                    )
                },
        )

        // The STX monogram — same art / width / position as the launch
        // window background (210 dp wide), starting at the same 45%
        // opacity it was left at, with the chrome sheen sweep (SrcIn
        // keeps the sheen inside the letters only).
        Box(
            modifier = Modifier
                .width(210.dp)
                .aspectRatio(1100f / 450f)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                },
        ) {
            Image(
                painter = painterResource(R.drawable.stx_logo),
                contentDescription = "ScottsTechX",
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        if (sheen > -0.5f && sheen < 1.5f) {
                            val cx = size.width * sheen
                            val beamW = size.width * 0.30f
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color(0xFFEAF6FF).copy(alpha = 0.80f),
                                    1f to Color.Transparent,
                                    startX = cx - beamW / 2f,
                                    endX = cx + beamW / 2f,
                                ),
                                blendMode = BlendMode.SrcIn,
                            )
                        }
                    },
            )
        }
    }
}
