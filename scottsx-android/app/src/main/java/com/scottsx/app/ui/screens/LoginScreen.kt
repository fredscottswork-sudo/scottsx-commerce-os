package com.scottsx.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.AuthRepository
import com.scottsx.app.data.AuthResult
import com.scottsx.app.data.GoogleSignInHelper
import com.scottsx.app.data.Session
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.AuthDivider
import com.scottsx.app.ui.components.AuthStatusSlot
import com.scottsx.app.ui.components.BrandedAuthScaffold
import com.scottsx.app.ui.components.BrandedAuthHeader
import com.scottsx.app.ui.components.BrandedFooterLink
import com.scottsx.app.ui.components.GoogleAuthButton
import com.scottsx.app.ui.components.PasswordField
import com.scottsx.app.ui.components.PrimaryCtaButton
import com.scottsx.app.ui.components.StyledAuthField
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Login — manual email/phone+password for EVERYONE (the same accounts
 * that sign in on the web) plus the one-tap Google path. No Apple.
 *
 * The manual form calls the BACKEND directly (POST /api/v1/auth/login),
 * not Firebase — accounts created on the website have a backend password
 * hash and no Firebase credential, so only the backend can authenticate
 * them. The returned JWT is adopted into BOTH session stores, exactly
 * like the Google exchange, so every screen downstream is authenticated.
 *
 * Role mismatch: the account is signed out and the GOOGLE ACCOUNT
 * PICKER opens immediately, so a seller who tapped "Buyer" can switch
 * to their other Gmail without hunting through menus.
 */
