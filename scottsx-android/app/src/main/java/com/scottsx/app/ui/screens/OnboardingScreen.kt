@file:OptIn(ExperimentalFoundationApi::class)

package com.scottsx.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.ui.components.LoopingVideo
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * The three intro screens shown before sign-in.
 *
 *   1. a full-bleed video (brand intro)
 *   2. four category pictures
 *   3. a second video (nearby stores / delivery)
 *
 * then the user continues to the login section, where they pick buyer or seller.
 *
 * Every page keeps the same layout skeleton — media behind, a dark scrim, copy
 * and controls in front — so paging feels like one continuous surface rather
 * than three unrelated screens.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
) {
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.DarkBackground),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> OnboardVideoPage(
                    resId = R.raw.welcome_intro,
                    fallbackDrawable = R.drawable.brand_lockup,
                    title = "Welcome to ScottsTechX",
                    body = "Uganda's marketplace for verified sellers and real buyers. Innovate. Integrate. Elevate.",
                )
                1 -> OnboardPicturesPage()
                else -> OnboardVideoPage(
                    resId = R.raw.welcome_nearby,
                    fallbackDrawable = R.drawable.onb_market,
                    title = "Shops near you",
                    body = "See stores around you on the map, sorted by distance as you move, and chat with sellers before you buy.",
                )
            }
        }

        // ---- Controls, floating above the pager -------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(count = pageCount, current = pagerState.currentPage)
            Spacer(Modifier.height(18.dp))

            val isLast = pagerState.currentPage == pageCount - 1
            Button(
                onClick = {
                    if (isLast) {
                        onFinished()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScottsTechXColors.BluePrimary,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    if (isLast) "Get started" else "Next",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // "Skip" disappears on the last page, where it would duplicate
            // the primary button. Kept in the layout as an invisible spacer
            // so the button above never shifts position between pages.
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onFinished,
                modifier = Modifier.alpha(if (isLast) 0f else 1f),
                enabled = !isLast,
            ) {
                Text(
                    "Skip",
                    color = ScottsTechXColors.DarkOnSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** A media page: video behind, scrim, headline and body in front. */
@Composable
private fun OnboardVideoPage(
    resId: Int,
    fallbackDrawable: Int,
    title: String,
    body: String,
) {
    // If the decoder refuses the clip we swap to a still image rather than
    // showing the user a black hole on the very first screen.
    var videoFailed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (videoFailed) {
            Image(
                painter = painterResource(fallbackDrawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            LoopingVideo(
                resId = resId,
                modifier = Modifier.fillMaxSize(),
                onFailed = { videoFailed = true },
            )
        }

        ScrimAndCopy(title = title, body = body)
    }
}

/** The four-picture page: a 2x2 grid that fades and rises into place. */
@Composable
private fun OnboardPicturesPage() {
    val tiles = listOf(
        R.drawable.onb_electronics to "Electronics",
        R.drawable.onb_fashion to "Fashion",
        R.drawable.onb_home to "Home",
        R.drawable.onb_market to "Local shops",
    )

    // Drives the staggered entrance so the four tiles arrive one after another
    // instead of snapping in together.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.DarkBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Everything, in one place",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Phones, fashion, home essentials and the shops down the road — all from sellers we have approved.",
                color = ScottsTechXColors.DarkOnSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(22.dp))

            // Two rows of two. A plain Column/Row grid is used rather than
            // LazyVerticalGrid because the count is fixed at four and nothing
            // here ever scrolls.
            for (rowIndex in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (colIndex in 0 until 2) {
                        val index = rowIndex * 2 + colIndex
                        val (drawable, label) = tiles[index]
                        AnimatedVisibility(
                            visible = shown,
                            enter = fadeIn(animationSpec = tween(420, delayMillis = index * 110)) +
                                slideInVertically(
                                    animationSpec = tween(420, delayMillis = index * 110),
                                    initialOffsetY = { it / 4 },
                                ),
                            exit = fadeOut(),
                            modifier = Modifier.weight(1f),
                        ) {
                            PictureTile(drawable = drawable, label = label)
                        }
                    }
                }
                if (rowIndex == 0) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PictureTile(drawable: Int, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(16.dp))
            .background(ScottsTechXColors.DarkPanel),
    ) {
        Image(
            painter = painterResource(drawable),
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Bottom-up scrim keeps the caption legible over a bright photo.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xCC0E1420),
                    ),
                ),
        )
        Text(
            label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
    }
}

/** Shared scrim + copy block used by both video pages. */
@Composable
private fun ScrimAndCopy(title: String, body: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0x660E1420),
                    0.45f to Color(0x990E1420),
                    1f to Color(0xF20E1420),
                ),
            ),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(bottom = 160.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            color = ScottsTechXColors.DarkOnSecondary,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Start,
        )
    }
}

/** Animated page indicator — the active dot stretches into a pill. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until count) {
            val active = i == current
            val width by animateFloatAsState(
                targetValue = if (active) 26f else 8f,
                animationSpec = tween(260),
                label = "dotWidth",
            )
            Box(
                Modifier
                    .width(width.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) ScottsTechXColors.BluePrimary
                        else ScottsTechXColors.DarkOnSecondary.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}
