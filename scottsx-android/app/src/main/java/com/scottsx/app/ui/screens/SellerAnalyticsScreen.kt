package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.SellerDashboardStats
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.theme.ScottsTechXColors

/** Seller analytics tab — dashboard stats from /seller/dashboard/stats. */
@Composable
fun SellerAnalyticsScreen(onBack: () -> Unit) {
    var stats by remember { mutableStateOf<SellerDashboardStats?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        stats = V2Client.fetchSellerDashboardStats()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Analytics", onBack = onBack)
        if (loading) {
            LoadingRow()
        } else if (stats == null) {
            EmptyState("📊", "No analytics yet", "Sell more to unlock insights.")
        } else {
            val s = stats!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)),
                                RoundedCornerShape(18.dp),
                            )
                            .padding(18.dp),
                    ) {
                        Column {
                            Text("Total revenue", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                            Text(formatUgx(s.revenueUgx), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            s.topProduct?.let {
                                Spacer(Modifier.height(6.dp))
                                Text("Top seller: $it", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricCard("Orders", s.orders.toString(), Icons.Filled.LocalShipping, Modifier.weight(1f))
                        MetricCard("Listings", s.totalProducts.toString(), Icons.Filled.Inventory2, Modifier.weight(1f))
                        MetricCard("Low stock", s.lowStock.toString(), Icons.Filled.Payments, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(icon, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
