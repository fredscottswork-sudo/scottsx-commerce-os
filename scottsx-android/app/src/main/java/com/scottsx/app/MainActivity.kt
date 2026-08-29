package com.scottsx.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.scottsx.app.data.preferences.LocalThemePreference
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.navigation.AppNavigation
import com.scottsx.app.ui.theme.ColorContext
import com.scottsx.app.ui.theme.LocalColorContext
import com.scottsx.app.ui.theme.ScottsTechXTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Seed activity. Hosts the NavHost and supplies the [ThemePreference]
 * singleton (persisted theme) + the default [ColorContext.Dark] so
 * the cinematic splash/onboarding screens stay on-brand while the
 * rest of the app follows the user's chosen theme.
 *
 * Also provides [LocalThemePreference] so any screen (e.g. the
 * seller dashboard) that reads `LocalThemePreference.current`
 * directly gets the same singleton instance.
 *
 * When the app is opened via the Firebase email-verification deep
 * link (manifest intent-filter for `scottstechx-52bab.firebaseapp.com`
 * and the `scottsx://` scheme), we eagerly call
 * `FirebaseAuth.currentUser.reload()` so the freshly-verified flag
 * is available by the time the user lands on VerifyEmailPendingScreen
 * and taps "I've verified — continue".
 */
class MainActivity : ComponentActivity() {

    /** Runtime permission prompt for POST_NOTIFICATIONS (Android 13+).
     *  On older API levels the permission is install-time and this
     *  launch is a silent no-op. */
    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { /* result ignored — posting is guarded */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars — targetSdk 35 makes this mandatory, so
        // declare it explicitly: every screen handles its own insets via the
        // ScreenScaffold/statusBarSpacer/navBarSpacer helpers.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleDeepLinkVerification(intent)
        refreshBackendSession()
        // Android 13+ requires a runtime grant before any notification —
        // sign-in ping, chat alerts, order updates — reaches the phone.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Register this device for pushes + create channels even before
        // a sign-in completes this launch (channels are cheap and idempotent).
        com.scottsx.app.data.push.ScottsMessagingService.ensureChannels(this)
        setContent {
            val themePref = remember { ThemePreference.get(applicationContext) }
            // Stage 5: cross-device theme sync — pull the saved theme
            // from /api/v1/settings/v2 on first frame so a new
            // device picks up the user's choice.
            LaunchedEffect(Unit) {
                themePref.loadFromServer()
            }
            CompositionLocalProvider(
                LocalColorContext provides ColorContext.Dark,
                LocalThemePreference provides themePref,
            ) {
                ScottsTechXTheme(
                    context = ColorContext.Dark,
                    themePreference = themePref,
                ) {
                    AppNavigation()
                }
            }
        }
    }

    /**
     * Called from onCreate and onNewIntent. If the launching Intent
     * is a Firebase email-verification deep link, refresh the cached
     * `isEmailVerified` flag on the currently signed-in user. The
     * VerifyEmailPendingScreen will then see the updated flag the
     * moment the user taps "I've verified — continue".
     */
    private fun handleDeepLinkVerification(intent: Intent?) {
        if (intent == null) return
        val isVerificationLink = when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data?.toString() ?: return
                data.contains("firebaseapp.com") || data.contains("scottsx://")
            }
            else -> false
        }
        if (!isVerificationLink) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        // Fire-and-forget. reload() updates the cached emailVerified
        // flag from Firebase's servers; the user navigates to
        // VerifyEmailPendingScreen and "I've verified — continue"
        // calls reload() again to double-check before entering.
        user.reload()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkVerification(intent)
    }

    /**
     * COLD-START SESSION RESTORE. Firebase Auth persists its credential
     * across process death, but the backend JWT does not — so after a
     * cold start every authenticated /api/v1 call would go out with no
     * Authorization header and the user would see empty dashboards,
     * blank messages and dead notifications until they sign in again.
     * If a Firebase session survived, silently re-exchange it for a
     * backend session on app launch (fire-and-forget; the screens that
     * need auth retry via their load paths, and each subsequent apiCall
     * picks the token up the moment it lands).
     */
    private fun refreshBackendSession() {
        if (com.scottsx.app.data.Session.tokenOrNull() != null) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val firebaseToken = user.getIdToken(false).await().token ?: return@launch
                val result = com.scottsx.app.data.remote.V2Client
                    .signInWithFirebase(idToken = firebaseToken) ?: return@launch
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
                com.scottsx.app.data.Session.adoptSession(
                    token = result.token,
                    userId = result.user.id,
                    role = if (result.user.role.equals("seller", true))
                        com.scottsx.app.data.domain.Role.SELLER
                    else com.scottsx.app.data.domain.Role.BUYER,
                    displayName = result.user.displayName,
                    email = result.user.email,
                    avatarUrl = result.user.profilePhotoUrl,
                    storeLocation = result.user.city,
                )
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "Cold-start session restore failed; will retry on next launch.", t)
            }
        }
    }
}
