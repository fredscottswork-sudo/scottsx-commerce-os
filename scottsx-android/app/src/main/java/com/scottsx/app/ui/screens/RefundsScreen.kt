package com.scottsx.app.ui.screens

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Refund
import com.scottsx.app.data.domain.Order
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.StatusChip
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Refund claims — list + new claim form. */
@Composable
fun RefundsScreen(onBack: () -> Unit) {
    var refunds by remember { mutableStateOf<List<Refund>>(emptyList()) }
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var selectedOrderId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            refunds = V2Client.fetchRefunds()
            orders = V2Client.fetchOrders()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Refunds", onBack = onBack)
        if (loading) {
            LoadingRow()
        } else if (refunds.isEmpty() && !showForm) {
            EmptyState("↩️", "No refund claims", "If an order isn't right, open a claim within 7 days.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(refunds) { refund ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(refund.reason, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                StatusChip(refund.status)
                            }
                            Text("Order ${refund.orderId.take(8)} · ${refund.createdAt.substringAfter("T").substring(0, 10)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (showForm) {
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text("New refund claim", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            if (orders.isEmpty()) {
                                Text("No orders to claim against yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                orders.forEach { order ->
                                    Surface(
                                        color = if (selectedOrderId == order.id) ScottsTechXColors.BluePrimary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                            .clickable { selectedOrderId = order.id },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(order.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                            Text(com.scottsx.app.ui.components.formatUgx(order.amount), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            InputField(value = reason, onValueChange = { reason = it }, label = "Reason", placeholder = "Item not delivered / not as described")
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Submit claim",
                                enabled = selectedOrderId.isNotBlank() && reason.length >= 3,
                                onClick = {
                                    scope.launch {
                                        V2Client.createRefund(selectedOrderId, reason)
                                        showForm = false
                                        reason = ""
                                        reload()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                color = ScottsTechXColors.BluePrimary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.clickable { showForm = !showForm },
            ) {
                Text(
                    if (showForm) "Close form" else "+ New refund claim",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