@Composable
fun LoginScreen(
    role: Role,
    onBack: () -> Unit,
    onLogin: (Role) -> Unit,
    onGoogle: (Role) -> Unit,
    @Suppress("UNUSED_PARAMETER") onApple: () -> Unit,   // removed — Apple sign-in is gone for good
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onSwitchAccount: () -> Unit = {},
    onRoleMismatch: (Role) -> Unit = {},
    onVerificationPending: (email: String) -> Unit = {},
    authRepository: AuthRepository = AuthRepository(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    var silentTried by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activityContext = LocalContext.current as? Activity
    val googleHelper = remember(activityContext) { activityContext?.let { GoogleSignInHelper(it) } }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            googleHelper?.handleResult(result)
        } catch (t: Throwable) {
            android.util.Log.e("LoginScreen", "Google Sign-In launcher crashed", t)
            errorMsg = "Google sign-in failed to finish. Try again."
            googleLoading = false
        }
    }

    // Wake the production API the moment this screen appears. The
    // free-tier server sleeps when idle and its wake-up burns 20-60 s;
    // nudging it now means it is already awake by the time the user
    // taps Sign in. Fire-and-forget — the result is irrelevant.
    LaunchedEffect(Unit) { V2Client.wakeServer() }

    // Honest status when sign-in runs long: a waking server (free
    // tier) is the usual cause, and a silent spinner for a minute
    // reads as "broken". Swap the status line after 5 s of loading.
    LaunchedEffect(loading) {
        if (loading) {
            kotlinx.coroutines.delay(5000)
            if (loading && statusMsg != null) {
                statusMsg = "The server is waking up — this can take a moment. Hold on…"
            }
        }
    }

    /** Complete the Google sign-in once we hold an id_token. */
    suspend fun finishGoogleSignIn(idToken: String?, silentResume: Boolean) {
        if (idToken == null) {
            if (!silentResume && googleLoading) errorMsg = "Google sign-in was cancelled."
            googleLoading = false
            statusMsg = null
            return
        }
        val result: AuthResult = try {
            authRepository.signInWithGoogle(idToken, role)
        } catch (t: Throwable) {
            AuthResult.Failure(t.message ?: "Google sign-in failed")
        }
        val stillActive = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive ?: true
        if (!stillActive) return
        googleLoading = false
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
            is AuthResult.VerificationPending -> {
                // Google sign-ins are pre-verified; route straight in.
                onGoogle(role)
            }
            is AuthResult.RoleMismatch -> {
                // Seller tapped Buyer (or vice versa): AUTO-RESET — clear
                // the session and pop the Gmail account picker so the
                // person can choose the right account on the spot.
                errorMsg = null
                statusMsg = "That Gmail is registered as a ${result.actual.displayName} — pick the right account…"
                scope.launch {
                    runCatching { authRepository.signOut() }
                    runCatching { googleHelper?.signOut() }
                    googleHelper?.forcePickerOnNextSignIn()
                    onSwitchAccount()
                    val helper = googleHelper ?: return@launch
                    googleLoading = true
                    val idToken2 = runCatching {
                        kotlinx.coroutines.withTimeoutOrNull(180_000) {
                            helper.signInWithInteractive(googleLauncher)
                        }
                    }.getOrNull()
                    finishGoogleSignIn(idToken2, silentResume = false)
                }
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
        googleLoading = true
        errorMsg = null
        statusMsg = "Opening Google…"
        scope.launch {
            val silentToken = helper.trySilentSignIn()
            val idToken = silentToken
                ?: runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(180_000) {
                        helper.signInWithInteractive(googleLauncher)
                    }
                }.getOrNull()
            finishGoogleSignIn(idToken, silentResume = false)
        }
    }

    /** Manual login — backend /auth/login (works for web-created accounts). */
    fun signInManually() {
        if (loading) return
        val identifier = email.trim()
        if (identifier.isBlank() || password.isBlank()) {
            errorMsg = "Enter your email (or phone) and password."
            return
        }
        loading = true
        errorMsg = null
        statusMsg = "Signing you in…"
        scope.launch {
            val res = V2Client.loginEmail(identifier, password)
            loading = false
            statusMsg = null
            when (res.status) {
                V2Client.EmailLoginStatus.SUCCESS -> {
                    val u = res.user!!
                    val actualRole = if (u.role.equals("seller", true)) Role.SELLER else Role.BUYER
                    if (actualRole != role) {
                        // Manual creds can't open a Gmail picker — hand off
                        // to the dedicated mismatch flow.
                        onRoleMismatch(actualRole)
                        return@launch
                    }
                    com.scottsx.app.SessionCache.save(
                        res.token!!,
                        com.scottsx.app.CurrentUser(
                            id = u.id,
                            email = u.email,
                            displayName = u.displayName.ifBlank { "ScottsTechX user" },
                            phone = u.phone,
                            role = u.role,
                            emailVerified = u.emailVerified,
                            profilePhotoUrl = u.profilePhotoUrl,
                            city = u.city,
                        ),
                        announce = true,
                    )
                    Session.adoptSession(
                        token = res.token,
                        userId = u.id,
                        role = actualRole,
                        displayName = u.displayName,
                        email = u.email,
                        avatarUrl = u.profilePhotoUrl,
                        storeLocation = u.city,
                    )
                    if (!u.emailVerified) {
                        onVerificationPending(u.email.takeIf { it.isNotBlank() } ?: identifier)
                        return@launch
                    }
                    onLogin(actualRole)
                }
                V2Client.EmailLoginStatus.INVALID_CREDENTIALS ->
                    errorMsg = "Wrong email or password. Try again — or tap \"Forgot password?\" below."
                V2Client.EmailLoginStatus.DISABLED ->
                    errorMsg = "This account is disabled. Contact ScottsTechX support."
                V2Client.EmailLoginStatus.NETWORK ->
                    // Usually the production API waking from its idle sleep —
                    // the retry already ran once; a second tap now connects.
                    errorMsg = "The server is waking up (free tier sleeps when idle) — wait 3–5 seconds and tap Sign in again. If it persists, check your internet."
            }
        }
    }

    // FASTER THAN A TAP: silently resume a cached Google account.
    LaunchedEffect(Unit) {
        if (silentTried) return@LaunchedEffect
        silentTried = true
        val helper = googleHelper ?: return@LaunchedEffect
        if (!helper.hasCachedAccount()) return@LaunchedEffect
        statusMsg = "Welcome back — signing you in…"
        googleLoading = true
        val silentToken = helper.trySilentSignIn()
        if (silentToken != null) {
            finishGoogleSignIn(silentToken, silentResume = true)
        } else {
            googleLoading = false
            statusMsg = null
        }
    }

    BrandedAuthScaffold(role = role, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandedAuthHeader(
                title = "Welcome back",
                sub = "Sign in to your " +
                    (if (role == Role.SELLER) "seller dashboard" else "buyer feed") +
                    " — the same account as the web.",
            )

            AuthStatusSlot(statusMsg)

            Spacer(modifier = Modifier.height(18.dp))

            // ── Manual credentials (backend accounts — the web's own) ──
            StyledAuthField(
                value = email,
                onValueChange = { email = it; errorMsg = null },
                label = "Email or phone",
                placeholder = "you@example.com",
                leadingIcon = Icons.Filled.Email,
                index = 0,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordField(
                value = password,
                onValueChange = { password = it; errorMsg = null },
                label = "Password",
                leadingIcon = Icons.Filled.Lock,
                imeAction = ImeAction.Done,
                onImeAction = { signInManually() },
                index = 1,
            )

            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Forgot password?",
                    color = ScottsTechXColors.AccentLink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onForgotPassword() }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            PrimaryCtaButton(
                label = "Sign in",
                loading = loading,
                onClick = { signInManually() },
                index = 2,
            )

            AuthDivider("or continue with", index = 3)

            GoogleAuthButton(
                label = if (googleLoading) "Signing you in…" else "Continue with Google",
                loading = googleLoading,
                onClick = { if (!googleLoading) startInteractiveSignIn() },
                index = 4,
            )

            // "Use a different Google account" — only when a cached one exists.
            val helper = googleHelper
            if (helper != null && helper.hasCachedAccount() && !googleLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Use a different Google account",
                    color = ScottsTechXColors.AccentLink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clickable {
                            helper.forcePickerOnNextSignIn()
                            errorMsg = null
                            startInteractiveSignIn()
                        }
                        .padding(vertical = 4.dp),
                )
            }

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMsg!!,
                    color = Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BrandedFooterLink(
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
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
