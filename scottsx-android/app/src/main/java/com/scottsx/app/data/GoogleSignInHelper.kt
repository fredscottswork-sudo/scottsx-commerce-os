package com.scottsx.app.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.scottsx.app.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper around the Google Sign-In SDK.
 *
 * Two flows are supported:
 *
 *  1. [trySilentSignIn] — fires a non-blocking request to see
 *     whether the SDK already has a cached Google account on this
 *     device. Returns the id_token if so, or null if not.
 *
 *  2. [signInWithInteractive] — launches the system account picker
 *     through the caller-provided [ActivityResultLauncher] and
 *     resolves to an id_token once the user finishes.
 *
 * The Compose layer wires both together: on "Login with Google",
 * it tries silent sign-in first; if that returns null it falls back
 * to the interactive flow.
 */
class GoogleSignInHelper(context: Context) {

    private val appContext: Context = context.applicationContext
    private val client: GoogleSignInClient = GoogleSignIn.getClient(
        appContext,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(appContext.getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build(),
    )

    /**
     * Returns true if the Google SDK still has a cached last-signed-in
     * account on this device, *without* triggering any UI. Used by the
     * LoginScreen to decide whether to show the "Use a different Google
     * account" button.
     */
    fun hasCachedAccount(): Boolean =
        GoogleSignIn.getLastSignedInAccount(appContext) != null

    /**
     * Force the next sign-in to open the system account chooser, even
     * if the Google SDK has a cached account. This is the behavior the
     * "Use a different Google account" button triggers. Internally we
     * call `client.signOut()` on the SDK only — *not* on FirebaseAuth —
     * so the user's Firebase session is preserved for any unrelated
     * flow, but the next Google tap must show the picker.
     *
     * Safe to call repeatedly. No-ops if the helper is in a clean state.
     */
    fun forcePickerOnNextSignIn() {
        runCatching { client.signOut() }
    }

    /** Silent sign-in for already-cached accounts. Returns null otherwise. */
    suspend fun trySilentSignIn(): String? {
        return try {
            awaitTask(client.silentSignIn()).idToken
        } catch (_: ApiException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    /**
     * Launch the system account picker through the supplied
     * [launcher] and resolve with the id_token once the user
     * finishes.
     *
     * @param launcher returned by
     *        `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())`
     *        in Compose. The Compose layer must register its
     *        callback via [handleResult] on the helper.
     */
    suspend fun signInWithInteractive(
        launcher: ActivityResultLauncher<Intent>,
    ): String {
        // Double-tap guard: a second launch while a picker is already in
        // flight used to OVERWRITE `pending`, orphaning the first
        // continuation for the full 180 s timeout while the UI sat with
        // a spinner. Refuse the second launch instantly instead.
        if (pending != null) throw IllegalStateException("Google sign-in already in flight")
        return suspendCancellableCoroutine<String> { cont ->
            pending = cont
            cont.invokeOnCancellation { if (pending === cont) pending = null }
            launcher.launch(client.signInIntent)
        }
    }

    /**
     * Called by the ActivityResultLauncher callback in the Compose
     * layer after the picker returns. Resolves the suspended
     * coroutine with the id_token or with a cancellation.
     *
     * IMPORTANT: this callback runs on the **main thread** as part
     * of `Activity.onActivityResult` dispatch. We must NOT throw
     * here — any uncaught exception would bubble up to the Android
     * framework and crash the process.
     */
    fun handleResult(result: ActivityResult) {
        val cont = pending ?: return
        pending = null

        // Decode the intent FIRST and for both paths: a RESULT_CANCELED may
        // still carry an ApiException status (e.g. DEVELOPER_ERROR), which is
        // NOT the user cancelling — reporting it as "cancelled" hid the real
        // misconfiguration from the very first bug report.
        val intent: Intent? = result.data
        var statusCode: Int? = null
        var account: GoogleSignInAccount? = null
        try {
            val task: Task<GoogleSignInAccount> =
                GoogleSignIn.getSignedInAccountFromIntent(intent)
            account = task.getResult(ApiException::class.java)
        } catch (ae: ApiException) {
            statusCode = ae.status.statusCode
            android.util.Log.w(
                "GoogleSignInHelper",
                "Google sign-in ApiException: ${ae.status.statusCode} (${ae.status.statusMessage})",
            )
        } catch (t: Throwable) {
            android.util.Log.w("GoogleSignInHelper", "Google sign-in failed", t)
            cont.resumeWithException(t)
            return
        }

        val idToken = account?.idToken
        if (result.resultCode == Activity.RESULT_OK) {
            if (!idToken.isNullOrBlank()) {
                try {
                    cont.resume(idToken)
                } catch (e: Throwable) {
                    // Continuation already cancelled or completed; nothing to do.
                    android.util.Log.w("GoogleSignInHelper", "continuation already done", e)
                }
            } else {
                android.util.Log.w("GoogleSignInHelper", "OK result carried no id_token")
                cont.resumeWithException(IllegalStateException("Google sign-in returned no id_token"))
            }
            return
        }

        cont.resumeWithException(IllegalStateException(describeFailure(statusCode)))
    }

    /**
     * Turn a Google status code into a message that says what actually
     * happened. Only a genuine user dismissal keeps the word "cancelled".
     */
    private fun describeFailure(statusCode: Int?): String = when (statusCode) {
        null, GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
            "Google sign-in cancelled."
        CommonStatusCodes.NETWORK_ERROR ->
            "Google sign-in needs a working network connection — check connectivity and retry."
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Google sign-in is rejected for this build (code 10): its SHA-1 fingerprint is not " +
                "registered in the Google Cloud project for com.scottsx.app. Register the SHA-1 " +
                "and SHA-256 printed by the build (keystores/README.md), then retry — no reinstall needed."
        CommonStatusCodes.INTERNAL_ERROR ->
            "Google sign-in hit an internal error (code 8) — retry in a moment."
        else ->
            "Google sign-in failed (code $statusCode). Try again, or use email sign-in."
    }

    fun signOut() {
        client.signOut()
    }

    private var pending: Continuation<String>? = null
}

private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
    task.addOnSuccessListener { cont.resume(it) }
    task.addOnFailureListener { cont.resumeWithException(it) }
    cont.invokeOnCancellation { /* nothing — task continues regardless */ }
}

/** Marker constant used by Compose code to remember the contract type. */
@Suppress("unused")
val GoogleSignInContract = ActivityResultContracts.StartActivityForResult()