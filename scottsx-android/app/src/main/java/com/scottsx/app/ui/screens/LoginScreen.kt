package com.scottsx.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.AuthRepository
import com.scottsx.app.data.AuthResult
import com.scottsx.app.data.GoogleSignInHelper
import com.scottsx.app.data.domain.Role
import com.scottsx.app.ui.components.GoogleG
import com.scottsx.app.ui.components.GoogleOnlyAuthHeader
import com.scottsx.app.ui.components.GoogleOnlyAuthLayout
import com.scottsx.app.ui.components.GoogleOnlyErrorSlot
import com.scottsx.app.ui.components.GoogleOnlyFooterLink
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Screen 4 — Login (Google-only).
 *
 * Per product direction the ONLY way into the app is a Google account:
 *  - ONE big white Google button with the authentic four-colour "G"
 *    (drawn by [GoogleG] — no asset, identical on every device).
 *  - NO email/password fields, NO Apple button, no social disabled rows.
 *  - SPEED: the moment the screen appears we attempt a silent Google
 *    sign-in with the device's cached account — returning users touch
 *    NOTHING and land on their dashboard in under a second. The manual
 *    button is the fallback and doubles as the account-picker entry.
 *
 * The card rides the shared [GoogleOnlyAuthLayout] so Login and Sign-Up
 * look and feel like the same place (which they are: the Google account
 * IS the account).
 */
@Composable
fun LoginScreen(
    role: Role,
    onBack: () -> Unit,
    onLogin: (Role) -> Unit,
    onGoogle: (Role) -> Unit,
    @Suppress("UNUSED_PARAMETER") onApple: () -> Unit,   // removed — Apple sign-in is gone for good
    onSignUp: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onForgotPassword: () -> Unit, // removed — Google owns credentials
    onRoleMismatch: (Role) -> Unit = {},
    onVerificationPending: (email: String) -> Unit = {},
    authRepository: AuthRepository = AuthRepository(),
) {
    var loading by remember { mutableStateOf(false) }
    var silentTried by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activityContext = LocalContext.current as? Activity
    val googleHelper = remember(activityContext) { activityContext?.let { GoogleSignInHelper(it) } }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        // Defer to handleResult (exception-safe) so the callback never crashes the process.
        try {
            googleHelper?.handleResult(result)
        } catch (t: Throwable) {
            android.util.Log.e("LoginScreen", "Google Sign-In launcher crashed", t)
            errorMsg = "Google sign-in failed to finish. Try again."
            loading = false
        }
    }

    /** Complete the sign-in once we hold a Google id_token. */
    suspend fun finishGoogleSignIn(idToken: String?, silentResume: Boolean) {
        if (idToken == null) {
            if (!silentResume) errorMsg = "Google sign-in was cancelled."
            return
        }
        val result: AuthResult = try {
            authRepository.signInWithGoogle(idToken, role)
        } catch (t: Throwable) {
            AuthResult.Failure(t.message ?: "Google sign-in failed")
        }
        val stillActive = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive ?: true
        if (!stillActive) return
        loading = false
        statusMsg = null
        when (result) {
            is AuthResult.Success -> {
                try {
                    onGoogle(result.role)
                } catch (t: Throwable) {
                    android.util.Log.e("LoginScreen", "post-google nav failed", t)
                    errorMsg = "Signed in, but the dashboard failed to load: ${t.message ?: t.javaClass.simpleName}"
                }
            }
            is AuthResult.Failure -> errorMsg = result.message
            is AuthResult.VerificationPending -> onVerificationPending(result.email)
            is AuthResult.RoleMismatch -> {
                errorMsg = "This Google account is registered as a ${result.actual.displayName}."
                onRoleMismatch(result.actual)
            }
        }
    }

    /** The manual "Continue with Google" tap: silent first, picker if needed. */
    fun startInteractiveSignIn() {
        val helper = googleHelper
        if (helper == null) {
            errorMsg = "Google sign-in is unavailable on this device."
            return
        }
        loading = true
        errorMsg = null
        statusMsg = "Opening Google…"
        scope.launch {
            val silentToken = helper.trySilentSignIn()
            val idToken = silentToken
                ?: kotlinx.coroutines.withTimeoutOrNull(180_000) {
                    helper.signInWithInteractive(googleLauncher)
                }
            finishGoogleSignIn(idToken, silentResume = false)
        }
    }

    // FASTER THAN A TAP: on first composition, if the device already knows
    // a Google account, resume the session silently and skip the sheet.
    LaunchedEffect(Unit) {
        if (silentTried) return@LaunchedEffect
        silentTried = true
        val helper = googleHelper ?: return@LaunchedEffect
        if (!helper.hasCachedAccount()) return@LaunchedEffect
        statusMsg = "Welcome back — signing you in…"
        loading = true
        val silentToken = helper.trySilentSignIn()
        if (silentToken != null) {
            finishGoogleSignIn(silentToken, silentResume = true)
        } else {
            loading = false
            statusMsg = null
        }
    }

    GoogleOnlyAuthLayout(role = role, onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoogleOnlyAuthHeader(
                title = "Welcome back",
                sub = "One tap on Google and you're straight into your " +
                    (if (role == Role.SELLER) "seller dashboard" else "buyer feed") +
                    " — no passwords, no forms.",
            )

            if (statusMsg != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = ScottsTechXColors.BluePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusMsg!!,
                        color = Color(0xFF475569),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // THE button. One Google button, the original one.
            GoogleAuthButtonSimple(
                label = if (loading) "Signing you in…" else "Continue with Google",
                loading = loading,
                onClick = { if (!loading) startInteractiveSignIn() },
            )

            GoogleOnlyErrorSlot(errorMsg)

            // "Use a different Google account" — only when the SDK has a cached account.
            val helper = googleHelper
            if (helper != null && helper.hasCachedAccount() && !loading) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Use a different Google account",
                    color = ScottsTechXColors.AccentLink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable {
                            helper.forcePickerOnNextSignIn()
                            errorMsg = null
                            startInteractiveSignIn()
                        }
                        .padding(vertical = 6.dp),
                )
            }

            GoogleOnlyFooterLink(
                text = "New to ScottsTechX?",
                linkText = "Create an account",
                onClick = onSignUp,
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Same account as the web — messages, orders and cart stay in sync.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * THE Google button — white with the authentic four-colour G, H-shaped
 * hit area, scale feedback. Everything about this matches the web
 * GoogleButton exactly.
 */
@Composable
private fun GoogleAuthButtonSimple(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(27.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE1E6EF), shape)
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = ScottsTechXColors.BluePrimary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                GoogleG(modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF101828),
            )
        }
    }
}
