package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.AuthErrorSlot
import com.scottsx.app.ui.components.AuthStatusSlot
import com.scottsx.app.ui.components.BrandedAuthHeader
import com.scottsx.app.ui.components.BrandedAuthScaffold
import com.scottsx.app.ui.components.BrandedFooterLink
import com.scottsx.app.ui.components.PasswordField
import com.scottsx.app.ui.components.PrimaryCtaButton
import com.scottsx.app.ui.components.StyledAuthField
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Password reset — two stages, both fully native:
 *
 *  1. REQUEST: the user types their email (or phone). We call
 *     POST /auth/forgot-password which mails a single-use token
 *     (the same email the website's flow sends — the link in it opens
 *     the web reset page if they'd rather finish there).
 *  2. REDEEM: the token from the email can be pasted (or typed) right
 *     here with a new password → POST /auth/reset-password. No browser
 *     round-trip needed; the account then signs in from Login.
 */
@Composable
fun ResetPasswordScreen(
    role: Role,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var stage by remember { mutableStateOf(1) }              // 1=request, 2=redeem, 3=done
    var identifier by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BrandedAuthScaffold(role = role, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (stage) {
                // ── 1. Request the reset email ──────────────────────────
                1 -> {
                    BrandedAuthHeader(
                        title = "Reset your password",
                        sub = "Enter the email or phone on your account — we'll send a reset code. The same email works on the web.",
                    )
                    AuthStatusSlot(statusMsg)
                    Spacer(modifier = Modifier.height(18.dp))

                    StyledAuthField(
                        value = identifier,
                        onValueChange = { identifier = it; errorMsg = null },
                        label = "Email or phone",
                        placeholder = "you@example.com",
                        leadingIcon = Icons.Filled.Email,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            if (identifier.isNotBlank() && !loading) {
                                loading = true
                                errorMsg = null
                                statusMsg = "Sending reset email…"
                                scope.launch {
                                    val ok = V2Client.forgotPassword(identifier.trim())
                                    loading = false
                                    statusMsg = null
                                    if (ok) stage = 2
                                    else errorMsg = "No connection. Check your internet and try again."
                                }
                            }
                        },
                        index = 0,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    PrimaryCtaButton(
                        label = "Send reset email",
                        loading = loading,
                        enabled = identifier.isNotBlank(),
                        onClick = {
                            loading = true
                            errorMsg = null
                            statusMsg = "Sending reset email…"
                            scope.launch {
                                val ok = V2Client.forgotPassword(identifier.trim())
                                loading = false
                                statusMsg = null
                                if (ok) stage = 2
                                else errorMsg = "No connection. Check your internet and try again."
                            }
                        },
                        index = 1,
                    )

                    AuthErrorSlot(errorMsg)
                    BrandedFooterLink(
                        text = "Remembered it?",
                        linkText = "Back to sign in",
                        onClick = onDone,
                    )
                }

                // ── 2. Redeem the token natively ────────────────────────
                2 -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.MarkEmailRead,
                            contentDescription = null,
                            tint = ScottsTechXColors.BluePrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    BrandedAuthHeader(
                        title = "Check your email",
                        sub = "We sent a reset code to $identifier. Paste it below with your new password (or tap the link in the email to finish on the web).",
                    )
                    AuthStatusSlot(statusMsg)
                    Spacer(modifier = Modifier.height(16.dp))

                    StyledAuthField(
                        value = token,
                        onValueChange = { token = it; errorMsg = null },
                        label = "Reset code",
                        placeholder = "Paste the code from the email",
                        leadingIcon = Icons.Filled.Key,
                        index = 0,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it; errorMsg = null },
                        label = "New password",
                        leadingIcon = Icons.Filled.Lock,
                        index = 1,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordField(
                        value = confirm,
                        onValueChange = { confirm = it; errorMsg = null },
                        label = "Confirm new password",
                        leadingIcon = Icons.Filled.Lock,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            if (!loading && token.isNotBlank() && password.length >= 6 && password == confirm) {
                                loading = true
                                errorMsg = null
                                statusMsg = "Resetting your password…"
                                scope.launch {
                                    val (ok, msg) = V2Client.resetPasswordWithToken(token.trim(), password)
                                    loading = false
                                    statusMsg = null
                                    if (ok) stage = 3 else errorMsg = msg
                                }
                            }
                        },
                        index = 2,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    PrimaryCtaButton(
                        label = "Set new password",
                        loading = loading,
                        enabled = token.isNotBlank() && password.length >= 6 && password == confirm,
                        onClick = {
                            loading = true
                            errorMsg = null
                            statusMsg = "Resetting your password…"
                            scope.launch {
                                val (ok, msg) = V2Client.resetPasswordWithToken(token.trim(), password)
                                loading = false
                                statusMsg = null
                                if (ok) stage = 3 else errorMsg = msg
                            }
                        },
                        index = 3,
                    )

                    if (password.isNotEmpty() && confirm.isNotEmpty() && password != confirm) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "The two passwords do not match.",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                        )
                    }
                    AuthErrorSlot(errorMsg)

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Resend the email",
                        color = ScottsTechXColors.AccentLink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(enabled = !loading) {
                                scope.launch {
                                    statusMsg = "Resending…"
                                    V2Client.forgotPassword(identifier.trim())
                                    statusMsg = null
                                }
                            }
                            .padding(vertical = 6.dp),
                    )
                    BrandedFooterLink(
                        text = "",
                        linkText = "Back to sign in",
                        onClick = onDone,
                    )
                }

                // ── 3. Done ─────────────────────────────────────────────
                else -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.CyanAccent),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    BrandedAuthHeader(
                        title = "Password updated",
                        sub = "Your password has been changed. Sign in with the new one — this works on app and web alike.",
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    PrimaryCtaButton(
                        label = "Back to sign in",
                        loading = false,
                        onClick = onDone,
                        index = 0,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
