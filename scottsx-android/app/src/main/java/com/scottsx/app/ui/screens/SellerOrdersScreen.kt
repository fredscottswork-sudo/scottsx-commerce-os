package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.OrderStatus
import com.scottsx.app.data.domain.SellerApiOrder
import com.scottsx.app.data.domain.SellerOrder
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Seller orders — rebuilt to web parity (web/src/pages/seller/SellerOrders.tsx):
 * stats strip (needs action w/ pending value, in transit, delivered,
 * revenue), tab groups with counts, client search over product/buyer, and
 * per-row message-the-buyer. All rows stream from /api/v1/seller/orders.
 */
@Composable
fun SellerOrdersScreen(
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit = {},
    onMessageBuyer: (SellerApiOrder) -> Unit = {},
) {
    var tab by remember { mutableStateOf("new") }
    var query by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf<List<SellerApiOrder>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadTick) {
        loadError = null
        raw = try { V2Client.fetchSellerOrders() }
        catch (t: Throwable) { loadError = "Couldn't load orders: ${t.message ?: "unknown error"}"; null }
        if (raw == null && loadError == null) loadError = "Couldn't load orders — check your connection."
    }

    val orders = raw ?: emptyList()
    fun group(o: SellerApiOrder): String = when (o.status.lowercase()) {
        "pending", "paid", "processing" -> "new"
        "shipped" -> "shipping"
        "delivered" -> "completed"
        else -> "other"
    }
    val needsAction = orders.filter { group(it) == "new" }
    val inTransit = orders.filter { group(it) == "shipping" }
    val delivered = orders.filter { group(it) == "completed" }
    val revenue = orders
        .filter { it.status.lowercase() in setOf("paid", "shipped", "delivered") }
        .sumOf { it.amount * maxOf(1, it.quantity).toLong() }
    val pendingValue = needsAction.sumOf { it.amount * maxOf(1, it.quantity).toLong() }

    val needle = query.trim().lowercase()
    val tabBase = when (tab) {
        "new" -> needsAction
        "shipping" -> inTransit
        "completed" -> delivered
        else -> orders
    }
    val rows = if (needle.isEmpty()) tabBase else tabBase.filter {
        it.title.lowercase().contains(needle) || it.buyerName.lowercase().contains(needle)
    }

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight).statusBarSpacer()) {
        // Header
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
            Column(Modifier.weight(1f)) {
                Text("Orders", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "Identical rows to the web seller dashboard",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable {
                        raw = null
                        reloadTick++
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        when {
            raw == null && loadError == null -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = ScottsTechXColors.BluePrimary)
                }
            }
            loadError != null -> {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(loadError!!, color = ScottsTechXColors.OnPanel)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ScottsTechXColors.BluePrimary)
                            .clickable {
                                loadError = null
                                reloadTick++
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Retry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            else -> {
                // Stats strip — web's four figures
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiniStat("Needs action", needsAction.size.toString(), Icons.Filled.HourglassTop, Color(0xFFF59E0B), hint = formatUgx(pendingValue))
                    MiniStat("In transit", inTransit.size.toString(), Icons.Filled.LocalShipping, Color(0xFF22D3EE))
                    MiniStat("Delivered", delivered.size.toString(), Icons.Filled.CheckCircle, Color(0xFF22C55E))
                    MiniStat("Revenue", com.scottsx.app.ui.components.formatUgxCompact(revenue), Icons.Filled.TrendingUp, Color(0xFF8B5CF6), hint = "${orders.size} orders")
                }

                // Tabs with counts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip("Needs action (${needsAction.size})", tab == "new") { tab = "new" }
                    FilterChip("Shipped (${inTransit.size})", tab == "shipping") { tab = "shipping" }
                    FilterChip("Delivered (${delivered.size})", tab == "completed") { tab = "completed" }
                    FilterChip("All (${orders.size})", tab == "all") { tab = "all" }
                    OrderStatus.entries.forEach { st ->
                        FilterChip(st.label, tab == st.name.lowercase()) { tab = st.name.lowercase() }
                    }
                }

                // Search (product or buyer)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ScottsTechXColors.OnCardSecondary) },
                    placeholder = { Text("Filter by product or buyer…", color = ScottsTechXColors.OnCardSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ScottsTechXColors.OnCard,
                        unfocusedTextColor = ScottsTechXColors.OnCard,
                        focusedContainerColor = ScottsTechXColors.CardSurface,
                        unfocusedContainerColor = ScottsTechXColors.CardSurface,
                        focusedBorderColor = ScottsTechXColors.BluePrimary,
                        unfocusedBorderColor = ScottsTechXColors.CardSurfaceAlt,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    "${rows.size} shown",
                    color = ScottsTechXColors.OnPanelSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Spacer(Modifier.height(4.dp))

                if (rows.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            if (needle.isNotEmpty()) "Nothing matched" else "No orders here yet",
                            color = ScottsTechXColors.OnPanel,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (needle.isNotEmpty()) "Try another search term."
                            else "Approved listings reach buyers instantly — orders land here.",
                            color = ScottsTechXColors.OnPanelSecondary,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(rows, key = { it.id }) { o ->
                            OrderRow(
                                o.toLegacyOrder(),
                                onClick = { onOpenOrder(o.id) },
                                onMessage = { onMessageBuyer(o) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, hint: String? = null) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.CardSurface)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = ScottsTechXColors.OnCardSecondary, fontSize = 10.5.sp, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = ScottsTechXColors.OnCard, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
        if (hint != null) Text(hint, color = ScottsTechXColors.OnCardSecondary, fontSize = 9.5.sp, maxLines = 1)
    }
}

/** Map a canonical backend order row onto this screen's legacy view model. */
private fun SellerApiOrder.toLegacyOrder(): SellerOrder = SellerOrder(
    id = id,
    productName = title,
    itemsCount = quantity,
    totalUgx = amount * quantity,
    placedAtLabel = createdAt.take(16).replace('T', ' '),
    status = when (status) {
        "pending" -> OrderStatus.PENDING
        "paid", "processing" -> OrderStatus.READY
        "shipped" -> OrderStatus.SHIPPED
        "delivered" -> OrderStatus.DELIVERED
        "cancelled" -> OrderStatus.CANCELLED
        else -> OrderStatus.PENDING
    },
    buyerName = buyerName,
)

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ScottsTechXColors.BluePrimary else ScottsTechXColors.CardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) Color.White else ScottsTechXColors.OnCard,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OrderRow(o: SellerOrder, onClick: () -> Unit, onMessage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.CardSurface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Buyer initial disc — same visual language as catalysed orders
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.BluePrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(o.buyerInitial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    o.productName,
                    color = ScottsTechXColors.OnCard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
                Text(
                    "${o.itemsCount} item${if (o.itemsCount == 1) "" else "s"} • ${o.buyerName} • ${o.placedAtLabel}",
                    color = ScottsTechXColors.OnCardSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    com.scottsx.app.ui.components.formatUgx(o.totalUgx),
                    color = ScottsTechXColors.OnCard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                StatusPill(o.status)
            }
        }
        Spacer(Modifier.height(8.dp))
        // Message the buyer — web parity (chatService.open(buyerId))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.12f))
                .clickable(onClick = onMessage)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("Message ${o.buyerName.split(" ").first()}", color = ScottsTechXColors.BluePrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusPill(status: OrderStatus) {
    val (bg, fg) = when (status) {
        OrderStatus.PENDING -> Color(0xFFFFFBEB) to Color(0xFFB45309)
        OrderStatus.READY -> Color(0xFFEFF6FF) to Color(0xFF1D4ED8)
        OrderStatus.SHIPPED -> Color(0xFFF5F3FF) to Color(0xFF6D28D9)
        OrderStatus.DELIVERED -> Color(0xFFECFDF5) to Color(0xFF047857)
        OrderStatus.CANCELLED -> Color(0xFFFEF2F2) to Color(0xFFB91C1C)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fg.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(status.label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
