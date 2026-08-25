package com.scottsx.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the signed-in user + bearer token.
 * Every network call in V2Client reads its token from here.
 */
data class CurrentUser(
    val id: String,
    val email: String,
    val displayName: String,
    val phone: String = "",
    val role: String = "buyer",
    val emailVerified: Boolean = false,
    val profilePhotoUrl: String? = null,
    val city: String = "",
)

object SessionCache {
    @Volatile
    private var token: String? = null

    private val _user = MutableStateFlow<CurrentUser?>(null)
    val user: StateFlow<CurrentUser?> = _user

    /**
     * Invoked whenever a session begins or ends.
     *
     * Set once by the Application class so push-token registration happens on
     * EVERY sign-in path (password, Firebase, Google, seller upgrade) without
     * each screen having to remember to call it.
     */
    @Volatile
    var onSessionChanged: ((signedIn: Boolean) -> Unit)? = null

    fun save(token: String, user: CurrentUser) {
        this.token = token
        _user.value = user
        onSessionChanged?.invoke(true)
    }

    fun updateUser(user: CurrentUser) {
        _user.value = user
    }

    fun clear() {
        onSessionChanged?.invoke(false)
        token = null
        _user.value = null
    }

    fun authToken(): String? = token
    fun isLoggedIn(): Boolean = !token.isNullOrBlank()
    fun isSeller(): Boolean = _user.value?.role == "seller"

    /** Restores a previous session from prefs (token kept out of prefs — dev only). */
    fun restore(user: CurrentUser) {
        _user.value = user
    }
}
