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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.scottsx.app.data.SellerDataSource
import com.scottsx.app.data.domain.OrderStatus
import com.scottsx.app.data.domain.SellerOrder
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx

/**
 * Seller orders. Lists every order with quick filters; tapping a row
 * shows the order detail bottom-sheet with Accept / Process / Mark
 * Ready / Complete actions.
 */
@Composable
fun SellerOrdersScreen(
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit = {}, // reserved for Stage 4 deep-linking
) {
    var filter by remember { mutableStateOf<OrderStatus?>(null) }
    val orders = remember(filter) { SellerDataSource.snapshot().recentOrders.filter { filter == null || it.status == filter } }
    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.BluePrimaryDark)
                .padding(start = 4.dp, end = 16.dp, top = 30.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Orders", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip("All", selected = filter == null) { filter = null }
            OrderStatus.values().forEach { st ->
                FilterChip(st.label, selected = filter == st) { filter = st }
            }
        }
        // Order list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(orders, key = { it.id }) { o ->
                OrderRow(o, onClick = { onOpenOrder(o.id) })
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) ScottsTechXColors.BluePrimary else ScottsTechXColors.PanelInputLight,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else ScottsTechXColors.OnLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OrderRow(o: SellerOrder, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ScottsTechXColors.PanelInputLight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = o.productName.first().uppercase(),
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${o.id}",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = o.productName,
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Text(
                    text = "${o.itemsCount} items · ${o.placedAtLabel}",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "UGX ${formatUgx(o.totalUgx)}",
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                StatusPill(o.status)
            }
        }
    }
}

@Composable
private fun StatusPill(status: OrderStatus) {
    val (bg, fg) = when (status) {
        OrderStatus.Pending -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        OrderStatus.Processing -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        OrderStatus.Ready -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
        OrderStatus.Completed -> Color(0xFFD1FAE5) to Color(0xFF059669)
        OrderStatus.Cancelled -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
