package com.scottsx.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.scottsx.app.ui.components.AuthErrorSlot
import com.scottsx.app.ui.components.AuthStatusSlot
import com.scottsx.app.ui.components.BrandedAuthHeader
import com.scottsx.app.ui.components.BrandedAuthScaffold
import com.scottsx.app.ui.components.BrandedFooterLink
import com.scottsx.app.ui.components.GoogleAuthButton
import com.scottsx.app.ui.components.PasswordField
import com.scottsx.app.ui.components.PrimaryCtaButton
import com.scottsx.app.ui.components.StyledAuthField
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Sign-Up — a REAL create-account form (name, email, phone, password,
 * confirm, plus store name for sellers) backed by POST /auth/register,
 * i.e. the very same endpoint the website uses. The account works on
 * app AND web the second it exists. Google remains as the one-tap
 * alternative below the divider. No Apple.
 *
 * After registering, the user is routed to the verification screen with
 * their fresh JWT already stored — /auth/verify endpoints require it.
 */
@Composable
fun SignUpScreen(
    role: Role,
    onBack: () -> Unit,
    onSubmit: (Role) -> Unit,
    onSignInInstead: () -> Unit,
    onRoleMismatch: (Role) -> Unit = {},
    onVerificationPending: (email: String) -> Unit = {},
    authRepository: AuthRepository = AuthRepository(),
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
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
            android.util.Log.e("SignUpScreen", "Google Sign-In launcher crashed", t)
            errorMsg = "Google sign-up failed to finish. Try again."
            googleLoading = false
        }
    }

    /** Adopt a fresh backend session into both stores. */
    fun adoptSession(token: String, u: V2Client.GoogleUser, actualRole: Role) {
        com.scottsx.app.SessionCache.save(
            token,
            com.scottsx.app.CurrentUser(
                id = u.id,
                email = u.email,
                displayName = u.displayName.ifBlank { name.trim().ifBlank { "ScottsTechX user" } },
                phone = u.phone.ifBlank { phone.trim() },
                role = u.role,
                emailVerified = u.emailVerified,
                profilePhotoUrl = u.profilePhotoUrl,
                city = u.city,
            ),
            announce = true,
        )
        Session.adoptSession(
            token = token,
            userId = u.id,
            role = actualRole,
            displayName = u.displayName.ifBlank { name.trim() },
            email = u.email,
            avatarUrl = u.profilePhotoUrl,
            storeLocation = u.city,
        )
    }

    /** Manual create-account via the backend register route. */
    fun createAccount() {
        if (loading) return
        val emailTrim = email.trim()
        when {
            name.isBlank() -> { errorMsg = "Enter your full name."; return }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches() -> {
                errorMsg = "Enter a valid email address."; return
            }
            password.length < 6 -> { errorMsg = "Password must be at least 6 characters."; return }
            password != confirm -> { errorMsg = "The two passwords do not match."; return }
            role == Role.SELLER && storeName.isBlank() -> { errorMsg = "Enter your store name."; return }
        }
        loading = true
        errorMsg = null
        statusMsg = "Creating your account…"
        scope.launch {
            val res = V2Client.registerEmail(
                email = emailTrim,
                password = password,
                displayName = name.trim(),
                phone = phone.trim(),
                role = if (role == Role.SELLER) "seller" else "buyer",
                storeName = if (role == Role.SELLER) storeName.trim() else null,
            )
            loading = false
            statusMsg = null
            when {
                res.ok && res.token != null && res.user != null -> {
                    adoptSession(res.token, res.user, role)
                    // Accounts start unverified → collect the 6-digit code.
                    onVerificationPending(emailTrim)
                }
                res.emailTaken -> errorMsg = "This email already has an account — tap \"Sign in\" below instead."
                else -> errorMsg = res.serviceMessage ?: "Could not create the account right now. Try again."
            }
        }
    }

    /** Google one-tap sign-up (auto-verified by Google). */
    fun startGoogleSignUp() {
        val helper = googleHelper
        if (helper == null) {
            errorMsg = "Google sign-up is unavailable on this device."
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
            if (idToken == null) {
                googleLoading = false
                statusMsg = null
                errorMsg = "Google sign-up was cancelled."
                return@launch
            }
            val result: AuthResult = try {
                authRepository.signInWithGoogle(idToken, role)
            } catch (t: Throwable) {
                AuthResult.Failure(t.message ?: "Google sign-up failed")
            }
            val stillActive = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive ?: true
            if (!stillActive) return@launch
            googleLoading = false
            statusMsg = null
            when (result) {
                is AuthResult.Success -> {
                    try {
                        onSubmit(result.role)
                    } catch (t: Throwable) {
                        android.util.Log.e("SignUpScreen", "post-google nav failed", t)
                        errorMsg = "Account created, but the dashboard failed to load: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
                is AuthResult.Failure -> errorMsg = result.message
                is AuthResult.VerificationPending -> onSubmit(role)
                is AuthResult.RoleMismatch -> errorMsg = "This Google account already exists as a ${result.actual.displayName} — sign in with it instead, or use a different Gmail."
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
            BrandedAuthHeader(
                title = "Create your account",
                sub = "Fill the form or tap Google — either way your account works on app AND web from the first second.",
            )

            AuthStatusSlot(statusMsg)

            Spacer(modifier = Modifier.height(16.dp))

            StyledAuthField(
                value = name,
                onValueChange = { name = it; errorMsg = null },
                label = "Full name",
                placeholder = "Amina Nakato",
                leadingIcon = Icons.Filled.Person,
                index = 0,
            )
            Spacer(modifier = Modifier.height(12.dp))
            StyledAuthField(
                value = email,
                onValueChange = { email = it; errorMsg = null },
                label = "Email",
                placeholder = "you@example.com",
                leadingIcon = Icons.Filled.Email,
                keyboardType = KeyboardType.Email,
                index = 1,
            )
            Spacer(modifier = Modifier.height(12.dp))
            StyledAuthField(
                value = phone,
                onValueChange = { phone = it; errorMsg = null },
                label = "Phone (optional)",
                placeholder = "+256 7XX XXX XXX",
                leadingIcon = Icons.Filled.Phone,
                keyboardType = KeyboardType.Phone,
                index = 2,
            )
            if (role == Role.SELLER) {
                Spacer(modifier = Modifier.height(12.dp))
                StyledAuthField(
                    value = storeName,
                    onValueChange = { storeName = it; errorMsg = null },
                    label = "Store name",
                    placeholder = "Nakato Collections",
                    leadingIcon = Icons.Filled.Storefront,
                    index = 3,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PasswordField(
                value = password,
                onValueChange = { password = it; errorMsg = null },
                label = "Password",
                leadingIcon = Icons.Filled.Lock,
                index = 4,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordField(
                value = confirm,
                onValueChange = { confirm = it; errorMsg = null },
                label = "Confirm password",
                leadingIcon = Icons.Filled.Lock,
                imeAction = ImeAction.Done,
                onImeAction = { createAccount() },
                index = 5,
            )

            Spacer(modifier = Modifier.height(16.dp))
            PrimaryCtaButton(
                label = if (role == Role.SELLER) "Create seller account" else "Create account",
                loading = loading,
                onClick = { createAccount() },
                index = 6,
            )

            AuthDivider("or sign up with", index = 7)

            GoogleAuthButton(
                label = if (googleLoading) "Creating your account…" else "Sign up with Google",
                loading = googleLoading,
                onClick = { if (!googleLoading) startGoogleSignUp() },
                index = 8,
            )

            AuthErrorSlot(errorMsg)

            BrandedFooterLink(
                text = "Already have an account?",
                linkText = "Sign in",
                onClick = onSignInInstead,
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "We'll email you a 6-digit verification code to keep your account safe.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
