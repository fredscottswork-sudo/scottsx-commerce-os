package com.scottsx.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * KineticBackground — a pure-Canvas animated ambient stage. Zero
 * decoders, zero native views, ~60fps everywhere: drifting radial
 * color fields in brand blue + a slow particle starfield. Replaces
 * the fragile video backgrounds on first-run screens (ExoPlayer
 * prepared big clips on the main thread and left some phones with
 * frozen, unresponsive onboarding screens).
 */
@Composable
fun KineticBackground(
    modifier: Modifier = Modifier,
    accent: Color = ScottsTechXColors.BlueGlow,
) {
    val transition = rememberInfiniteTransition(label = "kinetic-bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kinetic-drift",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "kinetic-orbit",
    )
    val orbs = remember {
        List(5) { i ->
            Triple(
                ((i * 29) % 100) / 100f,
                ((i * 47) % 100) / 100f,
                0.9f + (i % 3) * 0.45f,
            )
        }
    }
    val stars = remember {
        List(40) { i ->
            Triple(
                ((i * 53) % 100) / 100f,
                ((i * 31) % 100) / 100f,
                1f + (i % 3) * 0.8f,
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Base midnight wash
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF050713), Color(0xFF070C1D), Color(0xFF04060E)),
            ),
        )
        // Drifting color orbs
        orbs.forEachIndexed { i, (ox, oy, sc) ->
            val cx = w * (0.5f + 0.42f * ((ox - 0.5f) * 2f) * cos(spin * (0.6f + i * 0.13f) + i))
            val cy = h * (0.5f + 0.40f * ((oy - 0.5f) * 2f) * sin(spin * (0.7f + i * 0.11f) + i * 2))
            val r = w * 0.42f * sc * (0.85f + 0.25f * t)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.16f + 0.07f * sin(t * 6.28f + i)),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        // Slow starfield shimmer
        val tw = 0.4f + 0.6f * t
        for ((sx, sy, sr) in stars) {
            drawCircle(
                color = Color(0xFFA9CCFF).copy(alpha = 0.25f * tw + 0.1f),
                radius = sr,
                center = Offset(w * sx, h * sy),
            )
        }
    }
}
