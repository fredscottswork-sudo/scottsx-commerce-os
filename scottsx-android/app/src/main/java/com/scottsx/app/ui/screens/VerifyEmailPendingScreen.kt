package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
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
 * Email verification — LINK-based, exactly like the website.
 *
 * The backend mails a verification LINK (there is deliberately no
 * typed code in the email — the web app has no code entry either).
 * The user taps the link in their mail app, which opens the web page
 * and flips the shared `email_verified` flag on the backend. This
 * screen then DETECTS it:
 *
 *   * It polls GET /auth/me every 4 s while open (the same poll the
 *     website's verify page runs), so the moment the link is tapped —
 *     on this phone or any device — the app notices and moves on.
 *   * A manual "I've tapped the link" button checks immediately for
 *     users who don't want to wait for the next tick.
 *   * "Resend email" re-mails the link (60 s cooldown, 6/hour cap).
 *
 * The fresh JWT is already stored (register signs the user in
 * immediately), so /auth/me is callable right away.
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

    var checking by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var resendCooldown by remember { mutableIntStateOf(0) }

    fun markVerified() {
        confirmed = true
        // Flip the local flag too so every screen sees it now.
        val cached = com.scottsx.app.SessionCache.user.value
        if (cached != null && cached.id.isNotBlank()) {
            com.scottsx.app.SessionCache.updateUser(cached.copy(emailVerified = true))
        }
    }

    /** One immediate check — used by the poll AND the manual button. */
    suspend fun checkNow(silent: Boolean) {
        if (!silent) {
            checking = true
            errorMsg = null
            statusMsg = "Checking…"
        }
        val verified = V2Client.fetchEmailVerified()
        if (!silent) {
            checking = false
            statusMsg = null
        }
        when (verified) {
            true -> if (!confirmed) markVerified()
            false -> if (!silent) errorMsg =
                "Not verified yet — open the email we sent to $email and tap the link inside it."
            null -> if (!silent) errorMsg =
                "Could not reach the server — check your internet and try again."
        }
    }

    // AUTO-POLL while the screen is open: the link is tapped in the
    // mail app, not here, so nothing else would tell us it happened.
    // Same 4-second cadence as the website's verify page. A failed
    // poll (offline, server waking) is silent — the next tick retries.
    LaunchedEffect(Unit) {
        while (!confirmed) {
            delay(4000)
            if (!confirmed && !checking) {
                if (V2Client.fetchEmailVerified() == true) markVerified()
            }
        }
    }

    // Confirmed → brief success beat, then continue into the app.
    LaunchedEffect(confirmed) {
        if (confirmed) {
            delay(900)
            onVerified()
        }
    }

    // Tick the resend cooldown down every second when > 0.
    LaunchedEffect(resendCooldown) {
        while (resendCooldown > 0) {
            delay(1000)
            resendCooldown -= 1
        }
    }

    // Wake the API while the user reads this screen so the poll (and
    // any resend) answers instantly instead of racing a cold server.
    LaunchedEffect(Unit) { V2Client.wakeServer() }

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
                    sub = "We sent a verification link to $email. Open it and tap the link — this screen will detect it automatically. It works on this phone or any device, and verifies your account everywhere at once.",
                )
                AuthStatusSlot(statusMsg)
                Spacer(modifier = Modifier.height(20.dp))

                // Live "watching" indicator — the poll is running.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = ScottsTechXColors.BluePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Waiting for the link to be tapped…",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                PrimaryCtaButton(
                    label = if (checking) "Checking…" else "I've tapped the link — check now",
                    loading = checking,
                    enabled = !checking,
                    onClick = { scope.launch { checkNow(silent = false) } },
                    index = 1,
                )

                AuthErrorSlot(errorMsg)

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Email didn't arrive? ",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = if (resendCooldown == 0) "Resend email" else "Resend in ${resendCooldown}s",
                        color = if (resendCooldown == 0) ScottsTechXColors.AccentLink else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(enabled = resendCooldown == 0) {
                                resendCooldown = 60
                                errorMsg = null
                                scope.launch {
                                    val ok = V2Client.requestVerificationCode()
                                    if (!ok) errorMsg = "Could not resend right now — wait a moment and try again."
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
                    text = "Tip: check the spam or promotions folder if you don't see it within a minute.",
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
