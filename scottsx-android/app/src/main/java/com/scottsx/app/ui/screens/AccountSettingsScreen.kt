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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
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
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Account settings — email, password change, security info. */
@Composable
fun AccountSettingsScreen(onBack: () -> Unit) {
    val user = SessionCache.user.value
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
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
        SettingsRow(title = "", icon = Icons.Filled.KeyboardArrowLeft, onClick = onBack)
        Text("Account settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Signed in as ${user?.email ?: "-"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Text("Change password", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        InputField(value = oldPassword, onValueChange = { oldPassword = it }, label = "Current password", isPassword = true)
        Spacer(Modifier.height(12.dp))
        InputField(value = newPassword, onValueChange = { newPassword = it }, label = "New password", isPassword = true, placeholder = "min 6 characters")
        Spacer(Modifier.height(8.dp))
        message?.let {
            Text(it, color = if (isError) ScottsTechXColors.ErrorRed else ScottsTechXColors.SuccessGreen, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        PrimaryButton(
            text = "Update password",
            loading = busy,
            enabled = oldPassword.isNotBlank() && newPassword.length >= 6,
            onClick = {
                busy = true
                message = null
                scope.launch {
                    val ok = V2Client.changePassword(oldPassword, newPassword)
                    message = if (ok) "Password updated." else "Could not update — check your current password."
                    isError = !ok
                    if (ok) {
                        oldPassword = ""
                        newPassword = ""
                    }
                    busy = false
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Security: local accounts use bcrypt hashes; Firebase accounts are verified via email links.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
