package com.scottsx.app.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.scottsx.app.data.AuthRepository
import com.scottsx.app.data.domain.SessionCache
import kotlinx.coroutines.tasks.await

/**
 * Stage 5 — Firebase Auth primary repository.
 *
 * Replaces the existing phone/email+password flow with Firebase
 * Authentication. Falls back to the existing [AuthRepository] only
 * if Firebase is unavailable (offline / not configured).
 *
 * Flow:
 *   1. signInWithGoogle(idToken)   → FirebaseAuth.signInWithCredential
 *   2. After sign-in, call /api/v1/auth/firebase/sign-in on the
 *      backend with the Firebase ID token. The backend verifies it,
 *      auto-provisions the user in the `users` table, and returns an
 *      HS256 JWT for the rest of the backend. We keep both tokens:
 *      Firebase for the Firestore SDK, HS256 for the REST API.
 */
object FirebaseAuthRepository {

    private const val TAG = "FirebaseAuthRepo"

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
        return result.user ?: throw IllegalStateException("Firebase signIn returned null user")
    }

    /**
     * Update the Firebase user's display name. Returns the new value.
     */
    suspend fun setDisplayName(user: FirebaseUser, displayName: String): String {
        val req = UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        user.updateProfile(req).await()
        return displayName
    }

    /**
     * Send a Firebase email verification link. The user must be signed
     * in (i.e. user.email is available). The link is delivered by
     * Firebase; we return success.
     */
    suspend fun sendEmailVerification(user: FirebaseUser) {
        if (user.email.isNullOrBlank()) {
            throw IllegalArgumentException("user has no email; cannot verify")
        }
        user.sendEmailVerification().await()
    }

    /**
     * Sign out from Firebase.
     */
    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }

    /**
     * Look up the user's profile in our `users` table by their
     * Firebase UID, returning the rows we can mirror to SessionCache.
     * Returns null if the backend can't be reached or the user
     * doesn't have a row yet.
     */
    suspend fun mirrorProfileToSession(
        hjwt: String,
        baseUrl: String,
    ): Boolean {
        return try {
            val url = baseUrl.trimEnd('/') + "/api/v1/auth/firebase/me"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $hjwt")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            if (code !in 200..299) return false
            val body = conn.inputStream.bufferedReader().readText()
            val u = org.json.JSONObject(body)
            val id = u.optString("id")
            if (id.isBlank()) return false
            SessionCache.set(
                role = com.scottsx.app.data.domain.Role.valueOf(
                    u.optString("role", "BUYER").uppercase()
                ),
                displayName = u.optString("displayName").ifBlank { u.optString("email") },
                email = u.optString("email"),
                userId = id,
                firebaseUid = u.optString("firebaseUid"),
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "mirrorProfileToSession failed: ${t.message}")
            false
        }
    }

    /**
     * Push a product to Firestore /products/{id}. Best-effort; on
     * failure we log and continue (the rest of the app still works
     * from the in-memory cache).
     */
    suspend fun mirrorProduct(
        productId: String,
        title: String,
        description: String?,
        priceMinor: Long?,
        currency: String?,
        category: String?,
        sellerId: String,
        imageUrl: String?,
    ) {
        try {
            val data = hashMapOf(
                "id" to productId,
                "title" to title,
                "description" to (description ?: ""),
                "priceMinor" to (priceMinor ?: 0L),
                "currency" to (currency ?: "UGX"),
                "category" to (category ?: ""),
                "sellerId" to sellerId,
                "imageUrl" to (imageUrl ?: ""),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            )
            FirebaseFirestore.getInstance().collection("products")
                .document(productId)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (t: Throwable) {
            Log.w(TAG, "mirrorProduct failed: ${t.message}")
        }
    }
}
