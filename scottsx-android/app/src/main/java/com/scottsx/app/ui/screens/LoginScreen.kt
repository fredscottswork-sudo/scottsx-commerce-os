package com.scottsx.app.ui.screens

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsRole
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.CurrentUser
import com.scottsx.app.R
import com.scottsx.app.SessionCache
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.AppleLogo
import com.scottsx.app.ui.components.AuthFilledField
import com.scottsx.app.ui.components.AuthGradientButton
import com.scottsx.app.ui.components.AuthSheet
import com.scottsx.app.ui.components.FuturisticBackdrop
import com.scottsx.app.ui.components.GoogleAuthButton
import com.scottsx.app.ui.components.OrDivider
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * The login screen, recreated from the reference design: a white
 * bottom-sheet panel over the dark futuristic backdrop, with the logo,
 * the "Logging in as [Role]" pill, the combined email/phone + password
 * form, the gradient Login pill, Google and Apple social buttons and the
 * registration link.
 *
 * The role comes in from the welcome screen's choice (nav argument), so
 * Buyer → Login shows "Logging in as Buyer" and Seller → Login shows
 * "Logging in as Seller" — one screen, no duplicate.
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (role: String?) -> Unit,
    onGoSignUp: (role: String) -> Unit,
    onForgotPassword: () -> Unit,
    /** Buyer or seller as chosen on the welcome screen. */
    initialRole: String = "buyer",
) {
    // Named accountRole (not "role"): a bare `role` would shadow the
    // SemanticsPropertyReceiver.role inside the .semantics { } blocks below.
    val accountRole = if (initialRole == "seller") "seller" else "buyer"
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var appleNote by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val ident = identifier.trim()
        val identError = when {
            ident.isEmpty() -> "Enter your email or phone number"
            Patterns.EMAIL_ADDRESS.matcher(ident).matches() -> null
            ident.replace(Regex("[^0-9]"), "").length in 7..15 -> null
            else -> "Enter a valid email or phone number"
        }
        val formError = identError ?: (if (password.isEmpty()) "Enter your password" else null)
        if (formError != null) {
            error = formError
            return
        }
        if (loading) return
        loading = true
        error = null
        scope.launch {
            val result = V2Client.login(ident, password)
            if (result != null && result.token.isNotBlank()) {
                SessionCache.save(
                    result.token,
                    CurrentUser(
                        id = result.user.id,
                        email = result.user.email,
                        displayName = result.user.displayName,
                        phone = result.user.phone,
                        role = result.user.role,
                        emailVerified = result.user.emailVerified,
                        profilePhotoUrl = result.user.profilePhotoUrl,
                        city = result.user.city,
                    ),
                )
                onLoggedIn(result.user.role)
            } else {
                error = "Invalid email or password"
            }
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FuturisticBackdrop(modifier = Modifier.matchParentSize(), purpleTint = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The dark band and the sheet must clear the status bar and
                // the gesture pill on every device.
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AuthSheet(onBack = onBack) {
                // Logo — small and clean, undistorted.
                Image(
                    painter = painterResource(R.drawable.brand_mark),
                    contentDescription = "ScottsTechX",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(60.dp)
                        .padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))

                // Role indicator — reflects the choice made on the welcome
                // screen, so the user always knows which account type they
                // are about to use.
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Logging in as",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFE7EEFF))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (accountRole == "seller") "Seller" else "Buyer",
                            color = ScottsTechXColors.BlueDeep,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Welcome back to\nScottsTechX",
                    color = ScottsTechXColors.OnLight,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 33.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your next opportunity is one tap away.",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))

                // One identifier field for both emails and phone numbers —
                // the backend resolves either.
                AuthFilledField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = "Email or Phone Number",
                    placeholder = "Enter your email or phone number",
                )
                Spacer(Modifier.height(16.dp))
                AuthFilledField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "Enter your password",
                    isPassword = true,
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Forgot Password?",
                        color = ScottsTechXColors.BluePrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onForgotPassword() }
                            .semantics { role = SemanticsRole.Button }
                            .padding(vertical = 4.dp),
                    )
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
                }
                Spacer(Modifier.height(18.dp))
                AuthGradientButton(
                    text = "Login",
                    loading = loading,
                    onClick = { submit() },
                )

                Spacer(Modifier.height(20.dp))
                OrDivider(label = "or continue with")
                Spacer(Modifier.height(16.dp))
                GoogleAuthButton(
                    onSuccess = { onLoggedIn(SessionCache.user.value?.role) },
                    onError = { error = it },
                    label = "Login with Google",
                )
                Spacer(Modifier.height(12.dp))
                // Apple Sign-In: the UI is in place and the action is
                // abstracted behind this callback, so a provider can be
                // wired in later without touching the layout.
                Button(
                    onClick = { appleNote = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF4F6FA),
                        contentColor = Color(0xFF101828),
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE1E6EF)),
                ) {
                    AppleLogo(modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Apple", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (appleNote) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Apple Sign-In isn't connected on this build yet — it will light up here as soon as it is configured.",
                        color = ScottsTechXColors.OnLightTertiary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "New here? ",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Create your ScottsTechX account",
                        color = ScottsTechXColors.BluePrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onGoSignUp(accountRole) }
                            .semantics { role = SemanticsRole.Button },
                    )
                }
            }
        }
    }
}
