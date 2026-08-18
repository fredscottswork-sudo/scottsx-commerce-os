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

    fun save(token: String, user: CurrentUser) {
        this.token = token
        _user.value = user
    }

    fun updateUser(user: CurrentUser) {
        _user.value = user
    }

    fun clear() {
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
