package com.scottsx.app.ui.screens

import com.scottsx.app.data.domain.Role

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.scottsx.app.data.AuthRepository
import com.scottsx.app.data.AuthResult
import com.scottsx.app.data.GoogleSignInHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.components.BrandLogo
import com.scottsx.app.ui.components.CinematicBackground
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.SocialButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 4 — Email Login.
 *
 * Slides up from the bottom, leaving a slim peek of the cinematic
 * background above so the ScottsTechX brand identity carries through.
 *
 * @param role [Role.BUYER] or [Role.SELLER] — controls the copy
 *            and the badge shown at the top of the panel.
 */
@Composable
fun LoginScreen(
    role: Role,
    onBack: () -> Unit,
    onLogin: (Role) -> Unit,
    onGoogle: (Role) -> Unit,
    onApple: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    onRoleMismatch: (Role) -> Unit = {},
    onVerificationPending: (email: String) -> Unit = {},
    authRepository: AuthRepository = AuthRepository(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activityContext = LocalContext.current as? Activity
    val googleHelper = remember(activityContext) { activityContext?.let { GoogleSignInHelper(it) } }
    // Launcher for the Google Sign-In account picker.
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        // CRITICAL: any exception thrown here crashes the process
        // (the launcher callback runs on the main thread inside
        // Activity.onActivityResult). Defer to handleResult, which
        // is exception-safe.
        try {
            googleHelper?.handleResult(result)
        } catch (t: Throwable) {
            android.util.Log.e("LoginScreen", "Google Sign-In launcher crashed", t)
        }
    }

    val slideOffset = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        slideOffset.animateTo(0f, tween(420, easing = EaseOutCubic))
    }

    Box(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.BackgroundDark)) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = ScottsTechXColors.PanelLight,
                        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                    )
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .verticalScroll(rememberScrollState())
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 10.dp, bottom = 28.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(40.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ScottsTechXColors.PanelBorderHint),
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BrandLogo(
                        monogramSize = 64.dp,
                        showWordmark = true,
                        showTagline = false,
                        autoPlay = false,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Logging in as ",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 13.sp,
                    )
                    RoleBadge(role)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Welcome back to ScottsTechX",
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (role == Role.SELLER)
                        "Pick up where you left off — your customers are waiting."
                    else
                        "Your next opportunity is one tap away.",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(20.dp))

                InputField(
                    value = email,
                    onValueChange = { email = it; errorMsg = null },
                    label = "Email or Phone Number",
                    placeholder = "Enter your email or phone number",
                    keyboardType = KeyboardType.Email,
                )

                Spacer(modifier = Modifier.height(12.dp))

                InputField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = "Password",
                    placeholder = "Enter your password",
                    isPassword = true,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "Forgot Password?",
                        color = ScottsTechXColors.AccentLink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onForgotPassword() }
                            .padding(8.dp),
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = friendlyError(errorMsg!!),
                        color = Color(0xFFB91C1C),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                PrimaryButton(
                    text = if (role == Role.SELLER) "Login to my Seller account" else "Login",
                    loading = loading,
                    onClick = {
                        if (email.isBlank()) {
                            errorMsg = "Please enter your email or phone."
                            return@PrimaryButton
                        }
                        if (password.isBlank()) {
                            errorMsg = "Please enter your password."
                            return@PrimaryButton
                        }
                        errorMsg = null
                        loading = true
                        scope.launch {
                            val result = runCatching {
                                authRepository.signIn(email, password, expectedRole = role)
                            }.getOrElse { AuthResult.Failure(it.message ?: "Sign-in failed") }
                            loading = false
                            when (result) {
                                is AuthResult.Success -> {
                                    try {
                                        onLogin(result.role)
                                    } catch (t: Throwable) {
                                        android.util.Log.e("LoginScreen", "post-login nav failed", t)
                                        errorMsg = "Signed in, but the dashboard failed to load: ${t.message ?: t.javaClass.simpleName}"
                                    }
                                }
                                is AuthResult.Failure -> errorMsg = result.message
                                is AuthResult.VerificationPending -> {
                                    onVerificationPending(result.email)
                                }
                                is AuthResult.RoleMismatch -> {
                                    errorMsg = "This email is registered as a ${result.actual.displayName}."
                                    onRoleMismatch(result.actual)
                                }
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(ScottsTechXColors.PanelBorderHint))
                    Text(
                        text = "or continue with",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(ScottsTechXColors.PanelBorderHint))
                }

                Spacer(modifier = Modifier.height(14.dp))

                SocialButton(
                    text = "Login with Google",
                    onClick = {
                        val helper = googleHelper
                        if (helper == null) {
                            errorMsg = "Google sign-in unavailable right now."
                            return@SocialButton
                        }
                        loading = true
                        scope.launch {
                            val result: AuthResult = try {
                                // 1. Try silent sign-in first (works if the
                                //    user has previously authenticated with
                                //    Google on this device). The "Use a
                                //    different account" button below calls
                                //    helper.forcePickerOnNextSignIn() to skip
                                //    step 1 and go straight to the picker.
                                val silentToken = helper.trySilentSignIn()
                                // 2. Otherwise launch the interactive picker.
                                val idToken = silentToken
                                    ?: kotlinx.coroutines.withTimeoutOrNull(180_000) {
                                        helper.signInWithInteractive(googleLauncher)
                                    }
                                if (idToken == null) {
                                    AuthResult.Failure("Google sign-in cancelled.")
                                } else {
                                    authRepository.signInWithGoogle(idToken, role)
                                }
                            } catch (ce: kotlinx.coroutines.CancellationException) {
                                throw ce  // propagate structured cancellation
                            } catch (t: Throwable) {
                                AuthResult.Failure(t.message ?: "Google sign-in failed")
                            }
                            // If the composition was destroyed during the
                            // suspend (e.g. user pressed back), `loading = false`
                            // and `onGoogle(...)` would crash with a snapshot
                            // error. Capture the callbacks now and only invoke
                            // them if the coroutine is still active.
                            val onSuccess = onGoogle
                            val onMismatch = onRoleMismatch
                            val stillActive = coroutineContext[kotlinx.coroutines.Job]?.isActive ?: true
                            if (!stillActive) return@launch
                            loading = false
                            when (result) {
                                is AuthResult.Success -> {
                                    try {
                                        onSuccess(result.role)
                                    } catch (t: Throwable) {
                                        android.util.Log.e("LoginScreen", "post-google nav failed", t)
                                        errorMsg = "Signed in, but the dashboard failed to load: ${t.message ?: t.javaClass.simpleName}"
                                    }
                                }
                                is AuthResult.Failure -> errorMsg = result.message
                                is AuthResult.VerificationPending -> {
                                    // Google sign-in: email is already verified by Google,
                                    // but if Firebase says no, bounce to the pending screen.
                                    onVerificationPending(result.email)
                                }
                                is AuthResult.RoleMismatch -> {
                                    errorMsg = "This Google account is registered as a ${result.actual.displayName}."
                                    onMismatch(result.actual)
                                }
                            }
                        }
                    },
                    leadingIcon = { GoogleMark() },
                )

                // "Use a different Google account" — visible whenever the
                // Google SDK has a cached last-signed-in account. Forces the
                // next tap to open the system account chooser instead of
                // silently signing back in with the same account. Critical
                // for testing multi-account flows.
                val helper = googleHelper
                if (helper != null && helper.hasCachedAccount()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use a different Google account",
                        color = ScottsTechXColors.AccentLink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                // Clear the SDK cache; next tap shows picker.
                                helper.forcePickerOnNextSignIn()
                                errorMsg = "Pick a different Google account on the next screen."
                            }
                            .padding(vertical = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SocialButton(
                    text = "Continue with Apple",
                    onClick = onApple,
                    leadingIcon = { AppleMark() },
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (role == Role.SELLER)
                            "New to selling? "
                        else
                            "New here? ",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = if (role == Role.SELLER)
                            "Create a Seller account"
                        else
                            "Create your ScottsTechX account",
                        color = ScottsTechXColors.AccentLink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onSignUp() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RoleBadge(role: Role) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = role.displayName,
            color = ScottsTechXColors.BluePrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

private fun friendlyError(raw: String): String = when {
    raw.contains("no user record", ignoreCase = true) -> "No account found for that email."
    raw.contains("password is invalid", ignoreCase = true) -> "Wrong password. Try again."
    raw.contains("blocked", ignoreCase = true) -> "Too many attempts. Try again later."
    raw.contains("network", ignoreCase = true) -> "Network error. Check your connection."
    raw.isBlank() -> "Sign-in failed. Please try again."
    else -> raw
}

@Composable
private fun GoogleMark() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension / 2f
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 0f, sweepAngle = 90f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 90f, sweepAngle = 90f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 180f, sweepAngle = 90f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        )
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 270f, sweepAngle = 90f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        )
    }
}

@Composable
private fun AppleMark() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
        val w = this.size.width
        val h = this.size.height
        drawOval(
            color = Color(0xFF0F172A),
            topLeft = androidx.compose.ui.geometry.Offset(0.05f * w, 0.18f * h),
            size = androidx.compose.ui.geometry.Size(0.9f * w, 0.82f * h),
        )
        drawCircle(
            color = Color.White,
            radius = 0.15f * w,
            center = androidx.compose.ui.geometry.Offset(0.78f * w, 0.15f * h),
        )
        drawOval(
            color = Color(0xFF0F172A),
            topLeft = androidx.compose.ui.geometry.Offset(0.65f * w, 0.02f * h),
            size = androidx.compose.ui.geometry.Size(0.25f * w, 0.20f * h),
        )
    }
}