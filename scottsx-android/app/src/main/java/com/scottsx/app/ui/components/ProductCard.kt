package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Product
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Product tile.
 *
 * `showWishlist` exists because the seller dashboard reuses this card for the
 * seller's OWN listings, where a "save to wishlist" heart is meaningless — and
 * the white circle drawn behind it was the thing making that grid look messy.
 * Sellers get a status pill instead, which is information they actually need.
 */
@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showWishlist: Boolean = true,
    wished: Boolean = false,
    onWishToggle: (() -> Unit)? = null,
    statusLabel: String? = null,
) {

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            ProductImage(
                imageKey = product.id,
                categoryLabel = product.category,
                imageUrl = product.imageUrl,
                modifier = Modifier.fillMaxSize(),
            )
            if (product.isFlashDeal) {
                Surface(
                    color = ScottsTechXColors.ErrorRed,
                    shape = RoundedCornerShape(bottomEnd = 12.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = "-${product.discountPercent}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (showWishlist) {
                // A soft scrim disc, not an opaque white puck: it has to work on
                // both a bright product photo and a dark placeholder without
                // stamping a hard circle across the artwork.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(enabled = onWishToggle != null) { onWishToggle?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (wished) "Saved" else "Save",
                        tint = if (wished) ScottsTechXColors.ErrorRed else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (statusLabel != null) {
                Surface(
                    color = statusPillColor(statusLabel).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Text(
                        text = statusLabel.replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = product.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = product.seller.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer4()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatUgx(product.priceMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ScottsTechXColors.BluePrimary,
                )
                if (product.oldPriceMinor != null && product.oldPriceMinor > product.priceMinor) {
                    Text(
                        text = formatUgx(product.oldPriceMinor),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Filled.Star, contentDescription = "rating", tint = ScottsTechXColors.WarningAmber, modifier = Modifier.size(14.dp))
                Text("${product.rating}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("(${product.ratingCount})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))

private fun statusPillColor(status: String): Color = when (status.lowercase()) {
    "approved" -> ScottsTechXColors.SuccessGreen
    "pending" -> ScottsTechXColors.WarningAmber
    else -> ScottsTechXColors.ErrorRed
}
