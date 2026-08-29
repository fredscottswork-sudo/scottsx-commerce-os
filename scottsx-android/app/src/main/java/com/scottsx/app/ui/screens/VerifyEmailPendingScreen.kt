package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.components.BrandLogo
import com.scottsx.app.ui.components.CinematicBackground
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Email verification pending screen.
 *
 * Shown after sign-up OR after a sign-in attempt by an unverified user.
 * The user must:
 *   1. Open the email we sent them (Gmail / Yahoo / Outlook / Apple Mail).
 *   2. Tap the "Verify email address" link inside it.
 *   3. Return to this screen and tap "I've verified — continue".
 *
 * This screen owns the reload() call that re-checks Firebase's
 * isEmailVerified flag, plus the 60-second cooldown on the Resend button.
 *
 * Reference: scottsx-app-android skill, references/stage5z4-enterprise-features-pattern.md
 * section "Firebase real email-link verification (not 6-digit codes)".
 */
@Composable
fun VerifyEmailPendingScreen(
    email: String,
    onBack: () -> Unit,
    onVerified: () -> Unit,
    onUseDifferentEmail: () -> Unit,
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Waiting for you to tap the link in your email...") }
    var checking by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableIntStateOf(0) }

    // Tick the cooldown down every second when > 0.
    LaunchedEffect(resendCooldown) {
        while (resendCooldown > 0) {
            delay(1000)
            resendCooldown -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark),
    ) {
        CinematicBackground()

        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ScottsTechXColors.OnDark,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() }
                    .padding(8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                )
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .padding(start = 24.dp, end = 24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogo(
                monogramSize = 72.dp,
                showWordmark = true,
                showTagline = false,
                autoPlay = false,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = null,
                    tint = ScottsTechXColors.BluePrimary,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Check your email",
                color = ScottsTechXColors.OnLight,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "We sent a verification link to",
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = email,
                color = ScottsTechXColors.BluePrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = status,
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            PrimaryButton(
                text = when {
                    checking -> "Checking..."
                    else -> "I've verified — continue"
                },
                loading = checking,
                onClick = {
                    checking = true
                    status = "Checking your email..."
                    scope.launch {
                        val user = auth.currentUser
                        if (user == null) {
                            checking = false
                            status = "Sign-in expired. Please go back and sign in again."
                            return@launch
                        }
                        val ok = runCatching {
                            user.reload()
                            user.isEmailVerified
                        }.getOrDefault(false)
                        checking = false
                        if (ok) {
                            status = "Email verified. Loading your dashboard..."
                            onVerified()
                        } else {
                            status = "Not yet verified. Open the email, tap the link, then try again."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = {
                    if (resendCooldown > 0) return@OutlinedButton
                    resendCooldown = 60
                    status = "Resending verification email..."
                    scope.launch {
                        val user = auth.currentUser
                        val ok = runCatching { user?.sendEmailVerification()?.await() }
                            .map { true }
                            .getOrDefault(false)
                        status = if (ok) {
                            "Verification email re-sent. Check your inbox (and spam folder)."
                        } else {
                            "Could not resend right now. Try again in a moment."
                        }
                    }
                },
                enabled = resendCooldown == 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (resendCooldown == 0) "Resend verification email"
                        else "Resend in ${resendCooldown}s",
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Use a different email",
                color = ScottsTechXColors.AccentLink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onUseDifferentEmail() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tip: check your spam or promotions folder if you don't see the email within a minute.",
                color = ScottsTechXColors.OnLightSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
