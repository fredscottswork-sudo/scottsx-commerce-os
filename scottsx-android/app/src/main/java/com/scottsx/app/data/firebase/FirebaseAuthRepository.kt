package com.scottsx.app.data.firebase

import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.CurrentUserPayload
import com.scottsx.app.data.remote.V2Client

/**
 * Orchestrates the Firebase email-link verification flow and exchanges
 * the Firebase identity for a ScottsTechX JWT via the backend.
 *
 * Flow:
 *   1. createAccount()          -> Firebase user created + signed in
 *   2. sendVerificationEmail()  -> Firebase emails the real verification link
 *   3. reloadAndCheckVerified() -> user.reload() + isEmailVerified
 *   4. exchangeForJwt()         -> POST /auth/firebase/sign-in -> our JWT
 */
object FirebaseAuthRepository {

    /** Create account + send verification email. Returns error message or null on success. */
    suspend fun signUpAndSendVerification(email: String, password: String): String? {
        val user = FirebaseBridge.createAccount(email, password)
            ?: return "Could not create account. Check the email/password and try again."
        val sent = FirebaseBridge.sendVerificationEmail()
        return if (sent) null else "Account created but verification email could not be sent."
    }

    /** Re-check email verification status from the server. */
    suspend fun reloadAndCheckVerified(): Boolean {
        FirebaseBridge.reloadUser()
        return FirebaseBridge.isEmailVerified()
    }

    /** Exchange the Firebase idToken for a ScottsTechX JWT and cache the session. */
    suspend fun exchangeForJwt(): Boolean {
        val idToken = FirebaseBridge.idToken() ?: return false
        val result = V2Client.signInWithFirebase(idToken) ?: return false
        SessionCache.save(result.token, toCurrentUser(result.user))
        return true
    }

    /** Upgrade the (verified) account to a seller. */
    suspend fun upgradeToSeller(): Boolean {
        val idToken = FirebaseBridge.idToken() ?: return false
        val result = V2Client.upgradeToSeller(idToken) ?: return false
        SessionCache.save(result.token, toCurrentUser(result.user))
        return true
    }

    private fun toCurrentUser(p: CurrentUserPayload) =
        com.scottsx.app.CurrentUser(
            id = p.id,
            email = p.email,
            displayName = p.displayName,
            phone = p.phone,
            role = p.role,
            emailVerified = p.emailVerified,
            profilePhotoUrl = p.profilePhotoUrl,
            city = p.city,
        )
}
