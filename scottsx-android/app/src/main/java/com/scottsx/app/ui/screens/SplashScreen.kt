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
 * SPLASH — the cinematic STX opening.
 *
 * The official monogram is pre-sliced into three perfectly aligned
 * letter layers (splash_s / splash_t / splash_x, all on one identical
 * canvas) so each letter can be choreographed on its own:
 *
 *   0.00–0.12  deep-space bloom fades up behind the stage
 *   0.06–0.30  S slams in from the left   (overshoot + un-rotate)
 *   0.14–0.38  T drops in from the top    (overshoot + un-rotate)
 *   0.22–0.52  X slashes in from the right (stretch + spin-down)
 *   0.49–0.60  IMPACT flash + two expanding energy rings
 *   0.55–0.95  chrome shimmer beams sweep the assembled STX, twice
 *   0.50–1.00  emblem breathes, spark field twinkles
 *   exit       emblem zooms through the camera and fades → app
 *
 * A single frame clock drives everything, so a tap can instantly skip
 * to the exit phase without cancelling any coroutine mid-animation.
 * The network warm-up still races this window — the splash NEVER
 * waits on the network.
 */

private const val INTRO_MS = 3000L
private const val EXIT_MS = 720L
private const val MONOGRAM_ASPECT = 3.1877f   // W/H of the shared letter canvas

/** Normalised 0..1 progress of the stage window (a to b) inside the global clock. */
private fun stage(t: Float, a: Float, b: Float): Float =
    ((t - a) / (b - a)).coerceIn(0f, 1f)

/** 1 - (1-x)^3 — cinematic ease-out. */
private fun easeOut(x: Float): Float {
    val d = 1f - x.coerceIn(0f, 1f)
    return 1f - d * d * d
}

/** OvershootInterpolator — lands past 1 then settles (tension 2.4). */
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
    var t by remember { mutableFloatStateOf(0f) }
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
        // warm window: pull catalog + cart while the brand plays
        com.scottsx.app.data.LiveMarketplace.warm()
        com.scottsx.app.data.CartStore.warm()
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                t = ((now - start) / (INTRO_MS * 1_000_000f)).coerceAtMost(1f)
            }
            if (t >= 1f || skipped) break
        }
        start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                exit = ((now - start) / (EXIT_MS * 1_000_000f)).coerceAtMost(1f)
            }
            if (exit >= 1f) break
        }
        onFinished()
    }

    // ── letter choreography (derived from the master clock) ─────────────
    val sIn = stage(t, 0.06f, 0.30f)          // S flight
    val tIn = stage(t, 0.14f, 0.38f)          // T drop
    val xIn = stage(t, 0.22f, 0.52f)          // X slash
    val sEase = easeOut(sIn); val tEase = easeOut(tIn); val xEase = easeOut(xIn)
    val sAlpha = easeOut(stage(t, 0.06f, 0.20f))
    val tAlpha = easeOut(stage(t, 0.14f, 0.30f))
    val xAlpha = easeOut(stage(t, 0.22f, 0.38f))

    // impact flash, rings, shimmer, breathing
    val flashF = stage(t, 0.49f, 0.60f)
    val flash = if (flashF < 0.22f) flashF / 0.22f else 1f - (flashF - 0.22f) / 0.78f
    val ring1 = stage(t, 0.50f, 0.80f)
    val ring2 = stage(t, 0.56f, 0.90f)
    val breathe = if (t > 0.55f) 1f + 0.016f * sin((t - 0.55f) * 4f * PI.toFloat()) * (1f - exit) else 1f
    val pop = 0.90f + 0.10f * overshoot(stage(t, 0.46f, 0.60f))
    val bloom = easeOut(stage(t, 0.0f, 0.14f)) * (1f - exit)
    val emblemScale = breathe * pop * (1f + 4.2f * exit)
    val emblemAlpha = (1f - exit) * (1f - exit)

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
            // shimmer + spark + rings draw *behind* the letters but above
            // the aurora backdrop; this box is deliberately NOT clipped so
            // the energy rings can overflow the letter frame.
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(MONOGRAM_ASPECT)
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val baseR = min(size.width, size.height)

                    // deep-space bloom engine glow
                    if (bloom > 0f) {
                        val gAlpha = (0.32f + 0.10f * sin(t * 9f)) * bloom
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

                    // impact flash — white-blue radial, dies fast
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

                    // two expanding energy rings
                    for ((rs, wMul) in listOf(ring1 to 1f, ring2 to 0.6f)) {
                        if (rs > 0f && rs < 1f) {
                            drawCircle(
                                color = ScottsTechXColors.BlueGlow.copy(alpha = (1f - rs) * 0.5f * (1f - exit)),
                                radius = baseR * (0.35f + rs * 1.15f),
                                center = androidx.compose.ui.geometry.Offset(cx, cy),
                                style = Stroke(width = (2f + (1f - rs) * 14f) * wMul),
                            )
                        }
                    }

                    // twinkling spark field (appears after the impact)
                    val sparkMaster = stage(t, 0.50f, 0.58f) * (1f - exit)
                    if (sparkMaster > 0f) {
                        for (sp in sparks) {
                            val tw = 0.35f + 0.65f * sin(t * 26f + sp.phase * 6.28f)
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
            // ── offscreen letter layer: the SrcIn shimmer only lights up
            // the letters themselves, never the background. Compositing
            // happens on one graphics layer, so breathing/zoom move the
            // whole STX as a unit. ──────────────────────────────────────
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
                // S — slams in from the left
                Image(
                    painter = painterResource(R.drawable.splash_s),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = -size.width * 0.55f * (1f - sEase)
                            translationY = size.height * 0.25f * (1f - sEase)
                            rotationZ = -16f * (1f - sEase)
                            val sc = 0.55f + 0.45f * (sEase + 0.18f * (overshoot(sIn) - sEase))
                            scaleX = sc; scaleY = sc
                            alpha = sAlpha
                        },
                )
                // T — drops in from the top
                Image(
                    painter = painterResource(R.drawable.splash_t),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationY = -size.height * 0.9f * (1f - tEase)
                            translationX = size.width * 0.06f * (1f - tEase)
                            rotationZ = 10f * (1f - tEase)
                            val sc = 0.60f + 0.40f * (tEase + 0.18f * (overshoot(tIn) - tEase))
                            scaleX = sc; scaleY = sc
                            alpha = tAlpha
                        },
                )
                // X — slashes in from the right with a stretch-spin
                Image(
                    painter = painterResource(R.drawable.splash_x),
                    contentDescription = "ScottsTechX",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = size.width * 0.75f * (1f - xEase)
                            translationY = -size.height * 0.35f * (1f - xEase)
                            rotationZ = 26f * (1f - xEase)
                            scaleX = (1f + 0.9f * (1f - xEase)) * (0.7f + 0.3f * xEase)
                            scaleY = 0.7f + 0.3f * xEase
                            alpha = xAlpha
                        },
                )

                // chrome shimmer beams — twice across the assembled STX;
                // a sibling drawn ON TOP of the letters inside the same
                // offscreen layer, so SrcIn lights up only the letters.
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            for ((a, b) in listOf(0.55f to 0.72f, 0.78f to 0.95f)) {
                                val f = stage(t, a, b)
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
