package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Single store-settings detail screen. Renders a form specific to the
 * [section] passed in. Each form:
 *   1. Pre-fills from the backend on first compose
 *   2. Lets the seller edit values
 *   3. PATCHes to /api/v1/seller/store-settings (or /seller/profile for
 *      business info) on Save
 *   4. Shows a green "Saved!" flash card on success
 */
@Composable
fun StoreSettingsDetailScreen(
    section: String,
    onBack: () -> Unit,
) {
    val (title, _) = sectionMeta(section)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var saveFlash by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    // Form state — initialised from the backend
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var logoUrl by remember { mutableStateOf("") }
    var legalName by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var pickup by remember { mutableStateOf("") }
    var radiusText by remember { mutableStateOf("10") }
    var feeText by remember { mutableStateOf("5000") }
    var freeAbove by remember { mutableStateOf("100000") }
    var codEnabled by remember { mutableStateOf(true) }
    var momoProvider by remember { mutableStateOf("MTN MoMo") }
    var momoNumber by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var bankAcct by remember { mutableStateOf("") }
    var orderUpdates by remember { mutableStateOf(true) }
    var buyerMessages by remember { mutableStateOf(true) }
    var marketing by remember { mutableStateOf(false) }
    var weeklyDigest by remember { mutableStateOf(true) }
    var currentPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var twoFA by remember { mutableStateOf(false) }
    var returnsDays by remember { mutableStateOf("14") }
    var refundNote by remember { mutableStateOf("") }
    var termsNote by remember { mutableStateOf("") }

    // Load existing values — keys mirror the PATCH schema rowToSettings()
    // in 12_Backend/src/modules/seller/store-settings.route.ts exactly,
    // so every field in this editor round-trips with the web.

    LaunchedEffect(section) {
        scope.launch {
            val s = V2Client.fetchStoreSettings()
            if (s != null) {
                name = s.optString("storeName")
                desc = s.optString("storeDescription")
                logoUrl = s.optString("storeLogoUrl")
                address = s.optString("address")
                pickup = s.optString("pickupInstructions")
                radiusText = s.optInt("serviceRadiusKm", 20).toString()
                feeText = s.optInt("deliveryFeeUgx", 0).toString()
                freeAbove = s.optInt("freeAboveUgx", 0).toString()
                codEnabled = s.optBoolean("codEnabled", true)
                momoNumber = s.optString("momoNumber")
                bankName = s.optString("bankName")
                bankAcct = s.optString("bankAccount")
                orderUpdates = s.optBoolean("notifOrderUpdates", true)
                buyerMessages = s.optBoolean("notifBuyerMessages", true)
                marketing = s.optBoolean("notifMarketing", false)
                weeklyDigest = s.optBoolean("notifWeeklyDigest", true)
                twoFA = s.optBoolean("twoFactorEnabled", false)
                returnsDays = s.optInt("returnsWindowDays", 7).toString()
                refundNote = s.optString("refundPolicy")
                termsNote = s.optString("terms")
                email = s.optString("businessEmail")
                phone = s.optString("businessPhone")
            }
            val p = V2Client.fetchSellerProfile()
            if (p != null) {
                legalName = p.optString("legalName", p.optString("businessName", p.optString("business_name")))
                taxId = p.optString("tin", p.optString("taxId", p.optString("tax_id")))
            }
            loaded = true
        }
    }

    fun doSave() {
        saving = true
        errorMessage = null
        scope.launch {
            // Every section writes PATCH /seller/store-settings keys the
            // backend actually maps (no unknown/chesstag keys — saved
            // fields must round-trip, otherwise "Saved" would be a lie).
            val ok = when (section) {
                "store-profile" -> V2Client.updateStoreSettings(JSONObject()
                    .put("storeName", name)
                    .put("storeDescription", desc)
                    .put("storeLogoUrl", logoUrl))
                "business-info" -> V2Client.updateStoreSettings(JSONObject()
                    .put("legalName", legalName)
                    .put("tin", taxId)
                    .put("businessEmail", email)
                    .put("businessPhone", phone))
                "store-location" -> V2Client.updateStoreSettings(JSONObject()
                    .put("address", address)
                    .put("pickupInstructions", pickup)
                    .put("serviceRadiusKm", radiusText.toIntOrNull() ?: 20))
                "delivery" -> V2Client.updateStoreSettings(JSONObject()
                    .put("deliveryFeeUgx", feeText.toIntOrNull() ?: 0)
                    .put("freeAboveUgx", freeAbove.toIntOrNull() ?: 0)
                    .put("codEnabled", codEnabled))
                "payments" -> V2Client.updateStoreSettings(JSONObject()
                    .put("momoNumber", momoNumber)
                    .put("bankName", bankName)
                    .put("bankAccount", bankAcct))
                "notifications" -> V2Client.updateStoreSettings(JSONObject()
                    .put("notifOrderUpdates", orderUpdates)
                    .put("notifBuyerMessages", buyerMessages)
                    .put("notifMarketing", marketing)
                    .put("notifWeeklyDigest", weeklyDigest))
                "security" -> {
                    var okAll = true
                    if (newPwd.isNotBlank()) {
                        okAll = okAll && currentPwd.isNotBlank() &&
                            V2Client.changePassword(currentPwd, newPwd)
                    }
                    okAll = okAll && V2Client.updateStoreSettings(JSONObject().put("twoFactorEnabled", twoFA))
                    okAll
                }
                "policies" -> V2Client.updateStoreSettings(JSONObject()
                    .put("returnsWindowDays", returnsDays.toIntOrNull() ?: 7)
                    .put("refundPolicy", refundNote)
                    .put("terms", termsNote))
                "help" -> true
                else -> true
            }
            saving = false
            if (ok) {
                saveFlash = true
                if (section == "security") { currentPwd = ""; newPwd = "" }
            } else {
                errorMessage = "Failed to save — check your details and connection"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.PanelLight)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.BluePrimaryDark)
                .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (section) {
                "store-profile" -> {
                    Field("Store name", name) { name = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Description", desc) { desc = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Logo URL (https://…)", logoUrl, hint = "https://…") { logoUrl = it }
                }
                "business-info" -> {
                    Field("Legal business name", legalName) { legalName = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Tax ID / TIN", taxId) { taxId = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Business email", email) { email = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Business phone", phone) { phone = it }
                }
                "store-location" -> {
                    Field("Store address", address) { address = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Pickup instructions", pickup) { pickup = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Service radius (km)", radiusText) { radiusText = it }
                }
                "delivery" -> {
                    Field("Delivery fee (UGX)", feeText) { feeText = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Free delivery above (UGX)", freeAbove) { freeAbove = it }
                    Spacer(Modifier.height(14.dp))
                    ToggleRow("Cash on delivery", codEnabled) { codEnabled = it }
                }
                "payments" -> {
                    Field("Mobile money provider", momoProvider) { momoProvider = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Mobile money number", momoNumber) { momoNumber = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Bank name (optional)", bankName) { bankName = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Bank account (optional)", bankAcct) { bankAcct = it }
                }
                "notifications" -> {
                    ToggleRow("Order updates", orderUpdates) { orderUpdates = it }
                    ToggleRow("Buyer messages", buyerMessages) { buyerMessages = it }
                    ToggleRow("Marketing & promotions", marketing) { marketing = it }
                    ToggleRow("Weekly sales digest", weeklyDigest) { weeklyDigest = it }
                }
                "security" -> {
                    Field("Current password", currentPwd) { currentPwd = it }
                    Spacer(Modifier.height(10.dp))
                    Field("New password", newPwd) { newPwd = it }
                    Spacer(Modifier.height(14.dp))
                    ToggleRow("Two-factor authentication", twoFA) { twoFA = it }
                }
                "policies" -> {
                    Field("Returns window (days)", returnsDays) { returnsDays = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Refund policy", refundNote) { refundNote = it }
                    Spacer(Modifier.height(10.dp))
                    Field("Store terms", termsNote) { termsNote = it }
                }
                "help" -> {
                    Text(
                        "Need help managing your store? Reach out to ScottsTechX Seller Support.",
                        color = ScottsTechXColors.OnPanel,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Email: [email protected]",
                        color = ScottsTechXColors.OnPanelSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        "Phone: +256 800 100 100",
                        color = ScottsTechXColors.OnPanelSecondary,
                        fontSize = 12.sp,
                    )
                }
                else -> Text("Unknown section: $section", color = ScottsTechXColors.OnPanelSecondary)
            }

            Spacer(Modifier.height(20.dp))
            SettingsSaveButton(saving = saving, onSave = ::doSave)

            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = Color(0xFFB71C1C),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (saveFlash) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color(0xFF1B5E20),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Saved!", color = Color(0xFF1B5E20), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun sectionMeta(section: String): Pair<String, String> = when (section) {
    "store-profile" -> "Store Profile" to "Name, logo, description"
    "business-info" -> "Business Information" to "Verify your business"
    "store-location" -> "Store Location" to "Address and pickup points"
    "delivery" -> "Delivery Settings" to "Radius, fee, options"
    "payments" -> "Payment Settings" to "Mobile money, bank accounts"
    "notifications" -> "Notification Settings" to "Buyers, orders, marketing"
    "security" -> "Security" to "Password, 2FA"
    "policies" -> "Store Policies" to "Returns, refunds, terms"
    "help" -> "Help & Support" to "FAQs, contact us"
    else -> section.replaceFirstChar { it.uppercase() } to ""
}

@Composable
private fun Field(
    label: String,
    value: String,
    hint: String? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = ScottsTechXColors.OnCard) },
        placeholder = if (hint != null) { { Text(hint, color = ScottsTechXColors.OnCardSecondary) } } else null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = ScottsTechXColors.OnCard,
            fontSize = 15.sp,
        ),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = ScottsTechXColors.OnCard,
            unfocusedTextColor = ScottsTechXColors.OnCard,
            focusedBorderColor = ScottsTechXColors.BluePrimary,
            unfocusedBorderColor = ScottsTechXColors.OnCardSecondary.copy(alpha = 0.3f),
            cursorColor = ScottsTechXColors.BluePrimary,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedLabelColor = ScottsTechXColors.BluePrimary,
            unfocusedLabelColor = ScottsTechXColors.OnCardSecondary,
        ),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = ScottsTechXColors.OnPanel,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSaveButton(saving: Boolean, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.BluePrimary)
            .clickable(onClick = onSave)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (saving) {
            Text("Saving...", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        } else {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
