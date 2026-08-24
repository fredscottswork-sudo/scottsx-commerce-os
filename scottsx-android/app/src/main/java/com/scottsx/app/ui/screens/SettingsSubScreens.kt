package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.preferences.NotificationPrefs
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.SettingsScaffold
import com.scottsx.app.ui.components.SettingsSectionHeader
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { NotificationPrefs.get(ctx) }
    val push by prefs.pushFlow.collectAsState()
    val email by prefs.emailFlow.collectAsState()
    val sms by prefs.smsFlow.collectAsState()
    val orders by prefs.ordersFlow.collectAsState()
    val promos by prefs.promosFlow.collectAsState()

    SettingsScaffold(title = "Notifications", onBack = onBack) {
        SettingsSectionHeader("Channels")
        Spacer(Modifier.height(6.dp))
        ToggleRow("Push notifications", "Receive push on your device", push) { prefs.setPush(it) }
        ToggleRow("Email", "Order updates, receipts", email) { prefs.setEmail(it) }
        ToggleRow("SMS", "Urgent security alerts", sms) { prefs.setSms(it) }

        Spacer(Modifier.height(16.dp))
        SettingsSectionHeader("What you receive")
        Spacer(Modifier.height(6.dp))
        ToggleRow("Order updates", "Placed, shipped, delivered", orders) { prefs.setOrders(it) }
        ToggleRow("Promotions & deals", "Sales, coupons, new arrivals", promos) { prefs.setPromos(it) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            subtitle?.let { Text(it, fontSize = 12.sp, color = ScottsTechXColors.OnLightSecondary) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Language picker.
 */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { com.scottsx.app.data.preferences.UserPrefs.get(ctx) }
    var current by remember { mutableStateOf(prefs.getLanguage()) }
    val languages = listOf(
        "en" to "English",
        "sw" to "Kiswahili",
        "fr" to "Français",
        "lg" to "Luganda",
        "ach" to "Acholi",
    )
    SettingsScaffold(title = "Language", onBack = onBack) {
        languages.forEach { (code, name) ->
            val sel = current == code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { current = code; prefs.setLanguage(code) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(code.uppercase(), fontSize = 12.sp, color = ScottsTechXColors.OnLightSecondary)
                if (sel) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Check, null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * Currency picker (UGX, KES, USD, EUR, GBP, NGN, TZS, RWF).
 */
@Composable
fun CurrencyScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { com.scottsx.app.data.preferences.UserPrefs.get(ctx) }
    var current by remember { mutableStateOf(prefs.getCurrency()) }
    val currencies = listOf(
        "UGX" to "Ugandan Shilling",
        "KES" to "Kenyan Shilling",
        "TZS" to "Tanzanian Shilling",
        "RWF" to "Rwandan Franc",
        "NGN" to "Nigerian Naira",
        "USD" to "US Dollar",
        "EUR" to "Euro",
        "GBP" to "British Pound",
    )
    SettingsScaffold(title = "Currency", onBack = onBack) {
        currencies.forEach { (code, name) ->
            val sel = current == code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { current = code; prefs.setCurrency(code) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(code, modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ScottsTechXColors.BluePrimary)
                Text(name, modifier = Modifier.weight(1f), fontSize = 14.sp)
                if (sel) Icon(Icons.Filled.Check, null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Account activity — audit log.
 */
@Composable
fun AuditScreen(onBack: () -> Unit) {
    val items = remember { mutableStateListOf<org.json.JSONObject>() }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            val arr = V2Client.fetchMyAudit()
            items.clear()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) {
                        items.add(obj as JSONObject)
                    }
                }
            }
            loading = false
        }
    }
    SettingsScaffold(title = "Account Activity", onBack = onBack) {
        Text(
            "Last 100 account actions",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (loading) {
            Text("Loading...", fontSize = 13.sp, color = ScottsTechXColors.OnLightSecondary)
        } else if (items.isEmpty()) {
            Text("No recent activity.", fontSize = 13.sp, color = ScottsTechXColors.OnLightSecondary)
        } else {
            items.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(10.dp),
                ) {
                    Text(item.optString("action"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(item.optString("createdAt"), fontSize = 11.sp, color = ScottsTechXColors.OnLightSecondary)
                    if (!item.isNull("resource")) {
                        Text(item.optString("resource"), fontSize = 11.sp, color = ScottsTechXColors.OnLightSecondary)
                    }
                }
            }
        }
    }
}
