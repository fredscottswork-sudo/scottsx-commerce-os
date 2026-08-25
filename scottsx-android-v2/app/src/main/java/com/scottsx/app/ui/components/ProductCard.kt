package com.scottsx.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.WishlistStore
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx

/**
 * Compact premium product card used by Flash Deals and
 * Recommended rows.
 *
 *   ┌─────────────────────────┐
 *   │  [PRODUCT IMAGE]  -15%  │
 *   │                  ♡      │
 *   │                         │
 *   │  Product Name           │
 *   │  Short description     │
 *   │  UGX 1,150,000         │
 *   │  UGX 1,350,000 strikethrough│
 *   │                       + │
 *   └─────────────────────────┘
 */
@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit = {},
    onAddToCart: () -> Unit = {},
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 180.dp,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(18.dp))
            .background(ScottsTechXColors.SurfacePanelDark)
            .border(1.dp, ScottsTechXColors.Divider, RoundedCornerShape(18.dp))
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp),
        ) {
            ProductImage(
                imageKey = product.imageUrl,
                imageUrl = product.imageUrl,
                categoryLabel = product.category.displayName,
                modifier = Modifier.fillMaxSize(),
            )

            // Discount badge
            if (product.discountPercent > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ScottsTechXColors.BluePrimary,
                                    ScottsTechXColors.BluePrimaryLight,
                                ),
                            ),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "-${product.discountPercent}%",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                    )
                }
            }

            // Favorite (heart) toggle — isolated composable so only
            // this node recomposes when the wishlist state for THIS
            // product changes. The rest of the card stays still.
            HeartToggle(
                productId = product.id,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.BackgroundDark.copy(alpha = 0.72f)),
            )
        }

        Spacer(Modifier.height(2.dp))

        // Product name
        Text(
            text = product.name,
            color = ScottsTechXColors.OnDark,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )

        // Short description
        Text(
            text = product.shortDescription,
            color = ScottsTechXColors.OnDarkSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
        )

        Spacer(Modifier.height(6.dp))

        // Price + old price
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = formatUgx(product.priceUgx),
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
            product.oldPriceUgx?.let { old ->
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatUgx(old),
                    color = ScottsTechXColors.OnDarkMuted,
                    fontSize = 11.sp,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Rating row + add-to-cart button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, bottom = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "%.1f".format(product.rating),
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScottsTechXColors.BluePrimary)
                    .clickable { onAddToCart() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add to cart",
                    tint = ScottsTechXColors.OnDark,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
/**
 * Isolated favorite-heart toggle. Only recomposes when the wishlist
 * state for [productId] changes. The rest of the parent card is
 * unaffected by other cards' wishlist toggles.
 */
@Composable
private fun HeartToggle(productId: String, modifier: Modifier = Modifier) {
    val favIds by WishlistStore.ids.collectAsState()
    val isFav = productId in favIds
    var heartBump by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartBump) 1.3f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "heart-bump",
    )
    androidx.compose.runtime.LaunchedEffect(heartBump) {
        if (heartBump) {
            kotlinx.coroutines.delay(220)
            heartBump = false
        }
    }
    Box(
        modifier = modifier
            .clickable {
                WishlistStore.toggle(productId)
                heartBump = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = "Favorite",
            tint = if (isFav) Color(0xFFE11D48) else Color(0xFF6B7280),
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { scaleX = heartScale; scaleY = heartScale }
                .clip(CircleShape),
        )
    }
}
