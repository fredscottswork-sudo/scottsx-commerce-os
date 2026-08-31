package com.scottsx.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.scottsx.app.R
import com.scottsx.app.ui.components.CinematicBackground
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * SPLASH — the cinemapic STX opening that owns the WHOLE load window.
 *
 * From the instant the icon is tapped the platform shows the STX
 * monogram (Android 12 SplashScreen icon / pre-31 window layer-list —
 * both render the same assembled art). The first Compose frame is
 * pixel-identical: letters already fused at full strength, then:
 *
 *   0.00–0.35  STX sits assembled on the rising engine bloom (seamless)
 *   0.35–0.95  energy burst — S / T / X scatter apart at speed
 *   0.95–1.75  fusion flight — letters spring back together (overshoot)
 *   1.70       impact flash + energy rings ripple outward
 *   2.05–3.25  chrome shimmer sweeps the fused metal letters, twice
 *   3.25+      IGNITION HOLD — breathing emblem, sparks, ripples and a
 *              shimmer pass every ~1.9 s — sustained for as long as the
 *              live catalogue is still loading (capped at 8.5 s), never
 *              held up by the network: tap skips, slow fetch hands over.
 *   exit       STX zooms through the camera and fades -> the app.
 *
 * A single frame clock drives everything, so nothing can be cancelled
 * mid-animation and the jump to the exit beat is always instantaneous.
 */

private const val MIN_SHOW_S = 3.2f     // brand beat minimum on screen
private const val HARD_CAP_S = 8.5f     // never trap users on a cold server
private const val EXIT_S = 0.75f
private const val MONOGRAM_ASPECT = 3.1877f   // W/H of the shared letter canvas

/** Normalised 0..1 progress of the stage window (a to b) inside the clock. */
private fun stage(t: Float, a: Float, b: Float): Float =
    ((t - a) / (b - a)).coerceIn(0f, 1f)

/** 1 - (1-x)^3 ease-out. */
private fun easeOut(x: Float): Float {
    val d = 1f - x.coerceIn(0f, 1f)
    return 1f - d * d * d
}

/** OvershootInterpolator (tension 2.4) — passes the target, then settles. */
private fun overshoot(x: Float): Float {
    val d = x.coerceIn(0f, 1f) - 1f
    return d * d * (3.4f * d + 2.4f) + 1f
}

