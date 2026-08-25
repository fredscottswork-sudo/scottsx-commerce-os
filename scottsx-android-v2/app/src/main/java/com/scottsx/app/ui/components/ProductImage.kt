package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Renders the real product image from [imageUrl] when available,
 * otherwise falls back to a gradient + category-initial card.
 *
 * Uses Coil for HTTP loading with a crossfade transition. The
 * fallback ensures the UI never goes blank even if the network
 * fails or the URL is broken.
 */
@Composable
fun ProductImage(
    imageKey: String,
    categoryLabel: String,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = remember(imageKey) {
        palettes[imageKey.hashCode().let { (it and 0x7FFFFFFF) % palettes.size }]
    }
    val ctx = LocalContext.current
    // Use the global Coil ImageLoader (configured in ScottsTechXApp
    // with 50MB disk cache + 25% RAM memory cache + 8s/15s timeouts).
    // Local Coil.imageLoader(ctx) instead of building a new one so
    // product photos share the same cache and OkHttp pool.
    val loader = remember { Coil.imageLoader(ctx) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = palette.gradient,
                    start = Offset(0f, 0f),
                    end = Offset(800f, 800f),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            // Load real image from the network (Unsplash, Firebase, etc.)
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Top-right accent dot
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.30f)),
            )
            // Bottom-left accent bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.35f)),
            )
            Text(
                text = categoryLabel.firstOrNull()?.uppercase() ?: "",
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(4.dp),
            )
        }
    }
}

private data class Palette(
    val gradient: List<Color>,
)

private val palettes = listOf(
    Palette(listOf(Color(0xFF1E40AF), Color(0xFF7C3AED), Color(0xFFEC4899))),
    Palette(listOf(Color(0xFF059669), Color(0xFF0891B2), Color(0xFF1E40AF))),
    Palette(listOf(Color(0xFFDC2626), Color(0xFFEA580C), Color(0xFFF59E0B))),
    Palette(listOf(Color(0xFF7C3AED), Color(0xFFEC4899), Color(0xFFF43F5E))),
    Palette(listOf(Color(0xFF0891B2), Color(0xFF1E40AF), Color(0xFF312E81))),
    Palette(listOf(Color(0xFFB45309), Color(0xFF92400E), Color(0xFF78350F))),
    Palette(listOf(Color(0xFFBE185D), Color(0xFF9D174D), Color(0xFF831843))),
    Palette(listOf(Color(0xFF0F766E), Color(0xFF0D9488), Color(0xFF14B8A6))),
)
