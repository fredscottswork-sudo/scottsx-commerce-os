package com.scottsx.app.data

import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.SessionCache

/**
 * Single helper for session state + ending a session — used by every
 * network client (V2Client, RemoteAssistantClient, MessageStream) to
 * attach the user's bearer token, and by every "Sign Out" entry point
 * in the app (Buyer settings, Seller settings, Profile nav, automatic
 * sign-out on role mismatch, etc.).
 *
 * THE AUTH PIPELINE (why these accessors have multiple sources):
 *  - Fresh sign-in: the Google exchange saves the backend JWT +
 *    profile into the root `com.scottsx.app.SessionCache` and mirrors
 *    them here via [adoptSession] (jwt + role cache).
 *  - Cold start with a surviving Firebase credential: MainActivity
 *    re-exchanges the Firebase ID token via POST
 *    /api/v1/auth/firebase/sign-in and calls [adoptSession] again.
 *  - Reads: network clients call [tokenOrNull] / [userIdOrNull], which
 *    fall back to the root cache so no caller can ever "forget" to
 *    bridge the two stores — authenticated calls ALWAYS carry the
 *    Authorization header once the user is signed in.
 *
 * Wrapped internally so callers don't need to know which subsystems
 * need cleaning. If [googleHelper] is null (e.g. the user never
 * signed in with Google) the Google clear is silently skipped.
 */
object Session {
    // Bearer token for the Fastify REST API.
    @Volatile private var jwt: String? = null
    fun setToken(t: String?) { jwt = t }

    /** Bearer token, preferring the in-memory mirror but falling back to
     * the root session store (populated by the Google sign-in exchange). */
    fun tokenOrNull(): String? = jwt ?: com.scottsx.app.SessionCache.authToken()

    /**
     * Adopt a freshly-minted backend session: stores the JWT here AND
     * writes the profile into the role/display cache used by UI +
     * AI-tooling, so both session stores stay in lock-step.
     */
    fun adoptSession(
        token: String,
        userId: String?,
        role: Role?,
        displayName: String?,
        email: String?,
        storeName: String? = null,
        storeLocation: String? = null,
        avatarUrl: String? = null,
    ) {
        jwt = token
        if (userId != null || role != null) {
            SessionCache.set(
                role = role ?: Role.BUYER,
                displayName = displayName,
                email = email,
                userId = userId,
                storeName = storeName,
                storeLocation = storeLocation,
                avatarUrl = avatarUrl,
            )
        }
    }

    suspend fun signOut(
        authRepository: AuthRepository,
        googleHelper: GoogleSignInHelper? = null,
    ) {
        // 1. Firebase Auth — clears the active credential.
        runCatching { authRepository.signOut() }
        // 2. Google sign-in SDK — clears the cached id_token so
        //    the next "Sign in with Google" tap forces the picker
        //    instead of silently resuming the previous account.
        googleHelper?.let { runCatching { it.signOut() } }
        // 3. Every local session store: role cache, backend profile
        //    store, and the bearer token mirror.
        SessionCache.clear()
        runCatching { com.scottsx.app.SessionCache.clear() }
        jwt = null
    }

    // Read accessors used by the secure AI tool layer + chat UI.
    // Backend UUID (as embedded in the JWT) — falls back to the root
    // cache so chat bubbles always resolve "mine vs theirs" correctly.
    fun userIdOrNull(): String? =
        SessionCache.userIdOrNull() ?: com.scottsx.app.SessionCache.user.value?.id

    fun roleOrNull(): Role? =
        SessionCache.roleOrNull() ?: when (com.scottsx.app.SessionCache.user.value?.role) {
            "seller" -> Role.SELLER
            "buyer" -> Role.BUYER
            else -> null
        }

    fun displayNameOrEmpty(): String =
        SessionCache.displayNameOrEmpty().takeIf { it.isNotBlank() && it != "Buyer" && it != "Seller" }
            ?: com.scottsx.app.SessionCache.user.value?.displayName
            ?: SessionCache.displayNameOrEmpty()

    fun storeNameOrEmpty(): String = SessionCache.storeNameOrEmpty()
    fun locationOrEmpty(): String = SessionCache.locationOrEmpty()
    fun emailOrEmpty(): String =
        SessionCache.email.takeIf { !it.isNullOrBlank() }
            ?: com.scottsx.app.SessionCache.user.value?.email
            ?: ""
}
