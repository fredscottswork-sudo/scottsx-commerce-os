package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.SellerDashboard
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.AreaChart
import com.scottsx.app.ui.components.CountUpText
import com.scottsx.app.ui.components.DonutChart
import com.scottsx.app.ui.components.DonutSegment
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.HBarList
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.components.StatCard
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.formatUgxCompact
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Seller analytics — the Android mirror of the web analytics page.
 * Same /seller/dashboard/stats payload: KPI cards, a revenue/orders chart
 * you can toggle, listing-status donut and the top-products ranking.
 */
@Composable
fun SellerAnalyticsScreen(onBack: () -> Unit) {
    var dash by remember { mutableStateOf<SellerDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var series by remember { mutableStateOf("revenue") } // revenue | orders

    LaunchedEffect(Unit) {
        dash = V2Client.fetchSellerDashboard()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Analytics", onBack = onBack)
        if (loading) {
            LoadingRow()
        } else if (dash == null) {
            EmptyState("📊", "No analytics yet", "Sell more to unlock insights.")
        } else {
            val d = dash!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Revenue hero ─────────────────────────────────────────────
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Total revenue",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CountUpText(
                                target = d.revenueUgx,
                                formatter = { formatUgx(it) },
                                fontSize = 28.sp,
                                color = ScottsTechXColors.BluePrimary,
                            )
                            d.topProduct?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Top seller: $it",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ── KPI grid ────────────────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(
                                icon = Icons.Filled.LocalShipping,
                                label = "Orders",
                                value = d.orders.toLong(),
                                hint = if (d.orders30 > 0) "+${d.orders30} in 30d" else "",
                                accent = ScottsTechXColors.CyanAccent,
                                modifier = Modifier.weight(1f),
                            )
                            StatCard(
                                icon = Icons.Filled.Payments,
                                label = "Avg order value",
                                value = d.avgOrderValueUgx,
                                formatter = { formatUgxCompact(it) },
                                accent = ScottsTechXColors.PurpleAccent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(
                                icon = Icons.Filled.Visibility,
                                label = "Product views",
                                value = d.totalViews.toLong(),
                                accent = ScottsTechXColors.SuccessGreen,
                                modifier = Modifier.weight(1f),
                            )
                            StatCard(
                                icon = Icons.Filled.Groups,
                                label = "Followers",
                                value = d.followers.toLong(),
                                accent = ScottsTechXColors.BluePrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ── 14-day chart with revenue/orders toggle ────────────────
                if (d.salesSeries.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Last 14 days",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    SeriesToggle("Revenue", series == "revenue") { series = "revenue" }
                                    Spacer(Modifier.width(6.dp))
                                    SeriesToggle("Orders", series == "orders") { series = "orders" }
                                }
                                Spacer(Modifier.height(12.dp))
                                if (series == "revenue") {
                                    AreaChart(
                                        points = d.salesSeries.map { it.revenue },
                                        labels = d.salesSeries.map { it.date.takeLast(5) },
                                        color = ScottsTechXColors.BluePrimary,
                                    )
                                } else {
                                    AreaChart(
                                        points = d.salesSeries.map { it.orders.toLong() },
                                        labels = d.salesSeries.map { it.date.takeLast(5) },
                                        color = ScottsTechXColors.CyanAccent,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Listing status donut ────────────────────────────────────
                if (d.totalProducts > 0) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Listings by status", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                DonutChart(
                                    segments = listOfNotNull(
                                        d.productsByStatus["approved"]?.takeIf { it > 0 }
                                            ?.let { DonutSegment("Live", it, ScottsTechXColors.SuccessGreen) },
                                        d.productsByStatus["pending"]?.takeIf { it > 0 }
                                            ?.let { DonutSegment("In review", it, ScottsTechXColors.WarningAmber) },
                                        d.productsByStatus["draft"]?.takeIf { it > 0 }
                                            ?.let { DonutSegment("Draft", it, ScottsTechXColors.CyanAccent) },
                                        d.productsByStatus["rejected"]?.takeIf { it > 0 }
                                            ?.let { DonutSegment("Rejected", it, ScottsTechXColors.ErrorRed) },
                                        d.productsByStatus["suspended"]?.takeIf { it > 0 }
                                            ?.let { DonutSegment("Suspended", it, Color(0xFF64748B)) },
                                    ),
                                    centerLabel = "${d.totalProducts}",
                                    centerSub = "listings",
                                )
                                if (d.lowStock > 0 || d.outOfStock > 0) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "⚠️ ${d.lowStock} low stock · ${d.outOfStock} out of stock",
                                        fontSize = 12.sp,
                                        color = ScottsTechXColors.WarningAmber,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Top products ────────────────────────────────────────────
                val sold = d.topProducts.filter { it.sold > 0 }
                if (sold.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Top products", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                HBarList(items = sold.map { it.title to it.sold })
                            }
                        }
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .height(14.dp),
                        )
                        Text(
                            "Numbers update in real time — same data as the web dashboard.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(99.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
