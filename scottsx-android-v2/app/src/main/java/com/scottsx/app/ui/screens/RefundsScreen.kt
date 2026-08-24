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
import org.json.JSONObject
import org.json.JSONArray

/** Refund requests — list + open new refund against a transaction or receipt. */
@Composable
fun RefundsScreen(onBack: () -> Unit) {
    val items = remember { mutableStateListOf<JSONObject>() }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            val arr = V2Client.fetchRefunds()
            items.clear()
            if (arr != null) for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            if (obj != null) {
                                items.add(obj as JSONObject)
                            }
                        }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    SettingsScaffold(title = "Refunds", onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ScottsTechXColors.BluePrimary)
                .clickable { showDialog = true }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Request a refund", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) SettingsBlankHint("Loading...")
        else if (items.isEmpty()) SettingsBlankHint("No refund requests yet.")
        else {
            items.forEach { refund ->
                RefundCard(refund)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
    if (showDialog) {
        RefundDialog(onDismiss = { showDialog = false }, onSubmit = { body ->
            scope.launch {
                V2Client.createRefund(body)
                showDialog = false
                reload()
            }
        })
    }
}

@Composable
private fun RefundCard(r: JSONObject) {
    val statusColor = when (r.optString("status")) {
        "approved", "paid" -> Color(0xFF059669)
        "rejected" -> Color(0xFFDC2626)
        else -> Color(0xFFF59E0B)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Refund for " + r.optString("transactionId").take(8),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    r.optString("status").replaceFirstChar { it.uppercase() },
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(r.optString("reason"), fontSize = 12.sp)
        Text(
            "Amount: ${formatMinor(r.optLong("amountMinor"), r.optString("currency"))}",
            fontSize = 12.sp,
            color = ScottsTechXColors.OnLightSecondary,
        )
    }
}

@Composable
private fun RefundDialog(onDismiss: () -> Unit, onSubmit: (JSONObject) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request refund") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldRow("Amount (UGX minor)", amount, hint = "e.g. 50000 = 500 UGX") { amount = it }
                FieldRow("Reason", reason, lines = 2) { reason = it }
                FieldRow("Transaction ID (optional)", transactionId) { transactionId = it }
                FieldRow("Notes", notes, lines = 2) { notes = it }
            }
        },
        confirmButton = {
            Text(
                "Submit",
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    val body = JSONObject()
                        .put("amountMinor", amount.toLongOrNull() ?: 0L)
                        .put("reason", reason)
                        .put("currency", "UGX")
                        .put("transactionId", transactionId.takeIf { it.isNotBlank() })
                        .put("notes", notes.takeIf { it.isNotBlank() })
                    onSubmit(body)
                },
            )
        },
        dismissButton = {
            Text("Cancel", color = ScottsTechXColors.OnLightSecondary, modifier = Modifier.clickable(onClick = onDismiss))
        },
    )
}

/** Return requests — list + request a return against a transaction/product. */
@Composable
fun ReturnsScreen(onBack: () -> Unit) {
    val items = remember { mutableStateListOf<JSONObject>() }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            loading = true
            val arr = V2Client.fetchReturns()
            items.clear()
            if (arr != null) for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            if (obj != null) {
                                items.add(obj as JSONObject)
                            }
                        }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    SettingsScaffold(title = "Returns", onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ScottsTechXColors.BluePrimary)
                .clickable { showDialog = true }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Request a return", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) SettingsBlankHint("Loading...")
        else if (items.isEmpty()) SettingsBlankHint("No return requests yet.")
        else {
            items.forEach { ret ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(12.dp),
                ) {
                    Text("Return request: ${ret.optString("reason")}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Quantity: ${ret.optInt("quantity")}", fontSize = 12.sp)
                    Text("Status: ${ret.optString("status").replaceFirstChar { it.uppercase() }}", fontSize = 12.sp, color = ScottsTechXColors.BluePrimary)
                }
            }
        }
    }
    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Request a return") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var reason by remember { mutableStateOf("") }
                    var quantity by remember { mutableStateOf("1") }
                    var description by remember { mutableStateOf("") }
                    FieldRow("Reason", reason, lines = 2) { reason = it }
                    FieldRow("Quantity", quantity) { quantity = it }
                    FieldRow("Description", description, lines = 2) { description = it }
                    // store values for the submit button
                    SideEffect {
                        returnReason = reason
                        returnQuantity = quantity
                        returnDescription = description
                    }
                }
            },
            confirmButton = {
                Text(
                    "Submit",
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        scope.launch {
                            V2Client.createReturn(
                                JSONObject()
                                    .put("reason", returnReason)
                                    .put("quantity", returnQuantity.toIntOrNull() ?: 1)
                                    .put("description", returnDescription.takeIf { it.isNotBlank() }),
                            )
                            showDialog = false
                            reload()
                        }
                    },
                )
            },
            dismissButton = {
                Text("Cancel", color = ScottsTechXColors.OnLightSecondary, modifier = Modifier.clickable { showDialog = false })
            },
        )
    }
}

// Module-level scratch space for the return-dialog form values.
private var returnReason: String = ""
private var returnQuantity: String = "1"
private var returnDescription: String = ""
