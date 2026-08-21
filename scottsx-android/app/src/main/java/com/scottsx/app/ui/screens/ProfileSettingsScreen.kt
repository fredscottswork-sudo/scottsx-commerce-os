package com.scottsx.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.UserSettings
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.components.StatusChip
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Buyer profile + preferences — the Android mirror of the web Settings page.
 *
 * Profile (name / phone / city) saves via PATCH /auth/me; the notification
 * toggles save via PATCH /me/preferences — the same two endpoints the
 * website's Settings tabs use.
 */
@Composable
fun ProfileSettingsScreen(onBack: () -> Unit) {
    val user = SessionCache.user.value
    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var phone by remember { mutableStateOf(user?.phone ?: "") }
    var city by remember { mutableStateOf(user?.city ?: "") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    // Preferences (notifications) — loaded from the backend.
    var prefs by remember { mutableStateOf(UserSettings()) }
    var prefsLoaded by remember { mutableStateOf(false) }
    var prefsSavedTick by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        prefs = V2Client.fetchUserSettings()
        prefsLoaded = true
    }

    fun savePrefs(next: UserSettings) {
        val previous = prefs
        prefs = next
        prefsSavedTick = false
        scope.launch {
            val ok = V2Client.saveSettings(next)
            if (!ok) prefs = previous else prefsSavedTick = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Scrolling form: keep the status bar and the gesture pill clear.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SettingsRow(title = "", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = onBack)
        Text("Edit profile", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Update your public buyer profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        InputField(value = name, onValueChange = { name = it; saved = false }, label = "Full name", placeholder = "Your name")
        Spacer(Modifier.height(14.dp))
        InputField(value = phone, onValueChange = { phone = it; saved = false }, label = "Phone", placeholder = "+256 7xx xxx xxx")
        Spacer(Modifier.height(14.dp))
        InputField(value = city, onValueChange = { city = it; saved = false }, label = "City", placeholder = "Kampala")
        Spacer(Modifier.height(8.dp))
        if (saved) {
            StatusChip("saved")
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton(
            text = "Save profile",
            loading = saving,
            onClick = {
                saving = true
                scope.launch {
                    val ok = V2Client.updateMe(displayName = name, phone = phone, city = city)
                    saved = ok
                    if (ok) {
                        SessionCache.user.value?.let { current ->
                            SessionCache.updateUser(current.copy(displayName = name, phone = phone, city = city))
                        }
                    }
                    saving = false
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Email: ${user?.email ?: "-"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )

        // ── Notifications (same toggles as the web Settings page) ───────────
        Spacer(Modifier.height(26.dp))
        Text("Notifications", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(
            "Choose what ScottsTechX can notify you about.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                PrefToggle(
                    title = "Order updates",
                    subtitle = "Payment, shipping and delivery alerts",
                    checked = prefs.notifyOrderUpdates,
                    enabled = prefsLoaded,
                    onToggle = { savePrefs(prefs.copy(notifyOrderUpdates = it)) },
                )
                Spacer(Modifier.height(6.dp))
                PrefToggle(
                    title = "Messages",
                    subtitle = "New chat messages from sellers",
                    checked = prefs.notifyMessages,
                    enabled = prefsLoaded,
                    onToggle = { savePrefs(prefs.copy(notifyMessages = it)) },
                )
                Spacer(Modifier.height(6.dp))
                PrefToggle(
                    title = "Deals & marketing",
                    subtitle = "Flash deals and personalised offers",
                    checked = prefs.notifyMarketing,
                    enabled = prefsLoaded,
                    onToggle = { savePrefs(prefs.copy(notifyMarketing = it)) },
                )
                if (prefsSavedTick) {
                    Spacer(Modifier.height(8.dp))
                    StatusChip("saved")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrefToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = ScottsTechXColors.BluePrimary),
        )
    }
}
