package com.scottsx.app.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.firebase.FirebaseAuthRepository
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Firebase email-link signup.
 *
 *   1. Enter email -> "Send verification email"
 *   2. Firebase emails a REAL link
 *   3. Tap "I've verified — continue" -> reload() + isEmailVerified
 *   4. Only then can the account be created/exchanged for a JWT
 */
@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onLoggedIn: (role: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    var emailSent by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val emailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SettingsRow(title = "", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = onBack)
        Text("Create your account", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Real email verification — check your inbox for the link.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        InputField(value = name, onValueChange = { name = it }, label = "Full name", placeholder = "Kato Fred")
        Spacer(Modifier.height(12.dp))
        InputField(value = email, onValueChange = { email = it }, label = "Email", placeholder = "you@example.com", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        InputField(value = phone, onValueChange = { phone = it }, label = "Phone (optional)", placeholder = "+256 7xx xxx xxx", keyboardType = KeyboardType.Phone)
        Spacer(Modifier.height(12.dp))
        InputField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true, placeholder = "min 6 characters")
        Spacer(Modifier.height(12.dp))
        InputField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            isPassword = true,
            isError = confirm.isNotEmpty() && confirm != password,
            errorMessage = if (confirm.isNotEmpty() && confirm != password) "Passwords do not match" else null,
        )

        Spacer(Modifier.height(18.dp))
        info?.let {
            Text(it, color = ScottsTechXColors.SuccessGreen, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        error?.let {
            Text(it, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }

        // Step 1 — send the Firebase verification email.
        PrimaryButton(
            text = if (emailSent) "Resend verification email" else "Send verification email",
            enabled = emailValid && password.length >= 6 && !emailSent,
            loading = busy,
            onClick = {
                busy = true
                error = null
                info = null
                scope.launch {
                    val err = FirebaseAuthRepository.signUpAndSendVerification(email.trim(), password)
                    if (err == null) {
                        emailSent = true
                        info = "Verification email sent to $email — open the link in the email, then come back and continue."
                    } else {
                        error = err
                    }
                    busy = false
                }
            },
        )

        Spacer(Modifier.height(10.dp))

        // Step 2 — confirm verification and finish signup.
        PrimaryButton(
            text = if (verified) "Create account ✓" else "I've verified — continue",
            enabled = emailSent && name.isNotBlank() && confirm == password,
            loading = busy,
            onClick = {
                busy = true
                error = null
                info = null
                scope.launch {
                    val ok = FirebaseAuthRepository.reloadAndCheckVerified()
                    if (!ok) {
                        error = "Not verified yet — open the link in your email and try again."
                        busy = false
                        return@launch
                    }
                    verified = true
                    // Exchange Firebase identity for a ScottsTechX JWT + create profile.
                    val exchanged = FirebaseAuthRepository.exchangeForJwt()
                    if (!exchanged) {
                        // Fall back to local register so the demo keeps working offline.
                        val local = V2Client.register(email.trim(), password, name, phone, "buyer")
                        if (local != null) {
                            SessionCache.save(
                                local.token,
                                com.scottsx.app.CurrentUser(
                                    id = local.user.id, email = local.user.email,
                                    displayName = local.user.displayName, phone = local.user.phone,
                                    role = local.user.role, emailVerified = local.user.emailVerified,
                                    profilePhotoUrl = local.user.profilePhotoUrl, city = local.user.city,
                                ),
                            )
                        }
                    }
                    busy = false
                    onLoggedIn(SessionCache.user.value?.role)
                }
            },
        )
    }
}
