package com.scottsx.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay

/**
 * Animated mosaic of the four Uganda-market photos for Slide 2
 * ("Connect. Trade. Grow.").
 *
 * Layout
 * ------
 *   ┌─────────┬─────────┐
 *   │  A      │   B     │
 *   │ bananas │ crafts  │
 *   ├─────────┼─────────┤
 *   │  C      │   D     │
 *   │ market  │ textile │
 *   └─────────┴─────────┘
 *
 * Each tile enters with a distinct animation:
 *   * A — scale + rotation, fades in from top-left
 *   * B — slides in from the right with a slight overshoot
 *   * C — slides in from the left with a slight rotation
 *   * D — rises up from the bottom + scale
 *
 * On top of the grid, glowing connector lines pulse between the
 * four tiles to suggest a network — very on-brand for "Connect".
 */
@Composable
fun PhotoMosaicBackground(modifier: Modifier = Modifier) {
    // Subtle pulsing glow colour for the connector lines.
    val infinite = rememberInfiniteTransition(label = "mosaic")
    val pulse by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mosaic-pulse",
    )
    val rotation by infinite.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mosaic-rotation",
    )

    // Entrance triggers — staggered so the tiles pop in one at a time.
    val tileA = remember { Animatable(0f) }
    val tileB = remember { Animatable(0f) }
    val tileC = remember { Animatable(0f) }
    val tileD = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        tileA.animateTo(1f, tween(700, easing = EaseOutBack))
        delay(120)
        tileB.animateTo(1f, tween(700, easing = EaseOutBack))
        delay(120)
        tileC.animateTo(1f, tween(700, easing = EaseOutBack))
        delay(120)
        tileD.animateTo(1f, tween(700, easing = EaseOutBack))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020617),
                        Color(0xFF0B1120),
                        Color(0xFF0F172A),
                    ),
                ),
            ),
    ) {
        // Animated connector lines drawn in a Box behind the photos.
        ConnectorNetwork(
            pulse = pulse,
            rotation = rotation,
            modifier = Modifier.fillMaxSize(),
        )

        // The 2x2 mosaic.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AnimatedPhotoTile(
                    imageRes = R.drawable.wa_slide2_a,
                    anim = tileA,
                    entryStyle = EntryStyle.TopLeft,
                    caption = "FRESH HARVEST",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                AnimatedPhotoTile(
                    imageRes = R.drawable.wa_slide2_b,
                    anim = tileB,
                    entryStyle = EntryStyle.Right,
                    caption = "LOCAL CRAFTS",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AnimatedPhotoTile(
                    imageRes = R.drawable.wa_slide2_c,
                    anim = tileC,
                    entryStyle = EntryStyle.Left,
                    caption = "MARKET DAY",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                AnimatedPhotoTile(
                    imageRes = R.drawable.wa_slide2_d,
                    anim = tileD,
                    entryStyle = EntryStyle.Bottom,
                    caption = "MAKERS",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

private enum class EntryStyle { TopLeft, Right, Left, Bottom }

@Composable
private fun AnimatedPhotoTile(
    imageRes: Int,
    anim: Animatable<Float, *>,
    entryStyle: EntryStyle,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val v by anim.asStateBridge()
    val (offsetX, offsetY, rotation, scale) = when (entryStyle) {
        EntryStyle.TopLeft -> Quad(-160f, -160f, -8f, 0.85f)
        EntryStyle.Right    -> Quad(180f, 0f, 6f, 0.85f)
        EntryStyle.Left     -> Quad(-180f, 0f, -6f, 0.85f)
        EntryStyle.Bottom   -> Quad(0f, 220f, 4f, 0.85f)
    }
    val easeV: Float by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { anim.value }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ScottsTechXColors.BluePrimaryDark,
                        ScottsTechXColors.BluePrimary,
                    ),
                ),
            ),
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = caption,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(0.92f + 0.08f * easeV)
                .rotate(rotation * (1f - easeV))
                .offset(x = (offsetX * (1f - easeV)).dp, y = (offsetY * (1f - easeV)).dp),
        )
        // Bottom gradient + caption.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x00000000),
                            Color(0xCC050711),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            Text(
                text = caption,
                color = ScottsTechXColors.OnDark,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
            )
        }
    }
}

private data class Quad(val x: Float, val y: Float, val r: Float, val s: Float)

@Composable
private fun Animatable<Float, *>.asStateBridge(): androidx.compose.runtime.State<Float> =
    androidx.compose.runtime.derivedStateOf { value }

@Composable
private fun ConnectorNetwork(
    pulse: Float,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Center points of the four tiles.
        val tl = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.27f)
        val tr = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.27f)
        val bl = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.73f)
        val br = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.73f)
        val center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f)

        val color = ScottsTechXColors.BluePrimaryLight.copy(alpha = 0.55f * pulse)

        listOf(
            tl to tr,
            tr to br,
            br to bl,
            bl to tl,
            tl to br,
            tr to bl,
            tl to center,
            tr to center,
            bl to center,
            br to center,
        ).forEach { (a, b) ->
            drawLine(
                color = color,
                start = a,
                end = b,
                strokeWidth = 1.4f,
            )
        }
        // Pulsing center node.
        drawCircle(
            color = ScottsTechXColors.BluePrimaryLight,
            radius = 6f + 4f * pulse,
            center = center,
        )
    }
}