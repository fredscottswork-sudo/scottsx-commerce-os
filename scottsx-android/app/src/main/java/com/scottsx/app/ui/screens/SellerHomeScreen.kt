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
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.SellerDashboardStats
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.navigation.Routes
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.SectionHeader
import com.scottsx.app.ui.components.SellerBottomBar
import com.scottsx.app.ui.components.SellerBottomTab
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
            contentPadding = PaddingValues(bottom = 96.dp),
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatBox("UGX", stats?.revenueUgx?.toString() ?: "0", "Revenue")
                            StatBox("", (stats?.orders ?: 0).toString(), "Orders")
                            StatBox("", (stats?.totalProducts ?: 0).toString(), "Listings")
                            StatBox("", (stats?.lowStock ?: 0).toString(), "Low stock")
                        }
                    }
                }
            }

            if (stats?.lowStock != null && stats!!.lowStock > 0) {
                item {
                    Surface(
                        color = ScottsTechXColors.WarningAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = ScottsTechXColors.WarningAmber)
                            Text(
                                "${stats!!.lowStock} product(s) low on stock — restock soon.",
                                color = ScottsTechXColors.WarningAmber,
                                fontWeight = FontWeight.SemiBold,
                            )
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
                                )
                            }
                        }
                    }
                }
            }

            if (products.isNotEmpty()) {
                item {
                    // Low-stock inline list (duplicate of the alert, kept as a full row set)
                    SectionHeader("Inventory alerts")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(products.filter { it.stockQuantity <= 5 }) { product ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(18.dp))
                                    Text(
                                        "${product.title.take(18)}… · ${product.stockQuantity} left",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
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

@Composable
private fun StatBox(prefix: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = if (prefix.isBlank()) value else "$prefix $value",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}
