package com.scottsx.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay

/**
 * ScottsTechX brand lockup — uses the actual `logo.png` asset that
 * the user supplied. The lockup is circular (clip = CircleShape) and
 * animates:
 *
 *   * Entrance — fades in + scales from 0 with overshoot spring.
 *   * Continuous — the monogram rotates gently back and forth, and
 *     a soft blue glow ring pulses around it.
 *
 * If [showWordmark] is true, "ScottsTechX" is rendered below in a
 * staggered typewriter-style reveal.
 *
 * If [showTagline] is true, the lines "ENTERPRISES (U) LTD" and
 * "INNOVATE. INTEGRATE. ELEVATE." fade in below the wordmark.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    monogramSize: Dp = 96.dp,
    showWordmark: Boolean = true,
    showTagline: Boolean = true,
    autoPlay: Boolean = true,
    circular: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedLogoCircle(
            size = monogramSize,
            autoPlay = autoPlay,
            circular = circular,
        )

        if (showWordmark) {
            AnimatedWordmark(autoPlay = autoPlay)
        }

        if (showTagline) {
            AnimatedTagline(autoPlay = autoPlay)
        }
    }
}

// ============================================================================
// LOGO MONOGRAM — actual logo PNG, animated
// ============================================================================
@Composable
private fun AnimatedLogoCircle(
    size: Dp,
    autoPlay: Boolean,
    circular: Boolean,
) {
    val scale = remember { Animatable(if (autoPlay) 0f else 1f) }
    val alpha = remember { Animatable(if (autoPlay) 0f else 1f) }
    LaunchedEffect(autoPlay) {
        if (autoPlay) {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            alpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    // Continuous gentle wobble + glow pulse.
    val infinite = rememberInfiniteTransition(label = "logo-infinite")
    val wobble by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobble",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale.value),
        contentAlignment = Alignment.Center,
    ) {
        // Soft glow ring around the logo
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(size),
        ) {
            val r = this.size.minDimension / 2f
            drawCircle(
                color = ScottsTechXColors.BlueGlow,
                radius = r * pulse,
                center = androidx.compose.ui.geometry.Offset(r, r),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
            )
        }

        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "ScottsTechX logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .rotate(wobble)
                .clip(if (circular) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(percent = 18))
                .padding(2.dp),
        )
    }
}

// ============================================================================
// WORDMARK
// ============================================================================
@Composable
private fun AnimatedWordmark(autoPlay: Boolean) {
    val letters = remember { "ScottsTechX".toList() }
    val alphaAnims = remember {
        List(letters.size) { Animatable(if (autoPlay) 0f else 1f) }
    }
    val offsetAnims = remember {
        List(letters.size) { Animatable(if (autoPlay) 14f else 0f) }
    }
    LaunchedEffect(autoPlay) {
        if (autoPlay) {
            letters.forEachIndexed { i, _ ->
                delay(i * 35L)
            }
            letters.forEachIndexed { i, _ ->
                alphaAnims[i].animateTo(1f, tween(360, easing = EaseInOutCubic))
                offsetAnims[i].animateTo(0f, tween(360, easing = EaseInOutCubic))
            }
        }
    }
    Box(
        modifier = Modifier.height(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            letters.forEachIndexed { i, ch ->
                val a by alphaAnims[i].valueAsState()
                val yOff by offsetAnims[i].valueAsState()
                Box(
                    modifier = Modifier
                        .width(13.dp)
                        .height(28.dp),
                ) {
                    Box(
                        modifier = Modifier.offset(y = yOff.dp),
                    ) {
                        Text(
                            text = ch.toString(),
                            color = ScottsTechXColors.OnDark.copy(alpha = a),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = 2.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Animatable<Float, *>.valueAsState(): androidx.compose.runtime.State<Float> =
    androidx.compose.runtime.derivedStateOf { value }

// ============================================================================
// TAGLINE
// ============================================================================
@Composable
private fun AnimatedTagline(autoPlay: Boolean) {
    val line1 = remember { Animatable(if (autoPlay) 0f else 1f) }
    val line2 = remember { Animatable(if (autoPlay) 0f else 1f) }
    LaunchedEffect(autoPlay) {
        if (autoPlay) {
            delay(900)
            line1.animateTo(1f, tween(700, easing = EaseInOutCubic))
            delay(400)
            line2.animateTo(1f, tween(700, easing = EaseInOutCubic))
        }
    }
    val accent by rememberInfiniteTransition(label = "accent-sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "accent",
    )
    val l1 by line1.valueAsState()
    val l2 by line2.valueAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ENTERPRISES  (U)  LTD",
                color = ScottsTechXColors.OnDark.copy(alpha = l1),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 4.sp,
            )
        }
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "INNOVATE.  INTEGRATE.  ELEVATE.",
                color = ScottsTechXColors.AccentLink.copy(
                    alpha = (0.55f + 0.45f * accent) * l2,
                ),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 3.sp,
            )
        }
    }
}