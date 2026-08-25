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
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.TransactionStatus
import com.scottsx.app.ui.components.SettingsScaffold
import com.scottsx.app.ui.components.SettingsBlankHint
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * My Orders — uses the existing local TransactionStore and the
 * backend's /api/v1/orders endpoints as a fallback.
 *
 * For now this surfaces the transactions list from TransactionStore
 * (which already tracks local completed receipts) and lets the user
 * tap to track, request a return, or open a refund.
 */
@Composable
fun MyOrdersScreen(onBack: () -> Unit, onTrack: (String) -> Unit, onOpenReturn: (String) -> Unit, onOpenRefund: (String) -> Unit) {
    val agreements = TransactionStore.agreements
    SettingsScaffold(title = "My Orders", onBack = onBack) {
        if (agreements.isEmpty()) {
            SettingsBlankHint("You haven't placed any orders yet.")
        } else {
            agreements.forEach { ag ->
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
                            Text("Order #${ag.id.takeLast(6).uppercase()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Product: ${ag.revisions.lastOrNull()?.productName ?: "N/A"}", fontSize = 12.sp, color = ScottsTechXColors.OnLightSecondary)
                            Text("Status: ${ag.status.name}", fontSize = 12.sp, color = ScottsTechXColors.BluePrimary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Track", color = ScottsTechXColors.BluePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onTrack(ag.id) })
                        Text("Return", color = ScottsTechXColors.BluePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenReturn(ag.id) })
                        Text("Refund", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenRefund(ag.id) })
                    }
                }
            }
        }
    }
}

/** Track a single order — shows event timeline + ETA. */
@Composable
fun TrackOrderScreen(orderId: String, onBack: () -> Unit) {
    val agreements = TransactionStore.agreements
    val tx = agreements.firstOrNull { it.id == orderId }
    SettingsScaffold(title = "Track Order", onBack = onBack) {
        if (tx == null) {
            SettingsBlankHint("Order not found.")
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
            Text(tx.status.label, fontSize = 14.sp, color = ScottsTechXColors.BluePrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            val isDone = { status: TransactionStatus ->
                status == TransactionStatus.COMPLETED || status == TransactionStatus.CONFIRMED || status == TransactionStatus.IN_PROGRESS
            }
            val placed = true
            val confirmed = tx.status == TransactionStatus.CONFIRMED || tx.status == TransactionStatus.IN_PROGRESS || tx.status == TransactionStatus.COMPLETED
            val shipped = tx.status == TransactionStatus.IN_PROGRESS || tx.status == TransactionStatus.COMPLETED
            val delivered = tx.status == TransactionStatus.COMPLETED
            listOf<Pair<String, Boolean>>("Placed" to placed, "Confirmed" to confirmed, "Shipped" to shipped, "Delivered" to delivered).forEach { pair ->
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
