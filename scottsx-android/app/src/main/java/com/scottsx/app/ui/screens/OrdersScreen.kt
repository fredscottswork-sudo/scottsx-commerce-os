package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.SettingsScaffold
import com.scottsx.app.ui.components.SettingsBlankHint
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * My Orders — fully live off `GET /api/v1/me/orders`. Each row shows
 * the real order the buyer placed through checkout; Track derives the
 * stage timeline from the server status.
 */
@Composable
fun MyOrdersScreen(onBack: () -> Unit, onTrack: (String) -> Unit, onOpenReturn: (String) -> Unit, onOpenRefund: (String) -> Unit) {
    var orders by remember { mutableStateOf<List<V2Client.MyOrder>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            orders = V2Client.fetchMyOrders()
            if (orders == null) loadError = "Couldn't load your orders — pull to try again."
        } catch (t: Throwable) {
            loadError = "Couldn't load your orders: ${t.message ?: "unknown error"}"
        }
    }

    SettingsScaffold(title = "My Orders", onBack = onBack) {
        when {
            orders == null && loadError == null -> {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = ScottsTechXColors.BluePrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            orders.isNullOrEmpty() -> {
                SettingsBlankHint(loadError ?: "You haven't placed any orders yet.")
            }
            else -> {
                orders!!.forEach { order ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Order #${order.id.takeLast(6).uppercase()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Product: ${order.title}", fontSize = 12.sp, color = ScottsTechXColors.OnLightSecondary)
                                Text(
                                    "${order.quantity} × ${com.scottsx.app.ui.util.formatUgx(order.amountUgx)}",
                                    fontSize = 12.sp, color = ScottsTechXColors.OnLight,
                                )
                                Text(
                                    "Status: ${order.displayStatus}",
                                    fontSize = 12.sp,
                                    color = if (order.status == "cancelled" || order.status == "refunded") Color(0xFFDC2626) else ScottsTechXColors.BluePrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Track", color = ScottsTechXColors.BluePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onTrack(order.id) })
                            Text("Refund", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenRefund(order.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Track a single order — stage timeline derived from the real server status. */
@Composable
fun TrackOrderScreen(orderId: String, onBack: () -> Unit) {
    var order by remember { mutableStateOf<V2Client.MyOrder?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        order = try { V2Client.fetchMyOrders()?.firstOrNull { it.id == orderId } } catch (_: Throwable) { null }
        loaded = true
    }

    SettingsScaffold(title = "Track Order", onBack = onBack) {
        if (!loaded) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = ScottsTechXColors.BluePrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(26.dp),
                )
            }
            return@SettingsScaffold
        }
        val tx = order
        if (tx == null) {
            SettingsBlankHint("Order not found. It may have been placed on another device — refresh My Orders.")
            return@SettingsScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(16.dp),
        ) {
            Text("Order #${tx.id.takeLast(6).uppercase()}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(tx.displayStatus, fontSize = 14.sp, color = ScottsTechXColors.BluePrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(tx.title, fontSize = 12.sp, color = ScottsTechXColors.OnLightSecondary)
            Spacer(Modifier.height(12.dp))
            // pending → paid → shipped → delivered on the backend.
            val placed = true
            val confirmed = tx.status != "pending" && tx.status != "cancelled"
            val shipped = tx.status == "shipped" || tx.status == "delivered"
            val delivered = tx.status == "delivered"
            val cancelled = tx.status == "cancelled" || tx.status == "refunded"
            listOf<Pair<String, Boolean>>(
                "Placed" to placed,
                (if (cancelled) "Cancelled" else "Confirmed") to (confirmed && !cancelled),
                "Shipped" to (shipped && !cancelled),
                "Delivered" to (delivered && !cancelled),
            ).forEach { pair ->
                val stage = pair.first
                val done = pair.second
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (done) ScottsTechXColors.BluePrimary else Color(0xFFE5E7EB)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stage, color = if (done) ScottsTechXColors.OnLight else ScottsTechXColors.OnLightSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Account/security — change password + sign-out all devices. */
@Composable
fun SecurityScreen(onBack: () -> Unit, onSignOut: () -> Unit = {}) {
    SettingsScaffold(title = "Security", onBack = onBack) {
        Text(
            "Your account is secured with Firebase Auth. Use the Sign out button to end this session on this device.",
            fontSize = 13.sp,
            color = ScottsTechXColors.OnLightSecondary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFDC2626))
                .clickable(onClick = onSignOut)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Sign out", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Permanent account deletion — info dialog. */
@Composable
fun DeleteAccountScreen(onBack: () -> Unit, onConfirm: () -> Unit) {
    SettingsScaffold(title = "Delete Account", onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(16.dp),
        ) {
            Column {
                Text("This will permanently delete your account.", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
                Spacer(Modifier.height(12.dp))
                Text(
                    "All of the following will be removed:\n" +
                            "  - Profile (display name, avatar, bio)\n" +
                            "  - Saved addresses and payment methods\n" +
                            "  - Saved products and favorite sellers\n" +
                            "  - Refund and return history\n" +
                            "  - Notifications and account activity\n" +
                            "  - Support tickets\n\n" +
                            "This action cannot be undone.",
                    fontSize = 13.sp,
                    color = ScottsTechXColors.OnLightSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFDC2626))
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Yes, delete my account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
