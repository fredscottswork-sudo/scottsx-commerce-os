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
        return suspendCancellableCoroutine<String> { cont ->
            pending = cont
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

        if (result.resultCode != Activity.RESULT_OK) {
            // User cancelled or error — resume with cancellation exception
            cont.resumeWithException(IllegalStateException("Google sign-in cancelled"))
            return
        }

        val intent: Intent? = result.data
        try {
            val task: Task<GoogleSignInAccount> =
                GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken == null) {
                android.util.Log.w(
                    "GoogleSignInHelper",
                    "Google sign-in returned no id_token — silently ignoring.",
                )
                cont.resumeWithException(IllegalStateException("Google sign-in returned no id_token"))
                return
            }
            // Resume with the id token
            try {
                cont.resume(idToken)
            } catch (e: Throwable) {
                // Continuation already cancelled or completed; nothing to do.
                android.util.Log.w("GoogleSignInHelper", "continuation already done", e)
            }
        } catch (e: Throwable) {
            android.util.Log.w("GoogleSignInHelper", "Google sign-in failed", e)
            cont.resumeWithException(e)
        }
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