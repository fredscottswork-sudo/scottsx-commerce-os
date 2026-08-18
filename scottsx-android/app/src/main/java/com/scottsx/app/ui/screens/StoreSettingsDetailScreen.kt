package com.scottsx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.StoreSettings
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.StatusChip
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Store settings detail — routes the section slug ("store-profile",
 * "delivery", "policies", …) to one of 9 forms, all backed by
 * /seller/store-settings PATCH.
 */
@Composable
fun StoreSettingsDetailScreen(
    section: String,
    onBack: () -> Unit,
) {
    var settings by remember { mutableStateOf(StoreSettings()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settings = V2Client.fetchStoreSettings() ?: StoreSettings()
        loaded = true
    }

    fun set(block: (StoreSettings) -> StoreSettings) {
        settings = block(settings)
        saved = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = sectionLabel(section), onBack = onBack)
        Spacer(Modifier.height(6.dp))

        if (!loaded) {
            Spacer(Modifier.height(24.dp))
            com.scottsx.app.ui.components.LoadingRow()
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                when (section) {
                    "store-profile" -> {
                        TextFieldBlock("Store name") { InputField(value = settings.storeName, onValueChange = { new -> set { s -> s.copy(storeName = new) } }, label = "Store name") }
                        TextFieldBlock("Description") { InputField(value = settings.storeDescription, onValueChange = { new -> set { s -> s.copy(storeDescription = new) } }, label = "Store description", placeholder = "What do you sell?") }
                        TextFieldBlock("Logo URL") { InputField(value = settings.storeLogoUrl, onValueChange = { new -> set { s -> s.copy(storeLogoUrl = new) } }, label = "Logo image URL", placeholder = "https://…") }
                    }
                    "business-info" -> {
                        InputField(value = settings.legalName, onValueChange = { new -> set { s -> s.copy(legalName = new) } }, label = "Legal name")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.tin, onValueChange = { new -> set { s -> s.copy(tin = new) } }, label = "TIN", placeholder = "Tax identification number")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.businessEmail, onValueChange = { new -> set { s -> s.copy(businessEmail = new) } }, label = "Business email")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.businessPhone, onValueChange = { new -> set { s -> s.copy(businessPhone = new) } }, label = "Business phone")
                    }
                    "store-location" -> {
                        InputField(value = settings.address, onValueChange = { new -> set { s -> s.copy(address = new) } }, label = "Address")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.city, onValueChange = { new -> set { s -> s.copy(city = new) } }, label = "City")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.pickupInstructions, onValueChange = { new -> set { s -> s.copy(pickupInstructions = new) } }, label = "Pickup instructions")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.serviceRadiusKm.toString(), onValueChange = { new -> set { s -> s.copy(serviceRadiusKm = new.toIntOrNull() ?: 0) } }, label = "Service radius (km)")
                    }
                    "delivery" -> {
                        InputField(value = settings.deliveryFeeUgx.toString(), onValueChange = { new -> set { s -> s.copy(deliveryFeeUgx = new.toLongOrNull() ?: 0) } }, label = "Delivery fee (UGX)")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.freeAboveUgx.toString(), onValueChange = { new -> set { s -> s.copy(freeAboveUgx = new.toLongOrNull() ?: 0) } }, label = "Free delivery above (UGX)")
                        Spacer(Modifier.height(10.dp))
                        ToggleRow("Cash on delivery", settings.codEnabled) { new -> set { s -> s.copy(codEnabled = new) } }
                    }
                    "payments" -> {
                        InputField(value = settings.momoNumber, onValueChange = { new -> set { s -> s.copy(momoNumber = new) } }, label = "Mobile Money number", placeholder = "07xx xxx xxx")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.bankName, onValueChange = { new -> set { s -> s.copy(bankName = new) } }, label = "Bank name")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.bankAccount, onValueChange = { new -> set { s -> s.copy(bankAccount = new) } }, label = "Bank account")
                    }
                    "notifications" -> {
                        ToggleRow("Order updates", settings.notifOrderUpdates) { new -> set { s -> s.copy(notifOrderUpdates = new) } }
                        ToggleRow("Buyer messages", settings.notifBuyerMessages) { new -> set { s -> s.copy(notifBuyerMessages = new) } }
                        ToggleRow("Marketing", settings.notifMarketing) { new -> set { s -> s.copy(notifMarketing = new) } }
                        ToggleRow("Weekly digest", settings.notifWeeklyDigest) { new -> set { s -> s.copy(notifWeeklyDigest = new) } }
                    }
                    "security" -> {
                        ToggleRow("Two-factor authentication", settings.twoFactorEnabled) { new -> set { s -> s.copy(twoFactorEnabled = new) } }
                        Spacer(Modifier.height(8.dp))
                        Text("Security settings protect your store account.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "policies" -> {
                        InputField(value = settings.returnsWindowDays.toString(), onValueChange = { new -> set { s -> s.copy(returnsWindowDays = new.toIntOrNull() ?: 7) } }, label = "Returns window (days)")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.refundPolicy, onValueChange = { new -> set { s -> s.copy(refundPolicy = new) } }, label = "Refund policy", placeholder = "How refunds work…")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.terms, onValueChange = { new -> set { s -> s.copy(terms = new) } }, label = "Store terms", placeholder = "Your store's terms…")
                    }
                    "help" -> {
                        InputField(value = settings.contactEmail, onValueChange = { new -> set { s -> s.copy(contactEmail = new) } }, label = "Contact email")
                        Spacer(Modifier.height(10.dp))
                        InputField(value = settings.contactPhone, onValueChange = { new -> set { s -> s.copy(contactPhone = new) } }, label = "Contact phone")
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (saved) {
                    StatusChip("saved")
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    text = "Save ${sectionLabel(section)}",
                    loading = saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            saved = V2Client.updateStoreSettings(settings)
                            saving = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TextFieldBlock(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    content()
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { onChange(!checked) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

private fun sectionLabel(section: String): String =
    when (section) {
        "store-profile" -> "Store profile"
        "business-info" -> "Business info"
        "store-location" -> "Store location"
        "delivery" -> "Delivery"
        "payments" -> "Payments"
        "notifications" -> "Notifications"
        "security" -> "Security"
        "policies" -> "Policies"
        "help" -> "Help & contact"
        else -> "Store settings"
    }
