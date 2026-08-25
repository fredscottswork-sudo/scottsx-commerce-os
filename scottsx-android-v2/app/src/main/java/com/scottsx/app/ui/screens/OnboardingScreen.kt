package com.scottsx.app.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.ui.components.PageIndicator
import com.scottsx.app.ui.components.PhotoMosaicBackground
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.VideoBackground
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Onboarding flow — three slides.
 *
 *   Slide 1 — "Build the future." — looping video of a tech-style
 *             globe (the user's supplied MP4).
 *   Slide 2 — "Connect. Trade. Grow." — animated mosaic of the
 *             four Uganda-market photos supplied by the user.
 *   Slide 3 — "Your move. Your market." — looping video of an
 *             animated map with avatars (the user's MP4).
 *
 * Each slide darkens the background with a brand-blue overlay so
 * the white text remains highly readable.
 */
@Composable
fun OnboardingFlow(
    onFinish: () -> Unit,
) {
    var page by remember { mutableStateOf(0) }

    val slides = listOf(
        OnboardingSlide(
            backgroundKind = BackgroundKind.Video(R.raw.onboard1),
            eyebrow = "THE FUTURE OF UGANDA",
            headlineLines = listOf("Build", "the future."),
            description = "From Kampala to Gulu, from Mbarara to Mbale — ScottsTechX is the network that puts Uganda's buyers and sellers on the map.",
            cta = "Continue",
        ),
        OnboardingSlide(
            backgroundKind = BackgroundKind.PhotoMosaic,
            eyebrow = "",
            headlineLines = listOf("Connect.", "Trade.", "Grow."),
            description = "Real Ugandan goods, real Ugandan makers — discover them all in one trusted place.",
            cta = "Next",
        ),
        OnboardingSlide(
            backgroundKind = BackgroundKind.Video(R.raw.onboard3),
            eyebrow = "YOU'RE READY",
            headlineLines = listOf("Your move.", "Your market."),
            description = "Sign in to pick up where you left off, or create a free account and start something new today.",
            cta = "Start",
        ),
    )

    Box(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.BackgroundDark)) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (fadeIn(tween(500)) togetherWith fadeOut(tween(500)))
            },
            label = "onboarding-bg",
        ) { currentPage ->
            when (val bg = slides[currentPage].backgroundKind) {
                is BackgroundKind.Video -> {
                    val ctx = LocalContext.current
                    val uri = Uri.parse(
                        "android.resource://" + ctx.packageName + "/" + bg.resId,
                    )
                    VideoBackground(videoUri = uri)
                }
                BackgroundKind.PhotoMosaic -> PhotoMosaicBackground()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x00000000),
                            Color(0x00000000),
                            Color(0xAA050711),
                            Color(0xEE050711),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(size = 44.dp)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Skip",
                    color = ScottsTechXColors.OnDark,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { onFinish() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val slide = slides[page]
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (slide.eyebrow.isNotEmpty()) {
                    Text(
                        text = slide.eyebrow,
                        color = ScottsTechXColors.BluePrimaryLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 4.sp,
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                }
                slide.headlineLines.forEachIndexed { i, line ->
                    if (i > 0) Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line,
                        color = ScottsTechXColors.OnDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 52.sp,
                        lineHeight = 56.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1.0).sp,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = slide.description,
                    color = ScottsTechXColors.OnDarkSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PageIndicator(activeIndex = page, size = slides.size)
            }

            Spacer(modifier = Modifier.height(28.dp))

            PrimaryButton(
                text = slide.cta,
                onClick = {
                    if (page < slides.lastIndex) {
                        page += 1
                    } else {
                        onFinish()
                    }
                },
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private sealed class BackgroundKind {
    data class Video(val resId: Int) : BackgroundKind()
    object PhotoMosaic : BackgroundKind()
}

private data class OnboardingSlide(
    val backgroundKind: BackgroundKind,
    val eyebrow: String,
    val headlineLines: List<String>,
    val description: String,
    val cta: String,
)

/**
 * Small brand mark — circular logo + "ScottsTechX" wordmark.
 */
@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    val scale = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            tween(durationMillis = 600, easing = FastOutSlowInEasing),
        )
    }
    val infinite = rememberInfiniteTransition(label = "brand-mark")
    val wobble by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "brand-mark-wobble",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(size).scale(scale.value),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "ScottsTechX logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .rotate(wobble)
                    .clip(CircleShape)
                    .padding(2.dp),
            )
        }
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            text = "ScottsTechX",
            color = ScottsTechXColors.OnDark,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            letterSpacing = 1.sp,
        )
    }
}