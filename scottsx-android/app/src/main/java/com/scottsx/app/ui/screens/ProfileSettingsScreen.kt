package com.scottsx.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.components.StatusChip
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Buyer profile editor — updates display name + phone via PATCH /auth/me. */
@Composable
fun ProfileSettingsScreen(onBack: () -> Unit) {
    val user = SessionCache.user.value
    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var phone by remember { mutableStateOf(user?.phone ?: "") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Scrolling form: needs the status bar AND the gesture pill kept
            // clear, otherwise the back arrow hides under the clock and the
            // submit button under the navigation bar.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SettingsRow(title = "", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = onBack)
        Text("Edit profile", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Update your public buyer profile.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        InputField(value = name, onValueChange = { name = it; saved = false }, label = "Full name", placeholder = "Your name")
        Spacer(Modifier.height(14.dp))
        InputField(value = phone, onValueChange = { phone = it; saved = false }, label = "Phone", placeholder = "+256 7xx xxx xxx")
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
                    val ok = V2Client.updateMe(displayName = name, phone = phone)
                    saved = ok
                    SessionCache.user.value?.let { current ->
                        SessionCache.updateUser(current.copy(displayName = name, phone = phone))
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
    }
}