private data class Spark(
    val x: Float, val y: Float,
    val r: Float, val phase: Float,
    val blue: Boolean,
)

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    var clock by remember { mutableFloatStateOf(0f) }   // seconds since frame 0
    var exit by remember { mutableFloatStateOf(0f) }
    var skipped by remember { mutableStateOf(false) }

    val sparks = remember {
        List(26) { i ->
            Spark(
                x = ((i * 37) % 100) / 100f,
                y = ((i * 61) % 100) / 100f,
                r = 1.6f + (i % 3) * 1.1f,
                phase = ((i * 13) % 40) / 40f,
                blue = i % 3 != 0,
            )
        }
    }

    LaunchedEffect(Unit) {
        // The warm window: kick the catalogue + cart fetches; the splash
        // holds (ignition loop) until the catalogue reaches a terminal
        // state or the hard cap — whichever comes first.
        com.scottsx.app.data.LiveMarketplace.warm()
        com.scottsx.app.data.CartStore.warm()
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                clock = (now - start) / 1_000_000_000f
            }
            val market = com.scottsx.app.data.LiveMarketplace.state.value
            val loaded = market != com.scottsx.app.data.LiveMarketplace.State.Idle &&
                market != com.scottsx.app.data.LiveMarketplace.State.Loading
            if ((loaded && clock >= MIN_SHOW_S) || skipped || clock >= HARD_CAP_S) break
        }
        start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                exit = ((now - start) / 1_000_000_000f / EXIT_S).coerceAtMost(1f)
            }
            if (exit >= 1f) break
        }
        onFinished()
    }

    // ── burst + fusion choreography ─────────────────────────────────────
    // scatterAmt: 0 assembled -> 1 scattered (0.35-0.95s) -> springs back
    // through 0 (overshoot, 0.95-1.75s). One driver for all three letters.
    val scatterAmt = when {
        clock < 0.35f -> 0f
        clock < 0.95f -> easeOut(stage(clock, 0.35f, 0.95f))
        else -> 1f - overshoot(stage(clock, 0.95f, 1.75f))
    }
    val letterAlpha = (1f - 0.55f * scatterAmt.coerceAtLeast(0f)).coerceIn(0f, 1f)
    val sc = scatterAmt                          // shorthand below
    val holdClock = (clock - 3.25f).coerceAtLeast(0f)

    // impact flash + rings
    val flashF = stage(clock, 1.70f, 1.92f)
    val flash = if (flashF < 0.22f) flashF / 0.22f else 1f - (flashF - 0.22f) / 0.78f
    val ring1 = stage(clock, 1.72f, 2.45f)
    val ring2 = stage(clock, 1.95f, 2.70f)
    // ignition-loop ripple, every 1.7 s while holding
    val rippleF = (holdClock % 1.7f) / 1.1f
    val ripple = if (clock > 3.25f && rippleF <= 1f) rippleF else 0f

    val breathe = if (clock > 1.75f) 1f + 0.015f * sin(clock * 2.2f * PI.toFloat() / 2f) else 1f
    val pop = if (clock < 0.35f) 1f else 0.93f + 0.07f * overshoot(stage(clock, 1.55f, 1.78f))
    val bloom = easeOut(stage(clock, 0.0f, 0.7f)) * (1f - exit)
    val emblemScale = breathe * pop * (1f + 4.2f * exit)
    val emblemAlpha = (1f - exit) * (1f - exit)

    // shimmer schedule: 2.05-2.60, 2.80-3.25, then one pass every 1.9 s
    // in the hold loop.
    val shimmerPasses = buildList<Float> {
        add(stage(clock, 2.05f, 2.60f))
        add(stage(clock, 2.80f, 3.25f))
        if (clock > 3.4f) {
            val f = (holdClock % 1.9f) / 0.75f
            add(if (f <= 1f) f else 0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { skipped = true })
            },
        contentAlignment = Alignment.Center,
    ) {
        CinematicBackground()

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(MONOGRAM_ASPECT)
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val baseR = min(size.width, size.height)

                    // engine bloom rising behind the emblem
                    if (bloom > 0f) {
                        val gAlpha = (0.30f + 0.10f * sin(clock * 5.3f)) * bloom
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ScottsTechXColors.BlueGlow.copy(alpha = gAlpha),
                                    Color.Transparent,
                                ),
                                center = androidx.compose.ui.geometry.Offset(cx, cy),
                                radius = baseR * 1.25f,
                            ),
                            radius = baseR * 1.25f,
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                        )
                    }

                    // fusion impact flash
                    if (flash > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFDCEEFF).copy(alpha = 0.85f * flash),
                                    ScottsTechXColors.BlueGlow.copy(alpha = 0.35f * flash),
                                    Color.Transparent,
                                ),
                                center = androidx.compose.ui.geometry.Offset(cx, cy),
                                radius = baseR * 1.05f,
                            ),
                            radius = baseR * 1.05f,
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                        )
                    }

                    // impact rings + ignition ripples
                    for (rs in listOf(ring1, ring2, ripple)) {
                        if (rs > 0f && rs < 1f) {
                            drawCircle(
                                color = ScottsTechXColors.BlueGlow.copy(alpha = (1f - rs) * 0.5f * (1f - exit)),
                                radius = baseR * (0.35f + rs * 1.15f),
                                center = androidx.compose.ui.geometry.Offset(cx, cy),
                                style = Stroke(width = 2f + (1f - rs) * 12f),
                            )
                        }
                    }

                    // twinkling spark field (from the impact onward)
                    val sparkMaster = stage(clock, 1.80f, 2.10f) * (1f - exit)
                    if (sparkMaster > 0f) {
                        for (sp in sparks) {
                            val tw = 0.35f + 0.65f * sin(clock * 7.4f + sp.phase * 6.28f)
                            drawCircle(
                                color = (if (sp.blue) Color(0xFF8FC4FF) else Color(0xFFEAF4FF))
                                    .copy(alpha = sparkMaster * tw * 0.85f),
                                radius = sp.r,
                                center = androidx.compose.ui.geometry.Offset(
                                    size.width * sp.x, size.height * sp.y,
                                ),
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // offscreen letter layer — the SrcIn shimmer lights only the
            // letters; breathing + zoom move the whole STX as one unit.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        scaleX = emblemScale
                        scaleY = emblemScale
                        alpha = emblemAlpha
                    },
            ) {
                // S — scatters left, springs back
                Image(
                    painter = painterResource(R.drawable.splash_s),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = -size.width * 0.55f * sc
                            translationY = size.height * 0.22f * sc
                            rotationZ = -18f * sc
                            alpha = letterAlpha
                        },
                )
                // T — scatters up
                Image(
                    painter = painterResource(R.drawable.splash_t),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationY = -size.height * 0.85f * sc
                            translationX = size.width * 0.05f * sc
                            rotationZ = 9f * sc
                            alpha = letterAlpha
                        },
                )
                // X — scatters right with a stretch-spin
                Image(
                    painter = painterResource(R.drawable.splash_x),
                    contentDescription = "ScottsTechX",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = size.width * 0.70f * sc
                            translationY = -size.height * 0.30f * sc
                            rotationZ = 26f * sc
                            scaleX = 1f + 0.55f * sc.coerceAtLeast(0f)
                            alpha = letterAlpha
                        },
                )

                // chrome shimmer beams — drawn on top inside the offscreen
                // layer, SrcIn keeps them inside the letter metal only.
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            for (f in shimmerPasses) {
                                if (f <= 0f || f >= 1f) continue
                                val sweep = easeOut(f) * (1f - exit)
                                val cx = -size.width * 0.3f + size.width * 1.6f * sweep
                                val beamW = size.width * 0.30f
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        0.5f to Color(0xFFD8ECFF).copy(alpha = 0.85f),
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
