package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.TimelineEvent
import com.scottsx.app.data.domain.TimelineEventType
import com.scottsx.app.data.domain.TransactionStatus

/**
 * Stage 4 — Transaction detail screen.
 * Shows: agreement summary, revision history, timeline, mutual
 * confirmation buttons, dispute, "create receipt" shortcut.
 *
 * Real data only.
 */
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onBack: () -> Unit = {},
    onOpenReceipt: (String) -> Unit = {},
    onOpenThread: (String, String?) -> Unit = { _, _ -> },
    onOpenDispute: (String) -> Unit = {},
    onCreateReceipt: (String) -> Unit = {},
) {
    val role = Session.roleOrNull() ?: Role.BUYER
    val ag = remember(transactionId) { TransactionStore.agreementById(transactionId) }

    LaunchedEffect(transactionId) {
        // force recompose on each visit
    }

    if (ag == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text("Transaction", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text("Transaction not found.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        return
    }

    val rev = ag.latestRevision ?: return
    val receipt = remember(transactionId) {
        TransactionStore.receipts.firstOrNull { it.transactionId == transactionId }
    }
    val timeline = remember(transactionId) { TransactionStore.timelineFor(transactionId) }
    val isBuyer = role == Role.BUYER
    val counterparty = if (isBuyer) ag.sellerDisplayName else ag.buyerDisplayName
    val readiness = remember(transactionId) { TransactionStore.readinessFor(transactionId) }
    val canConfirm = rev.buyerConfirmedAt == null && rev.sellerConfirmedAt == null
    val iHaveConfirmed = if (isBuyer) rev.buyerConfirmedAt != null else rev.sellerConfirmedAt != null
    val counterpartyConfirmed = if (isBuyer) rev.sellerConfirmedAt != null else rev.buyerConfirmedAt != null

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Transaction", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "#${ag.id}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        )
                    }
                    StatusChip(ag.status)
                }
            }

            // Summary card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(rev.productName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "with $counterparty · revision ${ag.currentRevision}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                        Divider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                        SummaryRow("Quantity", "${rev.quantity}")
                        SummaryRow("Unit price", TransactionStore.ugxFormat(rev.agreedPriceUgx))
                        SummaryRow("Total", TransactionStore.ugxFormat(rev.agreedPriceUgx * rev.quantity), bold = true)
                        rev.variantLabel?.let { SummaryRow("Variant", it) }
                        rev.paymentMethod?.let { SummaryRow("Payment", it.label) }
                        rev.deliveryMethod?.let { SummaryRow("Delivery", it.label) }
                        rev.pickupOrDeliveryLocation?.let { SummaryRow("Location", it) }
                        rev.expectedDateLabel?.let { SummaryRow("Expected", "$it ${rev.expectedTimeLabel ?: ""}".trim()) }
                        rev.additionalNotes?.let {
                            Spacer(Modifier.height(8.dp))
                            Text("Notes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            Text(it, fontSize = 13.sp)
                        }
                    }
                }
            }

            // AI Readiness card
            if (readiness.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PendingActions, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Transaction readiness", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFF59E0B))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Missing: ${readiness.joinToString(", ")}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            // Mutual confirmation buttons
            if (canConfirm) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Button(
                            onClick = {
                                TransactionStore.acceptLatest(ag.id, role)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Accept the latest revision")
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                TransactionStore.cancel(ag.id, "Cancelled by ${role.displayName.lowercase()}")
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.Cancel, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cancel transaction")
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF3B82F6).copy(alpha = 0.08f),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (iHaveConfirmed) "You have confirmed this revision."
                                    else "${counterparty} has not confirmed yet.",
                                    fontSize = 13.sp,
                                )
                            }
                            Text(
                                if (counterpartyConfirmed && iHaveConfirmed)
                                    "Both parties confirmed. Status: ${ag.statusLabel()}."
                                else "Waiting for ${if (iHaveConfirmed) counterparty else "you"} to confirm.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }

            // Seller actions: create receipt
            if (role == Role.SELLER && ag.status != TransactionStatus.CANCELLED) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            onCreateReceipt(ag.id)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.08f),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ReceiptLong, null, tint = Color(0xFF10B981))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (receipt == null) "Generate a receipt"
                                    else "Receipt: ${receipt.number}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF10B981),
                                )
                                Text(
                                    "Payment recorded by seller (not processed by ScottsTechX)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                )
                            }
                            if (receipt != null) {
                                Text("Open", fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { onOpenReceipt(receipt.number) })
                            }
                        }
                    }
                }
            }

            // Open thread
            if (ag.threadId != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            onOpenThread(ag.threadId!!, ag.productId)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ) {
                        Text(
                            "Open conversation",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Timeline
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Timeline",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(6.dp))
            }
            items(timeline, key = { it.id }) { e ->
                TimelineRow(e, role = role)
            }

            // Dispute button
            if (ag.status != TransactionStatus.CANCELLED && ag.status != TransactionStatus.DISPUTED && ag.status != TransactionStatus.COMPLETED) {
                item {
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { onOpenDispute(ag.id) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.Flag, null, tint = Color(0xFFEF4444))
                        Spacer(Modifier.width(8.dp))
                        Text("Report a problem", color = Color(0xFFEF4444))
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun StatusChip(status: TransactionStatus) {
    val (color, _) = when (status) {
        TransactionStatus.CONFIRMED -> Color(0xFF3B82F6) to status.label
        TransactionStatus.IN_PROGRESS -> Color(0xFF8B5CF6) to status.label
        TransactionStatus.COMPLETED -> Color(0xFF10B981) to status.label
        TransactionStatus.CANCELLED -> Color(0xFFEF4444) to status.label
        TransactionStatus.DISPUTED -> Color(0xFFEF4444) to status.label
        else -> Color(0xFFF59E0B) to status.label
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            status.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TimelineRow(e: TimelineEvent, role: Role) {
    val color = when (e.type) {
        TimelineEventType.AGREEMENT_PROPOSED -> Color(0xFFF59E0B)
        TimelineEventType.AGREEMENT_ACCEPTED -> Color(0xFF3B82F6)
        TimelineEventType.RECEIPT_GENERATED -> Color(0xFF10B981)
        TimelineEventType.DELIVERY_PICKUP_AGREED -> Color(0xFF8B5CF6)
        TimelineEventType.DELIVERY_PICKUP_COMPLETED -> Color(0xFF10B981)
        TimelineEventType.TRANSACTION_COMPLETED -> Color(0xFF10B981)
        TimelineEventType.TRANSACTION_CANCELLED -> Color(0xFFEF4444)
        TimelineEventType.DISPUTE_OPENED -> Color(0xFFEF4444)
        TimelineEventType.DISPUTE_RESOLVED -> Color(0xFF3B82F6)
        TimelineEventType.BUYER_ACKNOWLEDGED -> Color(0xFF06B6D4)
        TimelineEventType.PRODUCT_SELECTED -> Color(0xFF6B7280)
        TimelineEventType.CONVERSATION_STARTED -> Color(0xFF6B7280)
    }
    val icon: ImageVector = when (e.type) {
        TimelineEventType.AGREEMENT_PROPOSED -> Icons.Filled.PendingActions
        TimelineEventType.AGREEMENT_ACCEPTED -> Icons.Filled.CheckCircle
        TimelineEventType.RECEIPT_GENERATED -> Icons.Filled.ReceiptLong
        TimelineEventType.DELIVERY_PICKUP_AGREED -> Icons.Filled.LocalShipping
        TimelineEventType.DELIVERY_PICKUP_COMPLETED -> Icons.Filled.LocalShipping
        TimelineEventType.TRANSACTION_COMPLETED -> Icons.Filled.CheckCircle
        TimelineEventType.TRANSACTION_CANCELLED -> Icons.Filled.Cancel
        TimelineEventType.DISPUTE_OPENED -> Icons.Filled.Flag
        TimelineEventType.DISPUTE_RESOLVED -> Icons.Filled.CheckCircle
        TimelineEventType.BUYER_ACKNOWLEDGED -> Icons.Filled.CheckCircle
        TimelineEventType.PRODUCT_SELECTED -> Icons.Filled.PendingActions
        TimelineEventType.CONVERSATION_STARTED -> Icons.Filled.PendingActions
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(28.dp).background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.type.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (!e.note.isNullOrBlank()) {
                Text(e.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Text(
                "${if (e.byRole == Role.BUYER) "Buyer" else "Seller"} · ${formatDateLabel(e.at)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
    }
}