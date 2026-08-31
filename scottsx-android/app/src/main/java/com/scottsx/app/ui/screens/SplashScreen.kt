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
import androidx.compose.foundation.layout.padding
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
 * Splash / Launch screen — the two-beat ScottsTechX opening.
 *
 * The launch WINDOW background (launch_wordmark_bg) already shows the
 * wordmark at 45% opacity from the instant the app is tapped, so there
 * is never a black/blank screen. The Compose splash then picks up that
 * exact state and plays:
 *
 *   BEAT 1 — "ScottsTechX" FORMS (~0.5 s): the brand wordmark — the
 *   actual ScottsTechX lettering extracted from the company lockup
 *   (chrome silver + electric blue on transparent) — brightens from
 *   its launch-window state to full strength and settles to size
 *   (frame 1 is pixel-matched to the window background: same art,
 *   same 300 dp width, same screen centre — the hand-off is
 *   invisible), a chrome light band sweeping through the letters.
 *
 *   BEAT 2 — STX APPEARS: the wordmark dissolves and the chrome-blue
 *   STX monogram STAMPS in at full brightness — slightly oversized,
 *   settling while the engine-glow blooms behind it, then breathing
 *   with a chrome sheen sweeping the letters (~1.1 s).
 *
 *   EXIT (~0.35 s): quick fade, then hand-over to the flow.
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
    var forming by remember { mutableStateOf(false) }   // beat 1: wordmark forms
    var toMono by remember { mutableStateOf(false) }    // beat 2: STX stamps in
    var leaving by remember { mutableStateOf(false) }   // exit

    // BEAT 1 — the wordmark brightens from the launch-window state
    // (45%) to full strength and settles to rest size.
    val formProgress by animateFloatAsState(
        targetValue = if (forming) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "word-form",
    )
    // Beat transition — the wordmark dissolves away.
    val wordDissolve by animateFloatAsState(
        targetValue = if (toMono) 0f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "word-dissolve",
    )
    // BEAT 2 — monogram stamp-settle (the monogram itself is at FULL
    // brightness from its first frame; only the settle is animated).
    val settle by animateFloatAsState(
        targetValue = if (toMono) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "mono-settle",
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
        delay(1000)
        toMono = true
        delay(1100)
        leaving = true
        delay(360)
        onFinished()
    }

    val wordAlpha = (0.45f + 0.55f * formProgress) * wordDissolve
    val wordScale = 0.97f + 0.03f * formProgress
    // The monogram exists only while composed — full brightness throughout.
    val monoAlpha = 1f - exit
    val monoScale = (1.06f - 0.06f * settle) * (1f - 0.10f * exit) *
        (0.995f + 0.010f * breathe)

    // NOTE: no systemBarsPadding — the splash centres in the FULL
    // window so beat 1's wordmark sits exactly where the launch
    // window's wordmark already is (a seamless pick-up).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark),
        contentAlignment = Alignment.Center,
    ) {
        CinematicBackground()

        // BEAT 1 — the ScottsTechX wordmark forming. Same art / width /
        // position as the launch-window background, starting at the same
        // 45% opacity it was left at. As it brightens to full strength a
        // chrome light band sweeps across the letters — the forming is
        // unmistakable.
        Box(
            modifier = Modifier
                .width(300.dp)
                .aspectRatio(1365f / 105f)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    scaleX = wordScale
                    scaleY = wordScale
                    alpha = wordAlpha
                },
        ) {
            Image(
                painter = painterResource(R.drawable.stx_wordmark),
                contentDescription = "ScottsTechX",
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
            // Forming sheen — sweeps left-to-right through the letters as
            // the wordmark brightens (SrcIn keeps it inside the letters).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val sweep = -0.45f + 1.9f * formProgress
                        if (sweep > -0.5f && sweep < 1.5f && wordDissolve > 0f) {
                            val cx = size.width * sweep
                            val beamW = size.width * 0.34f
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color(0xFFEAF6FF).copy(
                                        alpha = 0.85f * wordDissolve,
                                    ),
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

        if (toMono) {
            // Engine glow blooming BEHIND the emblem.
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
                                        alpha = (0.10f + 0.26f * settle) *
                                            (0.55f + 0.45f * breathe) * monoAlpha,
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

            // BEAT 2 — the STX monogram, full brightness, with the
            // chrome sheen sweep (SrcIn keeps it inside the letters).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        scaleX = monoScale
                        scaleY = monoScale
                        alpha = monoAlpha
                    },
            ) {
                Image(
                    painter = painterResource(R.drawable.stx_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
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
}
