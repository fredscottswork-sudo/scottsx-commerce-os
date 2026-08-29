package com.scottsx.app

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.scottsx.app.data.remote.V2Client

/**
 * Google Sign-In via Credential Manager (the One Tap successor).
 *
 * Flow: the system sheet returns a Google ID token minted for our WEB OAuth
 * client (the `serverClientId`), and the backend verifies it on
 * POST /api/v1/auth/google — signature against Google's JWKS, issuer and
 * audience all checked server-side. Google has already proven the email, so
 * the account arrives verified and skips the email-verification gate.
 */
object GoogleSignInHelper {

    /**
     * The WEB OAuth client id from google-services.json (client_type 3,
     * project scottstechx-52bab). Public by design — it is baked into every
     * client and is one of the audiences the backend accepts. The ID token
     * must carry this audience or the server refuses it.
     */
    private const val SERVER_CLIENT_ID =
        "911393008938-f0an8p59rlkhimcnn9rdqbtbi1aa9hbk.apps.googleusercontent.com"

    /** What a sign-in attempt ended as — the UI needs to tell these apart. */
    sealed class Outcome {
        data object Success : Outcome()
        /** The user dismissed the sheet — not an error, show nothing. */
        data object Cancelled : Outcome()
        /** No Google account on the device / Play Services unavailable. */
        data class Unavailable(val message: String) : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    /**
     * Launch the Google account sheet and exchange the returned ID token for
     * a ScottsTechX session. Must be called with an Activity context — the
     * credential sheet needs a window to attach to.
     */
    suspend fun signIn(activityContext: Context): Outcome {
        val response = try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(SERVER_CLIENT_ID)
                // Show every Google account on the device, not only ones that
                // have signed in to ScottsTechX before — new users need a way in.
                .setFilterByAuthorizedAccounts(false)
                .build()
            CredentialManager.create(activityContext).getCredential(
                context = activityContext,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
        } catch (e: GetCredentialCancellationException) {
            return Outcome.Cancelled
        } catch (e: NoCredentialException) {
            return Outcome.Unavailable("No Google account found on this device. Add one in Settings, or use email.")
        } catch (e: Exception) {
            return Outcome.Failed(e.message ?: "Google sign-in is unavailable right now.")
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return Outcome.Failed("Unexpected credential type from Google.")
        }

        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: Exception) {
            return Outcome.Failed("Could not read the Google credential.")
        }

        return if (exchange(idToken)) {
            Outcome.Success
        } else {
            Outcome.Failed("The server rejected the Google sign-in. Try again.")
        }
    }

    /**
     * Exchange a Google idToken for a ScottsTechX session.
     * Returns true when the session was stored in SessionCache.
     *
     * Writes into BOTH session stores: the root SessionCache (user
     * profile) and the network-session mirror (bearer JWT + role/userId
     * for V2Client + AI tooling + chat attribution). Without the mirror
     * write, every authenticated API call used to go out unauthenticated
     * (401 → "dashboard shows nothing", "messaging doesn't work").
     */
    suspend fun exchange(idToken: String): Boolean {
        val result = V2Client.signInWithGoogle(idToken) ?: return false
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
            announce = true,
        )
        com.scottsx.app.data.Session.adoptSession(
            token = result.token,
            userId = result.user.id,
            role = if (result.user.role.equals("seller", true)) com.scottsx.app.data.domain.Role.SELLER
                  else com.scottsx.app.data.domain.Role.BUYER,
            displayName = result.user.displayName,
            email = result.user.email,
            avatarUrl = result.user.profilePhotoUrl,
            storeLocation = result.user.city,
        )
        return true
    }
}
