package com.scottsx.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
 * Screen 5 — Sign-Up (Google-only).
 *
 * Mirrors [LoginScreen]: the Google account IS the account, so creating
 * one is a single tap. There are no email/password fields, no phone,
 * no Apple button — the Google identity supplies name, email and a
 * verified address automatically, which makes this the fastest possible
 * sign-up (one tap on the Google button, straight into the dashboard).
 *
 * For sellers the backend seeds a storefront row (store_settings) keyed
 * to the new user at first sign-in; the display name becomes the initial
 * store name and everything else (logo, category, location) is edited
 * later in the seller dashboard → Store settings, same as on the web.
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
    var loading by remember { mutableStateOf(false) }
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
            loading = false
        }
    }

    fun startGoogleSignUp() {
        val helper = googleHelper
        if (helper == null) {
            errorMsg = "Google sign-up is unavailable on this device."
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
            if (idToken == null) {
                loading = false
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
            loading = false
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
                is AuthResult.VerificationPending -> onVerificationPending(result.email)
                is AuthResult.RoleMismatch -> {
                    errorMsg = "This Google account already exists as a ${result.actual.displayName}."
                    onRoleMismatch(result.actual)
                }
            }
        }
    }

    GoogleOnlyAuthLayout(role = role, onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoogleOnlyAuthHeader(
                title = "Create your account",
                sub = "One Google tap creates your " +
                    (if (role == Role.SELLER) "seller account — your store is ready to stock immediately"
                     else "buyer account — start shopping right away") +
                    ". No forms, no passwords, no wait.",
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

            // THE Google button — authentic look, instant flow.
            val shape = RoundedCornerShape(27.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(shape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE1E6EF), shape)
                    .clickable(enabled = !loading) { startGoogleSignUp() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
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
                        text = if (loading) "Creating your account…" else "Sign up with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF101828),
                    )
                }
            }

            GoogleOnlyErrorSlot(errorMsg)

            GoogleOnlyFooterLink(
                text = "Already have an account?",
                linkText = "Sign in",
                onClick = onSignInInstead,
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Your Google account signs you into app AND web — products, orders, chats and cart stay in sync.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
