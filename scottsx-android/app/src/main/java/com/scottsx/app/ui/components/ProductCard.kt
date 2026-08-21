package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Product tile — the Compose mirror of the web `.pcard`.
 *
 * Same information hierarchy as the website: media with FLASH / discount /
 * sold-out badges, wishlist heart (state hoisted so the parent wires it to
 * the real bookmark API), title, rating + verified seller + views, price row,
 * location, and an optional add-to-cart action.
 *
 * `showWishlist` exists because the seller dashboard reuses this card for the
 * seller's OWN listings, where a "save to wishlist" heart is meaningless.
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
    /** Show an "Add to cart" button; the card reports success via [onAddedToCart]. */
    withCartButton: Boolean = false,
    onAddedToCart: (() -> Unit)? = null,
    /** Tighter layout for horizontal rails and the seller inventory grid. */
    compact: Boolean = false,
) {
    var adding by remember(product.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val discounted = product.oldPriceMinor != null && product.oldPriceMinor > product.priceMinor
    val soldOut = product.stockQuantity <= 0

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

            // Badges — top-left column, exactly like the web `.pcard-tags`.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (product.isFlashDeal) {
                    CardBadge("FLASH −${product.discountPercent}%", ScottsTechXColors.ErrorRed)
                } else if (discounted) {
                    val pct =
                        ((product.oldPriceMinor!! - product.priceMinor) * 100.0 / product.oldPriceMinor)
                            .roundToInt()
                    CardBadge("−$pct%", ScottsTechXColors.WarningAmber)
                }
                if (soldOut) {
                    CardBadge("Sold out", Color(0xFF64748B))
                }
            }

            if (showWishlist && statusLabel == null) {
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!compact) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "rating",
                        tint = ScottsTechXColors.WarningAmber,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        String.format(java.util.Locale.US, "%.1f", product.rating),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "(${product.ratingCount})",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (product.seller.verified) {
                        Text(
                            "· ✓",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScottsTechXColors.SuccessGreen,
                        )
                    }
                    if (product.viewCount > 0) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 3.dp)
                                .size(11.dp),
                        )
                        Text(
                            "${product.viewCount}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = product.seller.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formatUgx(product.priceMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ScottsTechXColors.BluePrimary,
                    maxLines = 1,
                )
                if (discounted) {
                    Text(
                        text = formatUgx(product.oldPriceMinor!!),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1,
                    )
                }
            }

            if (!compact) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        product.location.ifBlank { product.seller.location.ifBlank { "Uganda" } },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (withCartButton && SessionCache.isLoggedIn()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = if (soldOut) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        ScottsTechXColors.BluePrimary
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !soldOut && !adding) {
                            adding = true
                            scope.launch {
                                V2Client.addToCart(product.id, 1)
                                    .onSuccess { onAddedToCart?.invoke() }
                                adding = false
                            }
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (adding) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    tint = if (soldOut) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        Color.White
                                    },
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    if (soldOut) "Sold out" else "Add to cart",
                                    color = if (soldOut) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        Color.White
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardBadge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(7.dp)) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

private fun statusPillColor(status: String): Color = when (status.lowercase()) {
    "approved" -> ScottsTechXColors.SuccessGreen
    "pending" -> ScottsTechXColors.WarningAmber
    else -> ScottsTechXColors.ErrorRed
}
