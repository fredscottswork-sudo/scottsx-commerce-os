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
import com.scottsx.app.ui.components.SettingsSectionHeader
import com.scottsx.app.ui.components.SettingsBlankHint
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun PaymentMethodsScreen(onBack: () -> Unit) {
    var methods by remember { mutableStateOf<List<V2Client.PaymentMethod>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            methods = V2Client.fetchPaymentMethods()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    SettingsScaffold(title = "Payment Methods", onBack = onBack) {
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
                Text("Add payment method", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsSectionHeader("Saved methods")
        Spacer(Modifier.height(6.dp))
        if (loading) {
            SettingsBlankHint("Loading...")
        } else if (methods.isEmpty()) {
            SettingsBlankHint("No payment methods saved yet.")
        } else {
            methods.forEach { pm ->
                PaymentCard(
                    pm = pm,
                    onDelete = {
                        scope.launch {
                            V2Client.deletePaymentMethod(pm.id)
                            reload()
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    if (showDialog) {
        PaymentMethodDialog(
            onDismiss = { showDialog = false },
            onSave = { body ->
                scope.launch {
                    V2Client.createPaymentMethod(body)
                    showDialog = false
                    reload()
                }
            },
        )
    }
}

@Composable
private fun PaymentCard(pm: V2Client.PaymentMethod, onDelete: () -> Unit) {
    val icon = when (pm.kind) {
        "mobile_money" -> Icons.Filled.Phone
        "card" -> Icons.Filled.CreditCard
        "bank" -> Icons.Filled.AccountBalance
        else -> Icons.Filled.Payments
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pm.label,
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    pm.account,
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 12.sp,
                )
            }
            if (pm.isDefault) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("Default", color = ScottsTechXColors.BluePrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Delete", color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onDelete))
        }
    }
}

@Composable
private fun PaymentMethodDialog(
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    var kindIndex by remember { mutableStateOf(0) }
    var provider by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var expiresAt by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    val kinds = listOf("mobile_money", "card", "bank", "cash")

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add payment method") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Type", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ScottsTechXColors.OnLightSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEachIndexed { i, k ->
                        val sel = kindIndex == i
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) ScottsTechXColors.BluePrimary else Color.White)
                                .clickable { kindIndex = i }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                when (k) {
                                    "mobile_money" -> "Mobile Money"
                                    "card" -> "Card"
                                    "bank" -> "Bank"
                                    else -> "Cash"
                                },
                                color = if (sel) Color.White else ScottsTechXColors.OnLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                FieldRow("Provider", provider, hint = "MTN, Visa, Mastercard") { provider = it }
                FieldRow("Label", label) { label = it }
                FieldRow("Account / Number", account) { account = it }
                if (kindIndex == 1) FieldRow("Expires", expiresAt, hint = "YYYY-MM-DD") { expiresAt = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Text("Set as default", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Text(
                "Save",
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    val body = JSONObject()
                        .put("kind", kinds[kindIndex])
                        .put("provider", provider)
                        .put("label", label)
                        .put("account", account)
                        .put("expiresAt", expiresAt)
                        .put("isDefault", isDefault)
                    onSave(body)
                },
            )
        },
        dismissButton = {
            Text("Cancel", color = ScottsTechXColors.OnLightSecondary, modifier = Modifier.clickable(onClick = onDismiss))
        },
    )
}
