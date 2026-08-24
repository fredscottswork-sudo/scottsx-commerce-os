package com.scottsx.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Procedural "cinematic technology" background. Without internet
 * access for stock photography, we paint a deep-navy gradient with
 * animated purple glow orbs and a subtle radial vignette. The
 * silhouette hint at the bottom is a generalized "modern
 * environment" suggestion built from soft gradients.
 *
 * The brief allows placeholders as long as dimensions and styling
 * are correct. This is the placeholder.
 */
@Composable
fun CinematicBackground(
    modifier: Modifier = Modifier,
    showVignette: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "bg")
    val pulse1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse1",
    )
    val pulse2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse2",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBackgroundBase()
            drawBlueGlow(pulse1, this)
            drawBlueGlow(pulse2, this, xOffset = 0.7f, yOffset = 0.6f, radius = 0.55f)
            drawDistantHorizon()
            if (showVignette) drawVignette()
            drawSubtleStars()
        }
    }
}

private fun DrawScope.drawBackgroundBase() {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0A0719),
                Color(0xFF1A0938),
                Color(0xFF1F0E47),
                Color(0xFF0A0719),
                Color(0xFF050711),
            ),
        ),
        size = size,
    )
}

private fun DrawScope.drawBlueGlow(
    progress: Float,
    @Suppress("UNUSED_PARAMETER") ignore: DrawScope,
    xOffset: Float = 0.35f,
    yOffset: Float = 0.42f,
    radius: Float = 0.5f,
) {
    val cx = size.width * xOffset
    val cy = size.height * yOffset
    val r = size.minDimension * radius
    val alpha = (0.25f + 0.15f * progress).coerceAtMost(0.45f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ScottsTechXColors.BluePrimaryLight.copy(alpha = alpha),
                ScottsTechXColors.BluePrimary.copy(alpha = alpha * 0.6f),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
}

private fun DrawScope.drawDistantHorizon() {
    // Suggest a distant city-skyline band at the lower third.
    val horizonY = size.height * 0.62f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A0938).copy(alpha = 0.0f),
                Color(0xFF1A0938).copy(alpha = 0.5f),
                Color(0xFF050711).copy(alpha = 0.95f),
            ),
            startY = horizonY,
            endY = size.height,
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(size.width, size.height - horizonY),
    )
}

private fun DrawScope.drawVignette() {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xCC000000),
            ),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.85f,
        ),
        size = size,
    )
}

private fun DrawScope.drawSubtleStars() {
    // 24 deterministic "stars" / specks of light.
    val r = java.util.Random(0xC055F4L)
    repeat(28) {
        val x = r.nextFloat() * size.width
        val y = r.nextFloat() * size.height * 0.6f
        val dotRadius = 0.6f + r.nextFloat() * 1.4f
        drawCircle(
            color = Color.White.copy(alpha = 0.10f + r.nextFloat() * 0.18f),
            radius = dotRadius,
            center = Offset(x, y),
        )
    }
}

