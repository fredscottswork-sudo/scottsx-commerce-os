package com.scottsx.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.data.domain.Order
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.components.StatusChip
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.theme.ScottsTechXColors

/** Buyer order history. */
@Composable
fun OrdersScreen(onBack: () -> Unit) {
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        orders = V2Client.fetchOrders()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "My orders", onBack = onBack)
        if (loading) {
            LoadingRow()
        } else if (orders.isEmpty()) {
            EmptyState("📦", "No orders yet", "Your purchases will appear here.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(orders, key = { it.id }) { order ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (order.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = order.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .padding(end = 10.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(order.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${order.storeName.ifBlank { "ScottsTechX" }} · ${order.quantity} item(s)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(formatUgx(order.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ScottsTechXColors.BluePrimary)
                            }
                            StatusChip(order.status)
                        }
                    }
                }
            }
        }
    }
}
