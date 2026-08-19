package com.scottsx.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.firebase.FirebaseAuthRepository
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * The verification gate.
 *
 * The backend refuses every private route with 403 EMAIL_NOT_VERIFIED until the
 * address is proven, so an account that lands here cannot use the app at all.
 * This screen exists to get it unstuck, and it is the only screen an unverified
 * account can usefully reach.
 *
 * Two proofs are accepted because two sign-up paths exist:
 *   - the Firebase link  (normal path - tap it in the mail app)
 *   - a six-digit code   (fallback when Firebase Auth is unavailable)
 *
 * Signing out stays available on purpose: a typo in the address would otherwise
 * trap the user on a screen they can never pass.
 */
@Composable
fun VerifyEmailScreen(
    onVerified: (role: String?) -> Unit,
    onSignOut: () -> Unit,
) {
    val email = SessionCache.user.value?.email.orEmpty()

    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var resending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var devCode by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    /** Pull the latest state from the server and let the user through if proven. */
    suspend fun checkNow(silent: Boolean): Boolean {
        // Firebase first: the link is the normal path, and reload() is what
        // notices it was tapped in a different app.
        val viaFirebase = try {
            FirebaseAuthRepository.reloadAndCheckVerified()
        } catch (e: Exception) {
            false
        }
        if (viaFirebase) {
            // Trade the refreshed Firebase identity for a JWT the backend
            // accepts; without this the old token still reads as unverified.
            FirebaseAuthRepository.exchangeForJwt()
        }
        val me = V2Client.fetchMe()
        if (me?.emailVerified == true) {
            SessionCache.user.value?.let { SessionCache.updateUser(it.copy(emailVerified = true)) }
            onVerified(me.role)
            return true
        }
        if (!silent) {
            error = "Not verified yet - open the link in your email, then try again."
        }
        return false
    }

    // The link opens in the mail app, not here, so nothing would otherwise tell
    // this screen it was tapped. Check once on arrival; the user returning to
    // the app is the likeliest moment for it to have happened.
    LaunchedEffect(Unit) {
        checkNow(silent = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Scrolling content: keep the status bar and the gesture pill clear.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SettingsRow(title = "", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = onSignOut)

        Text("Verify your email", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (email.isBlank()) {
                "Confirm your email address to finish setting up your account."
            } else {
                "We sent a verification link to $email. Open it, then come back here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Verifying keeps your account recoverable and protects buyers and " +
                "sellers from fake listings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        devCode?.let {
            Text(
                "Email delivery is not set up for this server yet, so no link " +
                    "could be sent. Use this code instead: $it",
                color = ScottsTechXColors.WarningAmber,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
        }

        info?.let {
            Text(it, color = ScottsTechXColors.SuccessGreen, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        error?.let {
            Text(it, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }

        PrimaryButton(
            text = "I've opened the link",
            enabled = !checking,
            loading = checking,
            onClick = {
                checking = true
                error = null
                info = null
                scope.launch {
                    checkNow(silent = false)
                    checking = false
                }
            },
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Or enter the 6-digit code",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        InputField(
            value = code,
            onValueChange = { entered -> code = entered.filter { it.isDigit() }.take(6) },
            label = "Verification code",
            placeholder = "123456",
            keyboardType = KeyboardType.Number,
        )
        Spacer(Modifier.height(10.dp))

        PrimaryButton(
            text = "Verify",
            enabled = code.length == 6 && !busy,
            loading = busy,
            onClick = {
                busy = true
                error = null
                info = null
                scope.launch {
                    val user = V2Client.confirmVerification(code)
                    if (user != null && user.emailVerified) {
                        SessionCache.user.value?.let {
                            SessionCache.updateUser(it.copy(emailVerified = true))
                        }
                        onVerified(user.role)
                    } else {
                        error = "That code is not correct, or it has expired. " +
                            "Ask for a new one below."
                    }
                    busy = false
                }
            },
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                enabled = !resending,
                onClick = {
                    resending = true
                    error = null
                    info = null
                    scope.launch {
                        val res = V2Client.requestVerification()
                        when {
                            res == null ->
                                error = "Could not reach the server. Check your connection."
                            res.alreadyVerified -> {
                                SessionCache.user.value?.let {
                                    SessionCache.updateUser(it.copy(emailVerified = true))
                                }
                                onVerified(SessionCache.user.value?.role)
                            }
                            else -> {
                                devCode = res.devCode
                                info = if (res.sent) {
                                    "Sent - check your inbox, and your spam folder."
                                } else {
                                    "A new code was generated."
                                }
                            }
                        }
                        resending = false
                    }
                },
            ) {
                Text(if (resending) "Sending..." else "Resend email")
            }

            // Always reachable: a wrong address must not be a dead end.
            TextButton(onClick = onSignOut) {
                Text("Use a different account", color = ScottsTechXColors.ErrorRed)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Wrong address? Sign out and register again with the correct one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
