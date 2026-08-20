package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.SellerDashboardStats
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.navigation.Routes
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.SectionHeader
import com.scottsx.app.ui.components.formatUgxCompact
import com.scottsx.app.ui.components.SellerBottomBar
import com.scottsx.app.ui.components.SellerBottomTab
import com.scottsx.app.ui.components.bottomInset
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Seller home — store stats hero, inventory grid, low-stock alerts.
 * Bottom nav uses the balanced 2-half SellerBottomBar with a centre Add FAB.
 */
@Composable
fun SellerHomeScreen(
    onProductClick: (String) -> Unit,
    onAddProduct: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var stats by remember { mutableStateOf<SellerDashboardStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    val currentUser by SessionCache.user.collectAsState()

    LaunchedEffect(Unit) {
        products = V2Client.fetchSellerProducts()
        stats = V2Client.fetchSellerDashboardStats()
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp + bottomInset()),
        ) {
            item {
                // Store stats hero card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent),
                            ),
                            RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        )
                        .statusBarSpacer()
                        .padding(20.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (currentUser?.displayName ?: "My Store"),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Store settings",
                                tint = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable { onNavigate(Routes.PROFILE) }
                                    .padding(8.dp),
                            )
                        }
                        Text(
                            "Seller dashboard",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        // Two rows of two. Four raw figures in one row overflowed
                        // a 360dp screen as soon as revenue passed a million, and
                        // an un-abbreviated UGX total is up to 13 characters.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatBox(
                                // The currency lives on the label, not the
                                // figure. A stat tile is 72.5dp on a 360dp
                                // phone and "UGX 2.4M" needs ~81dp at 18sp, so
                                // keeping UGX on the value line ellipsised the
                                // revenue on every single phone. "2.4M" needs
                                // ~40dp and fits with room to spare, at the
                                // original 18sp with no shrinking.
                                value = formatUgxCompact(stats?.revenueUgx ?: 0L),
                                label = "Revenue UGX",
                                modifier = Modifier.weight(1f),
                            )
                            StatBox(
                                value = (stats?.orders ?: 0).toString(),
                                label = "Orders",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatBox(
                                value = (stats?.totalProducts ?: 0).toString(),
                                label = "Listings",
                                modifier = Modifier.weight(1f),
                            )
                            StatBox(
                                value = (stats?.lowStock ?: 0).toString(),
                                label = "Low stock",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Low stock used to be stated THREE times on this one screen: a
            // stat tile, a warning banner, and an "Inventory alerts" row whose
            // own comment called it a duplicate. The tile carries the count and
            // the named list below carries the action; the banner added nothing.

            // Needs restocking — placed BEFORE the full grid because it is the
            // only time-sensitive thing on the screen. Rendered only when the
            // list is non-empty; the previous version drew its heading
            // unconditionally and left an empty strip underneath.
            val lowStockItems = products.filter { it.stockQuantity <= 5 }
            if (lowStockItems.isNotEmpty()) {
                item {
                    SectionHeader("Needs restocking")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(lowStockItems) { product ->
                            Surface(
                                color = ScottsTechXColors.WarningAmber.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Inventory2,
                                        contentDescription = null,
                                        tint = ScottsTechXColors.WarningAmber,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        product.title.take(18) + "\u2026 \u00b7 " + product.stockQuantity + " left",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Your inventory", action = "Add product", onAction = onAddProduct) }

            if (loading) {
                item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
            } else if (products.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("📦", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No products yet", style = MaterialTheme.typography.titleMedium)
                        Text("Tap the + button to list your first product.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                products.chunked(2).forEach { rowItems ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { product ->
                                ProductCard(
                                    product = product,
                                    onClick = { onProductClick(product.id) },
                                    modifier = Modifier.weight(1f),
                                    // A seller cannot wishlist their own stock;
                                    // what they need at a glance is whether the
                                    // listing is live, waiting on an admin, or
                                    // was rejected.
                                    showWishlist = false,
                                    statusLabel = product.status,
                                )
                            }
                            // chunked(2) leaves the last row with a single
                            // item when the count is odd. Without this the
                            // lone card takes weight(1f) of the whole row
                            // and renders at double width. An empty
                            // weighted spacer holds the missing cell.
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Moderation status. Listings are not live until an admin approves
            // them, so the seller needs to see what is still in review and what
            // was rejected (and why) — otherwise a rejected listing just looks
            // like it silently vanished.
            val inReview = products.filter { it.status == "pending" }
            val rejected = products.filter { it.status == "rejected" }
            val suspended = products.filter { it.status == "suspended" }
            if (inReview.isNotEmpty() || rejected.isNotEmpty() || suspended.isNotEmpty()) {
                item {
                    SectionHeader("Listing status")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (inReview.isNotEmpty()) {
                            ModerationBanner(
                                emoji = "⏳",
                                tint = ScottsTechXColors.WarningAmber,
                                title = "${inReview.size} listing${if (inReview.size == 1) "" else "s"} in review",
                                body = "An admin approves new listings before buyers can see them. " +
                                    "You'll be notified as soon as they decide.",
                            )
                        }
                        rejected.forEach { product ->
                            ModerationBanner(
                                emoji = "⚠️",
                                tint = ScottsTechXColors.ErrorRed,
                                title = "Rejected: ${product.title.take(38)}",
                                body = product.rejectionReason
                                    ?: "Edit the listing and resubmit it for review.",
                            )
                        }
                        suspended.forEach { product ->
                            ModerationBanner(
                                emoji = "⛔",
                                tint = ScottsTechXColors.ErrorRed,
                                title = "Suspended: ${product.title.take(36)}",
                                body = product.rejectionReason
                                    ?: "This listing was taken down. Contact support for details.",
                            )
                        }
                    }
                }
            }

        }

        SellerBottomBar(
            selected = SellerBottomTab.Home,
            onTabSelected = { tab ->
                when (tab) {
                    SellerBottomTab.AI -> onNavigate(Routes.SELLER_AI)
                    SellerBottomTab.Messages -> onNavigate(Routes.SELLER_MESSAGES)
                    SellerBottomTab.Analytics -> onNavigate(Routes.SELLER_ANALYTICS)
                    else -> Unit
                }
            },
            onAddClick = onAddProduct,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * One dashboard figure. Sized by weight from the caller and allowed to shrink
 * its own text, so a long revenue figure narrows instead of pushing the tile
 * next to it off-screen.
 */
@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModerationBanner(
    emoji: String,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(emoji, fontSize = 16.sp)
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = tint)
            Text(
                body,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
