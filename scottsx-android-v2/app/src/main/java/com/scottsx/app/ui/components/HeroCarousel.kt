package com.scottsx.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.BannerBackground
import com.scottsx.app.data.domain.HeroBanner
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay

private const val AUTOROTATE_MS = 4500L

/**
 * Auto-rotating hero carousel. Cycles through [banners] every
 * ~4.5 seconds. The [onCtaClick] callback fires when the CTA
 * pill on the currently visible banner is tapped.
 *
 * The carousel card uses a sealed [BannerBackground] enum to
 * pick a base gradient — BluePurple, DarkNavy, GreenTeal, or
 * Sunset — and overlays a procedural collage of floating dots
 * + a dashed wave streak for visual depth.
 */
@Composable
fun HeroCarousel(
    banners: List<HeroBanner>,
    onCtaClick: (HeroBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banners.isEmpty()) return
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(currentIndex) {
        delay(AUTOROTATE_MS)
        currentIndex = (currentIndex + 1) % banners.size
    }

    val backgroundColors = when (banners[currentIndex].background) {
        BannerBackground.BluePurple -> listOf(
            ScottsTechXColors.BluePrimaryDark,
            ScottsTechXColors.BluePrimary,
        )
        BannerBackground.DarkNavy -> listOf(
            Color(0xFF0C1220),
            Color(0xFF111827),
        )
        BannerBackground.GreenTeal -> listOf(
            Color(0xFF064E3B),
            Color(0xFF047857),
        )
        BannerBackground.Sunset -> listOf(
            Color(0xFFB45309),
            Color(0xFFEA580C),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(colors = backgroundColors),
            ),
    ) {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                (fadeIn(tween(450)) togetherWith fadeOut(tween(450)))
            },
            label = "hero-anim",
        ) { idx ->
            val banner = banners[idx]
            HeroSlide(
                banner = banner,
                onCtaClick = { onCtaClick(banner) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Pagination dots overlay (inside Box so we can use .align)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            banners.indices.forEach { i ->
                val active = i == currentIndex
                Box(
                    modifier = Modifier
                        .size(if (active) 22.dp else 7.dp, 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) Color.White else Color.White.copy(alpha = 0.45f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun HeroSlide(
    banner: HeroBanner,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        HeroCollage()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = banner.supportingText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = banner.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = banner.subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                maxLines = 2,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable { onCtaClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = banner.cta,
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = ScottsTechXColors.BluePrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCollage() {
    val infiniteTransition = rememberInfiniteTransition(label = "hero-decor")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hero-phase",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (i in 0..5) {
            val cx = w * (0.55f + 0.08f * i)
            val cy = h * (0.18f + 0.12f * ((i + phase * 6f) % 5f))
            drawCircle(
                color = Color.White.copy(alpha = (0.18f - i * 0.018f).coerceAtLeast(0.02f)),
                radius = 18f - i * 2f,
                center = Offset(cx, cy),
            )
        }
        drawLine(
            color = Color.White.copy(alpha = 0.18f),
            start = Offset(0f, h * 0.7f),
            end = Offset(w, h * 0.85f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 14f)),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = h * 0.55f,
            center = Offset(w * 1.05f, h * 0.5f),
            style = Stroke(width = 1.4f),
        )
    }
}
