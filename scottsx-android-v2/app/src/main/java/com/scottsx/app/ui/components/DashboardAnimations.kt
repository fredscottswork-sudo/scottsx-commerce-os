package com.scottsx.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import androidx.compose.animation.core.animateFloat

/**
 * Shared animation + feed-state primitives for the buyer and seller
 * dashboards. Only uses Compose animation-core APIs that already ship
 * with this project (BOM 2023.10.01 / compose 1.5.x).
 */

/**
 * Staggered entrance: fades in and rises 18dp. [index] staggers the
 * delay so sections cascade in top-to-bottom when the feed loads.
 */
@Composable
fun Reveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val a by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 520, delayMillis = index * 90, easing = FastOutSlowInEasing),
        label = "reveal-alpha-$index",
    )
    val dy by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = tween(durationMillis = 520, delayMillis = index * 90, easing = FastOutSlowInEasing),
        label = "reveal-dy-$index",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = a
            translationY = dy
        },
    ) {
        content()
    }
}

/**
 * Shimmer skeleton surface — a diagonal highlight sweeping left→right
 * forever. Use inside loading states instead of static grey boxes.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    base: Color,
    highlight: Color,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-shift",
    )
    Canvas(modifier = modifier.clip(shape)) {
        val w = size.width
        val h = size.height
        val start = Offset(-w + shift * 3f * w, 0f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = start,
                end = Offset(start.x + w, h),
            ),
        )
    }
}

/** Softly pulsing dot — marks a section as live/real-time. */
@Composable
fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier.size(8.dp),
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-phase",
    )
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = 1f + 0.35f * phase
            scaleY = 1f + 0.35f * phase
            alpha = 1f - 0.45f * phase
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * Animated counter: counts from the previously displayed value to
 * [target] (no jump on refresh). The [format] lambda turns the raw
 * long into the label (e.g. UGX formatting).
 */
@Composable
fun AnimatedNumber(
    target: Long,
    format: (Long) -> String,
    modifier: Modifier = Modifier,
    durationMs: Int = 900,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    var display by remember { mutableStateOf(0L) }
    LaunchedEffect(target) {
        if (target == display) return@LaunchedEffect
        val from = display
        if (from == 0L && target == 0L) return@LaunchedEffect
        animate(
            initialValue = from.toFloat(),
            targetValue = target.toFloat(),
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        ) { value, _ ->
            display = value.roundToLong()
        }
    }
    Text(
        text = format(display),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )
}

/**
 * Feed error state — real network failures only. Includes a Retry
 * button that re-runs the fetch; no demo data is ever shown here.
 */
@Composable
fun FeedErrorCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    surface: Color,
    foreground: Color,
    secondary: Color,
    accent: Color,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.CloudOff,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Can't reach the marketplace",
            color = foreground,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your connection dropped or the server is taking a moment. Nothing was lost — try again.",
            color = secondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent)
                .clickable { onRetry() }
                .padding(horizontal = 26.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Retry",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Feed empty state — the backend genuinely has no matching products.
 */
@Composable
fun FeedEmptyCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    surface: Color,
    foreground: Color,
    secondary: Color,
    accent: Color,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.Storefront,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = foreground,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            color = secondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent)
                .clickable { onAction() }
                .padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionLabel,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
