package com.scottsx.app.ui.screens

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
 * Splash / Launch screen — the STX brand moment on cold-start.
 *
 * The ScottsTechX "STX" monogram (chrome-blue, house style) plays a
 * short branded opening the instant the app opens:
 *
 *   1. ENTRANCE (~0.7 s) — the logo fades in and settles up to full
 *      size while a blue engine-glow blooms behind it.
 *   2. HOLD (~1.2 s) — the emblem breathes (glow pulses), and a chrome
 *      sheen sweeps across the letters every ~1.9 s.
 *   3. EXIT (~0.35 s) — quick fade, then hand-over to the flow.
 *
 * The catalogue + cart warm-ups are fired at frame zero and race the
 * brand beat — the splash NEVER waits on the network.
 *
 * Deliberately bulletproof: standard Compose animation APIs only (the
 * same ones BrandLogo / PrimaryButton already use), one delay-driven
 * LaunchedEffect, no platform splash screen, no theme tricks, no
 * manual frame loops — the exact launch mechanism of the build that
 * works on real devices.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }     // entrance trigger
    var leaving by remember { mutableStateOf(false) }   // exit trigger

    // 1. Entrance — logo fades in (visible faintly from frame one) and
    // settles up to full size.
    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0.18f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "stx-entrance",
    )

    // 3. Exit — quick fade-out before the hand-over.
    val exit by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        label = "stx-exit",
    )

    // 2. Continuous brand motion — glow breathing + chrome sheen sweep.
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
        // while the logo plays we pull the live catalog + cart so the
        // home screen renders instantly. Fire-and-forget warm — the
        // splash NEVER waits on the network; a slow connection shows
        // the normal per-screen shimmers instead of a stuck launch.
        com.scottsx.app.data.LiveMarketplace.warm()
        com.scottsx.app.data.CartStore.warm()
        shown = true
        delay(1950)
        leaving = true
        delay(360)
        onFinished()
    }

    val logoAlpha = entrance * (1f - exit)
    val logoScale = (0.90f + 0.10f * entrance) * (1f - 0.10f * exit) *
        (0.995f + 0.010f * breathe)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        CinematicBackground()

        // Engine glow blooming BEHIND the emblem — breathes with the
        // brand motion and fades with the entrance/exit.
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
                                    alpha = (0.22f + 0.14f * breathe) * logoAlpha,
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

        // The STX monogram with its chrome sheen sweep.
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
            )

            // Chrome sheen band — SrcIn keeps it inside the letters only.
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
