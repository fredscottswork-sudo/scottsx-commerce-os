package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.Session
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.AuthErrorSlot
import com.scottsx.app.ui.components.AuthStatusSlot
import com.scottsx.app.ui.components.BrandedAuthHeader
import com.scottsx.app.ui.components.BrandedAuthScaffold
import com.scottsx.app.ui.components.BrandedFooterLink
import com.scottsx.app.ui.components.PrimaryCtaButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Email verification — the SIX-DIGIT CODE the backend mails at sign-up
 * (the exact same code the website asks for), typed into six company-
 * styled boxes. Confirms via POST /auth/verify/confirm. The email also
 * contains a link that finishes on the web if the user prefers — both
 * paths flip the same `email_verified` flag on the shared account.
 *
 * The fresh JWT is already stored (register signs the user in
 * immediately), so the verify endpoints are callable right away.
 */
@Composable
fun VerifyEmailPendingScreen(
    email: String,
    onBack: () -> Unit,
    onVerified: () -> Unit,
    onUseDifferentEmail: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val role = Session.roleOrNull() ?: Role.BUYER

    var code by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var resendCooldown by remember { mutableIntStateOf(0) }

    // Auto-resend once on mount (harmless — supersedes the first code
    // with a fresh 15-minute window) when the route carried an email
    // from a LOGIN (not a fresh register, which has one already).
    LaunchedEffect(Unit) {
        // Register already mailed a code; don't spam. Only the status
        // line is set so users know where to look.
        statusMsg = null
    }

    // Tick the cooldown down every second when > 0.
    LaunchedEffect(resendCooldown) {
        while (resendCooldown > 0) {
            delay(1000)
            resendCooldown -= 1
        }
    }

    fun confirm() {
        if (checking || confirmed) return
        if (code.length != 6) {
            errorMsg = "Enter all 6 digits from the email."
            return
        }
        checking = true
        errorMsg = null
        statusMsg = "Verifying…"
        scope.launch {
            val ok = V2Client.confirmVerificationCode(code)
            checking = false
            statusMsg = null
            if (ok) {
                confirmed = true
                // Flip the local flag too so every screen sees it now.
                val cached = com.scottsx.app.SessionCache.user.value
                if (cached != null && cached.id.isNotBlank()) {
                    com.scottsx.app.SessionCache.updateUser(cached.copy(emailVerified = true))
                }
                delay(900)
                onVerified()
            } else {
                errorMsg = "That code didn't match — check the email and try again (codes expire after 15 minutes)."
            }
        }
    }

    BrandedAuthScaffold(role = role, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!confirmed) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MarkEmailRead,
                        contentDescription = null,
                        tint = ScottsTechXColors.BluePrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                BrandedAuthHeader(
                    title = "Verify your email",
                    sub = "We sent a 6-digit code to $email. Type it below — the code is shared with the website, so verify once and you're in everywhere.",
                )
                AuthStatusSlot(statusMsg)
                Spacer(modifier = Modifier.height(20.dp))

                // ── Six company-styled code boxes ───────────────────────
                BasicTextField(
                    value = code,
                    onValueChange = { v ->
                        val digits = v.filter { it.isDigit() }.take(6)
                        if (digits != code) {
                            code = digits
                            errorMsg = null
                            if (digits.length == 6) confirm()
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = TextStyle(color = Color.Transparent),
                    modifier = Modifier.fillMaxWidth(),
                ) { inner ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        repeat(6) { i ->
                            val ch = code.getOrNull(i)
                            val active = code.length == i
                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (active) Color(0xFF14254A) else Color(0xFF111F3B),
                                    )
                                    .border(
                                        width = if (active) 2.dp else 1.dp,
                                        color = if (active) ScottsTechXColors.BluePrimaryLight
                                        else Color(0xFF22335C),
                                        shape = RoundedCornerShape(14.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ch?.toString() ?: "",
                                    color = Color(0xFFF3F7FF),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                    inner()
                }

                AuthErrorSlot(errorMsg)

                Spacer(modifier = Modifier.height(18.dp))
                PrimaryCtaButton(
                    label = if (checking) "Verifying…" else "Verify email",
                    loading = checking,
                    enabled = code.length == 6,
                    onClick = { confirm() },
                    index = 1,
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Didn't get it? ",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = if (resendCooldown == 0) "Resend code" else "Resend in ${resendCooldown}s",
                        color = if (resendCooldown == 0) ScottsTechXColors.AccentLink else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(enabled = resendCooldown == 0) {
                                resendCooldown = 60
                                errorMsg = null
                                scope.launch {
                                    val ok = V2Client.requestVerificationCode()
                                    if (!ok) errorMsg = "Could not resend right now. Try again in a moment."
                                }
                            }
                            .padding(vertical = 2.dp),
                    )
                }

                BrandedFooterLink(
                    text = "Wrong address?",
                    linkText = "Use a different email",
                    onClick = onUseDifferentEmail,
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tip: check the spam or promotions folder — or tap the link inside the email to finish on the web instead.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.CyanAccent),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                BrandedAuthHeader(
                    title = "Email verified",
                    sub = "Your account is fully active — welcome to ScottsTechX. Loading your dashboard…",
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
