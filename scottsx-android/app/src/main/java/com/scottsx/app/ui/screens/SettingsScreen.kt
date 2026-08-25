package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.preferences.ThemeMode
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Stage 5 — Settings screen.
 *
 * Real persistence via [V2Client.loadSettings] / [V2Client.saveSettings].
 * Falls back to local defaults if the backend is unreachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAiPersonalization: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themePref = remember { ThemePreference.get(context) }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }

    var theme by remember { mutableStateOf("system") }
    var language by remember { mutableStateOf("en") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var notificationSound by remember { mutableStateOf(true) }
    var locationSharing by remember { mutableStateOf("approximate") }
    var privacyShowReceipts by remember { mutableStateOf(true) }
    var privacyShowTransactions by remember { mutableStateOf(true) }
    var aiPersonalizationEnabled by remember { mutableStateOf(true) }
    var preferredCurrency by remember { mutableStateOf("UGX") }

    suspend fun save(patch: org.json.JSONObject) {
        isSaving = true
        V2Client.saveSettings(patch)
        // Mirror to local theme pref so the UI re-skins immediately.
        if (patch.has("theme")) {
            val t = patch.optString("theme")
            themePref.set(runCatching { ThemeMode.valueOf(t.uppercase()) }.getOrDefault(ThemeMode.SYSTEM))
        }
        isSaving = false
        savedFlash = true
    }

    LaunchedEffect(Unit) {
        val s = V2Client.loadSettings()
        if (s != null) {
            theme = s.theme
            language = s.language
            notificationsEnabled = s.notificationsEnabled
            notificationSound = s.notificationSound
            locationSharing = s.locationSharing
            privacyShowReceipts = s.privacyShowReceipts
            privacyShowTransactions = s.privacyShowTransactions
            aiPersonalizationEnabled = s.aiPersonalizationEnabled
            preferredCurrency = s.preferredCurrency
            themePref.set(runCatching { ThemeMode.valueOf(s.theme.uppercase()) }.getOrDefault(ThemeMode.SYSTEM))
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScottsTechXColors.Background,
                    titleContentColor = ScottsTechXColors.TextPrimary,
                    navigationIconContentColor = ScottsTechXColors.TextPrimary,
                ),
            )
        },
        containerColor = ScottsTechXColors.Background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = ScottsTechXColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // ----- Appearance -----
            SettingsSectionCard(title = "Appearance") {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = ScottsTechXColors.Primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Theme", color = ScottsTechXColors.TextPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            "Light, dark, or follow system",
                            color = ScottsTechXColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeChip("Light", theme == "light") { scope.launch { save(org.json.JSONObject().put("theme", "light")); theme = "light" } }
                    ThemeChip("Dark", theme == "dark") { scope.launch { save(org.json.JSONObject().put("theme", "dark")); theme = "dark" } }
                    ThemeChip("System", theme == "system") { scope.launch { save(org.json.JSONObject().put("theme", "system")); theme = "system" } }
                }
                Divider(color = ScottsTechXColors.Divider)
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Language", color = ScottsTechXColors.TextPrimary, fontWeight = FontWeight.Medium)
                        Text("English (UK)", color = ScottsTechXColors.TextSecondary, fontSize = 12.sp)
                    }
                    Text(
                        preferredCurrency,
                        color = ScottsTechXColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            // ----- Notifications -----
            SettingsSectionCard(title = "Notifications") {
                SettingsToggleRow(
                    icon = if (notificationsEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                    title = "Push notifications",
                    subtitle = "Messages, transactions, receipt updates",
                    checked = notificationsEnabled,
                ) { v ->
                    notificationsEnabled = v
                    scope.launch { save(org.json.JSONObject().put("notificationsEnabled", v)) }
                }
                Divider(color = ScottsTechXColors.Divider)
                SettingsToggleRow(
                    icon = if (notificationSound) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    title = "Sound",
                    subtitle = "Play a tone on new messages",
                    checked = notificationSound,
                ) { v ->
                    notificationSound = v
                    scope.launch { save(org.json.JSONObject().put("notificationSound", v)) }
                }
            }

            // ----- Location & privacy -----
            SettingsSectionCard(title = "Location & Privacy") {
                SettingsToggleRow(
                    icon = if (locationSharing != "off") Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                    title = "Location sharing",
                    subtitle = when (locationSharing) {
                        "precise" -> "Precise — used for nearby sellers"
                        "approximate" -> "Approximate — used for city-level nearby"
                        else -> "Off — nearby will be hidden"
                    },
                    checked = locationSharing != "off",
                ) { v ->
                    val next = if (v) "approximate" else "off"
                    locationSharing = next
                    scope.launch { save(org.json.JSONObject().put("locationSharing", next)) }
                }
                Divider(color = ScottsTechXColors.Divider)
                SettingsToggleRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Show receipts on profile",
                    subtitle = "Sellers can see receipts in your history",
                    checked = privacyShowReceipts,
                ) { v ->
                    privacyShowReceipts = v
                    scope.launch { save(org.json.JSONObject().put("privacyShowReceipts", v)) }
                }
                Divider(color = ScottsTechXColors.Divider)
                SettingsToggleRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Show transactions on profile",
                    subtitle = "Counterparties can see the transaction timeline",
                    checked = privacyShowTransactions,
                ) { v ->
                    privacyShowTransactions = v
                    scope.launch { save(org.json.JSONObject().put("privacyShowTransactions", v)) }
                }
            }

            // ----- AI -----
            SettingsSectionCard(title = "AI Personalization") {
                SettingsToggleRow(
                    icon = Icons.Filled.SmartToy,
                    title = "Personalize my AI",
                    subtitle = "Let ScottsTechX AI learn from your searches and preferences",
                    checked = aiPersonalizationEnabled,
                ) { v ->
                    aiPersonalizationEnabled = v
                    scope.launch { save(org.json.JSONObject().put("aiPersonalizationEnabled", v)) }
                }
                Divider(color = ScottsTechXColors.Divider)
                Row(
                    Modifier.fillMaxWidth().padding(16.dp).clickable { onOpenAiPersonalization() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = ScottsTechXColors.Primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Manage AI memory", color = ScottsTechXColors.TextPrimary, fontWeight = FontWeight.Medium)
                        Text("View / clear what ScottsTechX AI remembers about you", color = ScottsTechXColors.TextSecondary, fontSize = 12.sp)
                    }
                    Text("›", color = ScottsTechXColors.TextSecondary, fontSize = 24.sp)
                }
            }

            // ----- Save indicator -----
            if (isSaving) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = ScottsTechXColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
            } else if (savedFlash) {
                Text(
                    "Saved",
                    color = ScottsTechXColors.Primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ScottsTechXColors.Surface),
    ) {
        Text(
            title.uppercase(),
            color = ScottsTechXColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ScottsTechXColors.Primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ScottsTechXColors.TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ScottsTechXColors.TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ScottsTechXColors.Primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = ScottsTechXColors.Divider,
            ),
        )
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) ScottsTechXColors.Primary else ScottsTechXColors.Surface
    val fg = if (selected) Color.White else ScottsTechXColors.TextPrimary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            label,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}