package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/**
 * Global image loader for product photos.
 *
 * GOTCHA (v0.22.1): ALWAYS use Coil.imageLoader(context) / LocalImageLoader —
 * building a fresh ImageLoader.Builder(ctx) drops the shared network stack and
 * images silently never load.
 */
@Composable
fun ProductImage(
    imageKey: String,
    categoryLabel: String,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val loader = LocalImageLoader.current
    val palette = remember(imageKey) { pickPalette(imageKey) }

    Box(modifier = modifier) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = loader,
                contentDescription = categoryLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Gradient placeholder with the category letter.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(palette.first, palette.second))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = categoryLabel.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Deterministic palette per image key so placeholders don't flicker. */
internal fun pickPalette(key: String): Pair<Color, Color> {
    val palettes = listOf(
        Color(0xFF1E6FFF) to Color(0xFF124CA8),
        Color(0xFF8B5CF6) to Color(0xFF6D28D9),
        Color(0xFFEC4899) to Color(0xFFBE185D),
        Color(0xFFF59E0B) to Color(0xFFD97706),
        Color(0xFF10B981) to Color(0xFF047857),
        Color(0xFF3B82F6) to Color(0xFF1D4ED8),
    )
    val idx = (key.hashCode() and 0x7fffffff) % palettes.size
    return palettes[idx]
}
