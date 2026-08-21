package com.scottsx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.GoogleAuthButton
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.OrDivider
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Email/password login (local backend auth). */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (role: String?) -> Unit,
    onGoSignUp: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
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
        Text("Welcome back", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Sign in to ScottsTechX",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        InputField(value = email, onValueChange = { email = it }, label = "Email", placeholder = "you@example.com", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(14.dp))
        InputField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true, placeholder = "••••••••")
        Spacer(Modifier.height(10.dp))
        error?.let {
            Text(it, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        PrimaryButton(
            text = "Sign in",
            loading = loading,
            onClick = {
                loading = true
                error = null
                scope.launch {
                    val result = V2Client.login(email.trim(), password)
                    if (result != null && result.token.isNotBlank()) {
                        SessionCache.save(
                            result.token,
                            com.scottsx.app.CurrentUser(
                                id = result.user.id,
                                email = result.user.email,
                                displayName = result.user.displayName,
                                phone = result.user.phone,
                                role = result.user.role,
                                emailVerified = result.user.emailVerified,
                                profilePhotoUrl = result.user.profilePhotoUrl,
                                city = result.user.city,
                            ),
                        )
                        onLoggedIn(result.user.role)
                    } else {
                        error = "Invalid email or password"
                    }
                    loading = false
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        OrDivider()
        Spacer(Modifier.height(16.dp))
        GoogleAuthButton(
            onSuccess = { onLoggedIn(SessionCache.user.value?.role) },
            onError = { error = it },
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                "New here?  ",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Create an account",
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickableNoRipple(onGoSignUp),
            )
        }
    }
}

// Named clickableNoRipple, but it never removed the ripple - and the
// fully-qualified call could not resolve without the import. Use the real
// modifier directly at the call site instead of wrapping it.
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
