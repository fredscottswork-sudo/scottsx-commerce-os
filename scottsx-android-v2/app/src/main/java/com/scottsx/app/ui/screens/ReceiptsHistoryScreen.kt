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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.TransactionStatus

/**
 * Stage 4 — Receipts history screen.
 * Real data only.
 */
@Composable
fun ReceiptsHistoryScreen(
    onBack: () -> Unit = {},
    onOpenReceipt: (String) -> Unit = {},
) {
    val role = Session.roleOrNull() ?: Role.BUYER
    val userId = Session.userIdOrNull() ?: ""
    var query by remember { mutableStateOf("") }
    val items = remember(userId, role) { TransactionStore.receiptsForUser(userId, role) }
    val filtered = items.filter { r ->
        if (query.isBlank()) return@filter true
        val q = query.trim().lowercase()
        r.number.lowercase().contains(q) ||
            r.buyerDisplayName.lowercase().contains(q) ||
            r.lines.firstOrNull()?.productName?.lowercase()?.contains(q) == true ||
            r.sellerStoreName.lowercase().contains(q)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (role == Role.SELLER) "Receipts" else "My Receipts",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                    )
                    Text("${items.size} receipt(s) — payment recorded by seller", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }

            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search by receipt number, buyer, product") },
                singleLine = true,
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (items.isEmpty()) "No receipts yet."
                        else "No receipts match \"$query\".",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.number }) { r ->
                        ReceiptRow(
                            receipt = r,
                            role = role,
                            onOpen = { onOpenReceipt(r.number) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: com.scottsx.app.data.domain.Receipt, role: Role, onOpen: () -> Unit) {
    val statusColor = when (receipt.status) {
        TransactionStatus.COMPLETED -> Color(0xFF10B981)
        TransactionStatus.CONFIRMED -> Color(0xFF3B82F6)
        TransactionStatus.CANCELLED -> Color(0xFFEF4444)
        TransactionStatus.DISPUTED -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(Color(0xFF3B82F6).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ReceiptLong, null, tint = Color(0xFF3B82F6))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(receipt.number, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "${receipt.lines.firstOrNull()?.productName ?: "Receipt"} · ${receipt.buyerDisplayName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Text(
                    "${receipt.paymentMethod.label} · ${receipt.issuedAtLabel}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(TransactionStore.ugxFormat(receipt.totalUgx), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        receipt.status.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (role == Role.SELLER) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        TransactionStore.duplicateReceiptAsTemplate(receipt.number)
                    }) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(10.dp), tint = Color(0xFF6B7280))
                        Spacer(Modifier.width(2.dp))
                        Text("Duplicate", fontSize = 9.sp, color = Color(0xFF6B7280))
                    }
                }
            }
        }
    }
}