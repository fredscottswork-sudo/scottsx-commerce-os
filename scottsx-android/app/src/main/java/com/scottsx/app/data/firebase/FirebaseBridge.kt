package com.scottsx.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around the Firebase Auth SDK so screens never touch
 * Firebase APIs directly.
 */
object FirebaseBridge {

    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    fun currentUser(): FirebaseUser? = auth().currentUser

    /** Create the account (this also logs the user into Firebase). */
    suspend fun createAccount(email: String, password: String): FirebaseUser? =
        try {
            auth().createUserWithEmailAndPassword(email, password).await().user
        } catch (e: Exception) {
            null
        }

    /** Ask Firebase to email the verification link to the signed-in user. */
    suspend fun sendVerificationEmail(): Boolean {
        return try {
            val user = currentUser() ?: return false
            user.sendEmailVerification().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Re-read the account from the server (picks up emailVerified). */
    suspend fun reloadUser(): Boolean {
        return try {
            val user = currentUser() ?: return false
            user.reload().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isEmailVerified(): Boolean = currentUser()?.isEmailVerified == true

    /** Fresh idToken to hand to the backend (POST /auth/firebase/sign-in). */
    suspend fun idToken(): String? =
        try {
            currentUser()?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            null
        }

    /** Classic email/password sign-in — returns the idToken on success. */
    suspend fun signIn(email: String, password: String): String? =
        try {
            auth().signInWithEmailAndPassword(email, password).await().user
                ?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            null
        }

    fun signOut() {
        auth().signOut()
    }
}
