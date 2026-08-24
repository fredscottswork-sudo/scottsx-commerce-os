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
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.TransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage 4 — Transactions list screen.
 * Real data only — no placeholders. Filters by status + search.
 */
@Composable
fun TransactionsListScreen(
    onBack: () -> Unit = {},
    onOpenTransaction: (String) -> Unit = {},
    onOpenReceipt: (String) -> Unit = {},
) {
    val role = Session.roleOrNull() ?: Role.BUYER
    val userId = Session.userIdOrNull() ?: ""

    LaunchedEffect(userId, role) {
        // Recompose when the user signs in or out
    }

    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<TransactionStatus?>(null) }

    val items = remember(userId, role) {
        TransactionStore.agreementsForUser(userId, role)
    }
    val filtered = items.filter { ag ->
        val rev = ag.latestRevision
        val matchesStatus = statusFilter?.let { ag.status == it } ?: true
        val matchesQuery = if (query.isBlank()) true else {
            val q = query.trim().lowercase()
            ag.id.lowercase().contains(q) ||
                (rev?.productName?.lowercase()?.contains(q) ?: false) ||
                (if (role == Role.BUYER) ag.sellerDisplayName else ag.buyerDisplayName).lowercase().contains(q)
        }
        matchesStatus && matchesQuery
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (role == Role.SELLER) "My Sales" else "My Transactions",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "${items.size} total · Real ScottsTechX transactions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }

            // Search bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search by product, seller, transaction ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }

            // Status filter chips
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChipPill(
                        label = "All",
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                    )
                }
                items(TransactionStatus.values()) { st ->
                    FilterChipPill(
                        label = st.label,
                        selected = statusFilter == st,
                        onClick = { statusFilter = if (statusFilter == st) null else st },
                    )
                }
            }

            if (filtered.isEmpty()) {
                EmptyState(role = role, hasAny = items.isNotEmpty(), query = query)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { ag ->
                        TransactionRow(
                            transactionId = ag.id,
                            role = role,
                            onOpenTransaction = onOpenTransaction,
                            onOpenReceipt = onOpenReceipt,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(20.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF3B82F6).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            color = if (selected) Color(0xFF3B82F6) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TransactionRow(
    transactionId: String,
    role: Role,
    onOpenTransaction: (String) -> Unit,
    onOpenReceipt: (String) -> Unit,
) {
    val ag = remember(transactionId) { TransactionStore.agreementById(transactionId) }
    val rev = ag?.latestRevision
    if (ag == null || rev == null) return
    val counterparty = if (role == Role.BUYER) ag.sellerDisplayName else ag.buyerDisplayName
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTransaction(ag.id) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(ag.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    "#${ag.id.takeLast(6).uppercase()}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    TransactionStore.ugxFormat(rev.quantity * rev.agreedPriceUgx),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${rev.productName} × ${rev.quantity}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (role == Role.BUYER) "Seller" else "Buyer"}: $counterparty",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDateLabel(ag.updatedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            val receipt = remember(transactionId) {
                TransactionStore.receipts.firstOrNull { it.transactionId == transactionId }
            }
            if (receipt != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ReceiptLong, null, modifier = Modifier.size(14.dp), tint = Color(0xFF3B82F6))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Receipt ${receipt.number}",
                        fontSize = 11.sp,
                        color = Color(0xFF3B82F6),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "View",
                        modifier = Modifier.clickable { onOpenReceipt(receipt.number) },
                        fontSize = 11.sp,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TransactionStatus) {
    val (color, icon, label) = when (status) {
        TransactionStatus.DRAFT -> Triple(Color(0xFF6B7280), Icons.Filled.Search, status.label)
        TransactionStatus.PROPOSED -> Triple(Color(0xFFF59E0B), Icons.Filled.Search, status.label)
        TransactionStatus.BUYER_ACCEPTED, TransactionStatus.SELLER_ACCEPTED -> Triple(Color(0xFF06B6D4), Icons.Filled.CheckCircle, status.label)
        TransactionStatus.CONFIRMED -> Triple(Color(0xFF3B82F6), Icons.Filled.CheckCircle, status.label)
        TransactionStatus.IN_PROGRESS -> Triple(Color(0xFF8B5CF6), Icons.Filled.LocalShipping, status.label)
        TransactionStatus.COMPLETED -> Triple(Color(0xFF10B981), Icons.Filled.CheckCircle, status.label)
        TransactionStatus.CANCELLED -> Triple(Color(0xFFEF4444), Icons.Filled.Cancel, status.label)
        TransactionStatus.DISPUTED -> Triple(Color(0xFFEF4444), Icons.Filled.Cancel, status.label)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(6.dp).background(color, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyState(role: Role, hasAny: Boolean, query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📋", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (hasAny && query.isNotBlank()) "No transactions match \"$query\""
            else if (role == Role.SELLER) "No sales yet.\nCreate a receipt for your first sale."
            else "No transactions yet.\nOpen a product and message a seller to start.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

internal fun formatDateLabel(millis: Long): String {
    return SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(millis))
}