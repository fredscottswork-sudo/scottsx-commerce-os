package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/**
 * Manage saved delivery addresses. CRUD via the backend.
 */
@Composable
fun AddressesScreen(onBack: () -> Unit) {
    var addresses by remember { mutableStateOf<List<V2Client.Address>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<V2Client.Address?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            addresses = V2Client.fetchAddresses()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    SettingsScaffold(title = "Saved Addresses", onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ScottsTechXColors.BluePrimary)
                .clickable { editing = null; showDialog = true }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add new address", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsSectionHeader("Your addresses")
        Spacer(Modifier.height(6.dp))
        if (loading) {
            SettingsBlankHint("Loading...")
        } else if (addresses.isEmpty()) {
            SettingsBlankHint("No saved addresses yet. Add one above.")
        } else {
            addresses.forEach { addr ->
                AddressCard(
                    addr = addr,
                    onEdit = { editing = addr; showDialog = true },
                    onDelete = {
                        scope.launch {
                            V2Client.deleteAddress(addr.id)
                            reload()
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    if (showDialog) {
        AddressDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { payload ->
                scope.launch {
                    if (editing == null) {
                        V2Client.createAddress(payload)
                    } else {
                        V2Client.updateAddress(editing!!.id, payload)
                    }
                    showDialog = false
                    reload()
                }
            },
        )
    }
}

@Composable
private fun AddressCard(
    addr: V2Client.Address,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                addr.label,
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (addr.isDefault) {
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
        Spacer(Modifier.height(4.dp))
        Text("${addr.recipient} - ${addr.phone ?: ""}", fontSize = 12.sp, color = ScottsTechXColors.OnLightSecondary)
        Text(addr.line1, fontSize = 12.sp, color = ScottsTechXColors.OnLight)
        if (!addr.line2.isNullOrBlank()) Text(addr.line2, fontSize = 12.sp, color = ScottsTechXColors.OnLight)
        Text("${addr.city}${addr.region?.let { ", $it" }.orEmpty()} ${addr.country}", fontSize = 12.sp, color = ScottsTechXColors.OnLight)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit", color = ScottsTechXColors.BluePrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onEdit))
            Text("Delete", color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onDelete))
        }
    }
}

@Composable
private fun AddressDialog(
    initial: V2Client.Address?,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label ?: "Home") }
    var recipient by remember { mutableStateOf(initial?.recipient ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var line1 by remember { mutableStateOf(initial?.line1 ?: "") }
    var line2 by remember { mutableStateOf(initial?.line2 ?: "") }
    var city by remember { mutableStateOf(initial?.city ?: "") }
    var region by remember { mutableStateOf(initial?.region ?: "") }
    var postalCode by remember { mutableStateOf(initial?.postalCode ?: "") }
    var isDefault by remember { mutableStateOf(initial?.isDefault ?: false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New address" else "Edit address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldRow("Label", label) { label = it }
                FieldRow("Recipient", recipient) { recipient = it }
                FieldRow("Phone", phone) { phone = it }
                FieldRow("Street", line1) { line1 = it }
                FieldRow("Apt / Suite", line2) { line2 = it }
                FieldRow("City", city) { city = it }
                FieldRow("Region", region) { region = it }
                FieldRow("Postal code", postalCode ?: "") { postalCode = it }
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
                        .put("label", label)
                        .put("recipient", recipient)
                        .put("phone", phone)
                        .put("line1", line1)
                        .put("line2", line2)
                        .put("city", city)
                        .put("region", region)
                        .put("country", "UG")
                        .put("postalCode", postalCode)
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
