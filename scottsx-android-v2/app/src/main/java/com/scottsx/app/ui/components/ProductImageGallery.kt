package com.scottsx.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.ProductImage
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlin.math.abs

/**
 * Premium product image gallery.
 *
 * Shows:
 *  - Large main image (60% of viewport height, 1:1 aspect)
 *  - Thumbnail strip below (1 tap = switch)
 *  - Swipe left/right to navigate
 *  - Tap to open full-screen viewer
 *  - Page indicator (1/N) overlay
 *  - Smooth cross-fade animation between images
 *
 * Falls back gracefully when the product has only 1 image
 * (no thumbnails shown, no swipe zone active).
 */
@Composable
fun ProductImageGallery(
    images: List<ProductImage>,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        EmptyGallery(modifier)
        return
    }
    val safeImages = images
    var currentIndex by remember(safeImages) { mutableStateOf(0) }
    var fullscreenOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Main hero image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ScottsTechXColors.PanelInputLight)
                .clickable { fullscreenOpen = true },
        ) {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(280)) { it / 6 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 6 } + fadeOut(tween(280)))
                },
                label = "hero-image",
            ) { idx ->
                GalleryImage(safeImages[idx], fill = true)
            }
            // Page indicator overlay
            if (safeImages.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${currentIndex + 1}/${safeImages.size}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // Zoom hint
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .clickable { fullscreenOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ZoomIn,
                    contentDescription = "Zoom",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Swipe gesture zone (transparent overlay)
        if (safeImages.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(safeImages) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag < -50f && currentIndex < safeImages.lastIndex) currentIndex++
                                else if (drag > 50f && currentIndex > 0) currentIndex--
                                drag = 0f
                            },
                        ) { _, delta -> drag += delta }
                    },
            )
        }

        // Thumbnail strip
        if (safeImages.size > 1) {
            Spacer(Modifier.height(10.dp))
            val rowState = rememberLazyListState()
            LaunchedEffect(currentIndex) {
                rowState.animateScrollToItem(currentIndex)
            }
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                itemsIndexed(safeImages) { idx, img ->
                    val isActive = idx == currentIndex
                    val borderColor by animateColorAsState(
                        targetValue = if (isActive) ScottsTechXColors.BluePrimary else Color.Transparent,
                        animationSpec = tween(200),
                        label = "thumb-border",
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(borderColor, RoundedCornerShape(14.dp))
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ScottsTechXColors.PanelInputLight)
                            .clickable { currentIndex = idx },
                    ) {
                        GalleryImage(img, fill = true)
                    }
                }
            }
        }
    }

    if (fullscreenOpen) {
        FullscreenGalleryViewer(
            images = safeImages,
            initialIndex = currentIndex,
            onDismiss = { fullscreenOpen = false },
        )
    }
}

@Composable
private fun GalleryImage(image: ProductImage, fill: Boolean) {
    val mod = if (fill) Modifier.fillMaxSize() else Modifier
    ProductImage(
        imageKey = image.id,
        categoryLabel = image.alt.ifBlank { "ScottsTechX" },
        imageUrl = image.url.takeIf { it.isNotBlank() },
        modifier = mod,
    )
}

@Composable
private fun EmptyGallery(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ScottsTechXColors.PanelInputLight),
        contentAlignment = Alignment.Center,
    ) {
        Text("No image", color = ScottsTechXColors.OnLightSecondary, fontSize = 14.sp)
    }
}

/**
 * Full-screen image viewer with a close button + page indicator.
 * Triggered by tapping the hero image.
 */
@Composable
private fun FullscreenGalleryViewer(
    images: List<ProductImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableStateOf(initialIndex) }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(280)))
            },
            label = "fullscreen-image",
            modifier = Modifier.fillMaxSize(),
        ) { idx ->
            GalleryImage(images[idx], fill = true)
        }

        // Close button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // Swipe between images
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(images) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag < -40f && index < images.lastIndex) index++
                            else if (drag > 40f && index > 0) index--
                            drag = 0f
                        },
                    ) { _, delta -> drag += delta }
                },
        )

        // Page indicator
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "${index + 1} / ${images.size}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
