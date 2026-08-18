package com.scottsx.app

import com.scottsx.app.data.remote.V2Client

/**
 * Google Sign-In helper.
 *
 * NOTE: the actual Google sign-in UI (play-services-auth OneTap / Credential
 * Manager) is wired to this stub when you add the dependency. The backend
 * endpoint is ready: POST /api/v1/auth/google { idToken }.
 *
 * To enable real Google sign-in:
 *   1. Add `implementation("com.google.android.gms:play-services-auth:21.2.0")`
 *   2. Launch the sign-in intent from your activity
 *   3. Call GoogleSignInHelper.exchange(idToken) with the returned idToken
 */
object GoogleSignInHelper {

    /**
     * Exchange a Google idToken for a ScottsTechX session.
     * Returns true when the session was stored in SessionCache.
     */
    suspend fun exchange(idToken: String): Boolean {
        val result = V2Client.signInWithGoogle(idToken) ?: return false
        SessionCache.save(
            result.token,
            com.scottsx.app.CurrentUser(
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
        return true
    }

    /** TODO(future): launch OneTap sign-in from the activity and return the idToken. */
    fun pendingGoogleSignIn(): Boolean = false
}
