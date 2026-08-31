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
import androidx.compose.runtime.LaunchedEffect
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
 * Password reset — LINK-based, exactly like the website.
 *
 *  1. REQUEST: the user types their email (or phone). We call
 *     POST /auth/forgot-password which mails a single-use reset LINK
 *     (there is deliberately no typed code — the web app redeems the
 *     link too). Tapping the link opens the secure reset page where
 *     they choose a new password; that's the primary path.
 *  2. NATIVE REDEEM (optional): the user can instead paste the whole
 *     emailed link here with a new password → the app extracts the
 *     token and redeems it via POST /auth/reset-password. No browser
 *     round-trip needed.
 *
 * Either way the account's password changes on the shared backend —
 * the new password then works on Login, app and web alike.
 */
@Composable
fun ResetPasswordScreen(
    role: Role,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var stage by remember { mutableStateOf(1) }              // 1=request, 2=redeem, 3=done
    var identifier by remember { mutableStateOf("") }
    var linkPaste by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Wake the API while the user reads the form, so the request
    // answers instantly instead of racing a cold free-tier server.
    LaunchedEffect(Unit) { V2Client.wakeServer() }

    /**
     * Pull the reset token out of whatever the user pasted: the full
     * emailed link, or the bare token. Returns null (and sets the
     * error) when neither shape is present.
     */
    fun extractToken(paste: String): String? {
        val trimmed = paste.trim()
        if (trimmed.isEmpty()) return null
        Regex("[?&]token=([A-Za-z0-9_-]{20,200})").find(trimmed)?.let {
            return it.groupValues[1]
        }
        if (Regex("^[A-Za-z0-9_-]{20,200}$").matches(trimmed)) return trimmed
        return null
    }

    fun requestReset() {
        if (loading || identifier.isBlank()) return
        loading = true
        errorMsg = null
        statusMsg = "Sending reset link…"
        scope.launch {
            val ok = V2Client.forgotPassword(identifier.trim())
            loading = false
            statusMsg = null
            if (ok) stage = 2
            else errorMsg = "No connection. Check your internet and try again."
        }
    }

    fun redeemToken() {
        val token = extractToken(linkPaste)
        if (loading || token == null || password.length < 6 || password != confirm) return
        loading = true
        errorMsg = null
        statusMsg = "Setting your new password…"
        scope.launch {
            val (ok, msg) = V2Client.resetPasswordWithToken(token, password)
            loading = false
            statusMsg = null
            if (ok) stage = 3 else errorMsg = msg
        }
    }

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
                        sub = "Enter the email or phone on your account — we'll send you a reset link. The same link works on the web.",
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
                        onImeAction = { requestReset() },
                        index = 0,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    PrimaryCtaButton(
                        label = "Send reset link",
                        loading = loading,
                        enabled = identifier.isNotBlank(),
                        onClick = { requestReset() },
                        index = 1,
                    )

                    AuthErrorSlot(errorMsg)
                    BrandedFooterLink(
                        text = "Remembered it?",
                        linkText = "Back to sign in",
                        onClick = onDone,
                    )
                }

                // ── 2. Check your email / optional native redeem ────────
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
                        sub = "We sent a reset link to $identifier. Open the email on this phone and TAP the link — it opens a secure page where you choose your new password.",
                    )
                    AuthStatusSlot(statusMsg)
                    Spacer(modifier = Modifier.height(16.dp))

                    PrimaryCtaButton(
                        label = "Done — back to sign in",
                        loading = false,
                        onClick = onDone,
                        index = 0,
                    )

                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Prefer to set it here? Paste the reset link from the email below.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StyledAuthField(
                        value = linkPaste,
                        onValueChange = { linkPaste = it; errorMsg = null },
                        label = "Reset link (paste the whole link)",
                        placeholder = "https://…/reset-password?token=…",
                        leadingIcon = Icons.Filled.Key,
                        index = 1,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it; errorMsg = null },
                        label = "New password",
                        leadingIcon = Icons.Filled.Lock,
                        index = 2,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordField(
                        value = confirm,
                        onValueChange = { confirm = it; errorMsg = null },
                        label = "Confirm new password",
                        leadingIcon = Icons.Filled.Lock,
                        imeAction = ImeAction.Done,
                        onImeAction = { redeemToken() },
                        index = 3,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    PrimaryCtaButton(
                        label = "Set new password",
                        loading = loading,
                        enabled = extractToken(linkPaste) != null && password.length >= 6 && password == confirm,
                        onClick = { redeemToken() },
                        index = 4,
                    )

                    if (password.isNotEmpty() && confirm.isNotEmpty() && password != confirm) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "The two passwords do not match.",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                        )
                    }
                    if (linkPaste.isNotBlank() && extractToken(linkPaste) == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "That doesn't look like the reset link — paste the whole link from the email.",
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
