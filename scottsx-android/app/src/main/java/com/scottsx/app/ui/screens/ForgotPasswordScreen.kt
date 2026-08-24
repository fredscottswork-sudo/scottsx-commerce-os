package com.scottsx.app.ui.screens

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.AuthFilledField
import com.scottsx.app.ui.components.AuthGradientButton
import com.scottsx.app.ui.components.AuthSheet
import com.scottsx.app.ui.components.FuturisticBackdrop
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Password reset.
 *
 * The layout is the real thing — same white sheet, same fields, same button
 * as the login screen — and the action is wired to the backend client
 * ([V2Client.requestPasswordReset]). The API endpoint does not exist yet, so
 * until the backend ships it the button answers honestly: it never fakes a
 * success, it tells the user the feature is not live yet and points them at
 * Google sign-in. When the endpoint lands, no UI change is needed.
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
) {
    var identifier by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        FuturisticBackdrop(modifier = Modifier.matchParentSize(), purpleTint = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Clear the status bar and the gesture pill on every device.
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AuthSheet(onBack = onBack) {
                Image(
                    painter = painterResource(R.drawable.brand_mark),
                    contentDescription = "ScottsTechX",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(56.dp)
                        .padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Forgot your password?",
                    color = ScottsTechXColors.OnLight,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter the email or phone number linked to your account and we'll send you a reset link.",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))
                AuthFilledField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = "Email or Phone Number",
                    placeholder = "Enter your email or phone number",
                )

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
                }
                info?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        color = ScottsTechXColors.BluePrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(18.dp))
                AuthGradientButton(
                    text = "Send reset link",
                    loading = busy,
                    onClick = {
                        val ident = identifier.trim()
                        val identError = when {
                            ident.isEmpty() -> "Enter your email or phone number"
                            Patterns.EMAIL_ADDRESS.matcher(ident).matches() -> null
                            ident.replace(Regex("[^0-9]"), "").length in 7..15 -> null
                            else -> "Enter a valid email or phone number"
                        }
                        if (identError != null) {
                            error = identError
                            info = null
                            return@onClick
                        }
                        if (busy) return@onClick
                        busy = true
                        error = null
                        info = null
                        scope.launch {
                            val sent = V2Client.requestPasswordReset(ident)
                            info = if (sent) {
                                "If an account exists for $ident, a password reset link is on its way."
                            } else {
                                "The reset endpoint isn't live on the current API build yet — this screen is already wired to it and will work the moment it ships. In the meantime you can sign in with Google."
                            }
                            busy = false
                        }
                    },
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Trouble signing in? You can also sign in with Google from the login screen.",
                    color = ScottsTechXColors.OnLightTertiary,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
