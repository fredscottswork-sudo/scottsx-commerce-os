package com.scottsx.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.SessionCache
import kotlinx.coroutines.tasks.await

/**
 * Auth repository — thin wrapper around Firebase Auth + Firestore.
 *
 * Storage strategy
 * ----------------
 *  * Authentication: Firebase Authentication (email + password).
 *  * User profile (display name, phone, role, business fields): a
 *    document at `/users/{uid}` in Firestore.
 *  * Role: stored on the Firestore profile AND mirrored locally so
 *    the UI can show "Logging in as Buyer / Seller" without an
 *    extra round-trip.
 *
 * Stage-1 scope
 * -------------
 *  * signUp / signIn with email + password
 *  * persist role + seller fields to Firestore
 *  * read the role back from Firestore on each sign-in
 *
 * Stage-2 will move role into a Firebase custom claim via a Cloud
 * Function so the JWT carries it server-side.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /**
     * THE critical bridge: AFTER Firebase Auth succeeds, exchange the
     * Firebase ID token for a backend JWT via
     * POST /api/v1/auth/firebase/sign-in and store it in BOTH session
     * caches (root + network mirror). Without this, every authenticated
     * /api/v1 call sent NO Authorization header and silently 401'd —
     * which presented to users as "dashboard shows nothing", "messages
     * don't send", "notifications never arrive".
     *
     * Best-effort: a failed exchange must NOT block the user from
     * reaching Home — a later retry (cold start or next sign-in) fixes
     * the session. But it is retried eagerly on every fresh sign-in.
     */
    private suspend fun syncBackendSession(
        user: FirebaseUser,
        role: Role,
        displayName: String?,
        phone: String? = null,
        storeName: String? = null,
    ) {
        runCatching {
            val firebaseToken = user.getIdToken(false).await().token ?: return@runCatching
            val result = com.scottsx.app.data.remote.V2Client.signInWithFirebase(
                idToken = firebaseToken,
                displayName = displayName,
                phone = phone,
                role = role.name.lowercase(),
                storeName = storeName,
            ) ?: return@runCatching
            // 1. Root session store (user profile + token).
            com.scottsx.app.SessionCache.save(
                result.token,
                com.scottsx.app.CurrentUser(
                    id = result.user.id,
                    email = result.user.email,
                    displayName = result.user.displayName.ifBlank { displayName ?: "ScottsTechX user" },
                    phone = result.user.phone.ifBlank { phone ?: "" },
                    role = result.user.role,
                    emailVerified = result.user.emailVerified,
                    profilePhotoUrl = result.user.profilePhotoUrl,
                    city = result.user.city,
                ),
                announce = true,
            )
            // 2. Network-session mirror (Bearer token + role/userId).
            com.scottsx.app.data.Session.adoptSession(
                token = result.token,
                userId = result.user.id,
                role = if (result.user.role.equals("seller", true)) Role.SELLER else role,
                displayName = result.user.displayName.ifBlank { displayName },
                email = result.user.email,
                avatarUrl = result.user.profilePhotoUrl,
                storeLocation = result.user.city,
            )
        }.onFailure {
            android.util.Log.w("AuthRepository", "Backend session exchange failed (offline?); will retry on next launch.", it)
        }
    }

    suspend fun signIn(email: String, password: String, expectedRole: Role? = null): AuthResult {
        val res = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = res.user ?: return AuthResult.Failure("Sign-in failed")
        // Best-effort role lookup. Offline -> fall back to Buyer.
        val actualRole = runCatching { fetchRole(user.uid) }
            .onFailure {
                android.util.Log.w(
                    "AuthRepository",
                    "Firestore role lookup failed during sign-in (offline?).",
                    it,
                )
            }
            .getOrNull() ?: Role.BUYER
        // Enforce role separation: if the caller asked for a specific role
        // (they tapped "Login as Buyer" or "Login as Seller" at the role
        // selector) but the server says otherwise, hand back a RoleMismatch
        // so the UI can route them to the right dashboard.
        if (expectedRole != null && actualRole != expectedRole) {
            return AuthResult.RoleMismatch(actual = actualRole)
        }
        // Block unverified sign-ins. Bounce them to the pending screen so
        // they can tap the link and try again. Re-fetch the flag in case
        // it changed since the cached value.
        runCatching { user.reload() }
        if (!user.isEmailVerified) {
            // Try to resend in case the original email was lost.
            runCatching { user.sendEmailVerification().await() }
            return AuthResult.VerificationPending(email = email.trim())
        }
        SessionCache.set(actualRole, user.displayName, user.email)
        syncBackendSession(user, actualRole, user.displayName)
        return AuthResult.Success(actualRole)
    }

    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        phone: String,
        role: Role,
        sellerExtras: SellerExtras? = null,
    ): AuthResult {
        // First check whether the email is already taken on a different role.
        // FirebaseAuth's createUserWithEmailAndPassword will throw if the
        // account already exists, but that error message is opaque. We look
        // up any existing profile by email first so we can return a clean
        // RoleMismatch with a usable message rather than "email already in use".
        val existingRoleForEmail = runCatching { fetchRoleByEmail(email.trim()) }
            .getOrNull()
        if (existingRoleForEmail != null && existingRoleForEmail != role) {
            return AuthResult.RoleMismatch(actual = existingRoleForEmail)
        }
        val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = res.user ?: return AuthResult.Failure("Sign-up failed")
        // Set display name.
        val profile = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(profile).await()

        // Persist profile to Firestore.
        val profileMap = buildMap {
            put("uid", user.uid)
            put("displayName", displayName)
            put("email", email.trim())
            put("phone", phone.trim())
            put("role", role.name.lowercase())
            put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
            if (role == Role.SELLER && sellerExtras != null) {
                put("seller", mapOf(
                    "businessName" to sellerExtras.businessName,
                    "businessType" to sellerExtras.businessType,
                    "storeLocation" to sellerExtras.storeLocation,
                    "nin" to sellerExtras.nin,
                    "yearsInBusiness" to sellerExtras.yearsInBusiness.toIntOrNull(),
                    "bio" to sellerExtras.bio,
                ))
            }
        }
        runCatching {
            db.collection("users").document(user.uid).set(profileMap, SetOptions.merge()).await()
        }.onFailure {
            android.util.Log.w(
                "AuthRepository",
                "Firestore sign-up profile write failed; will retry on next sign-in.",
                it,
            )
        }
        SessionCache.set(role, user.displayName, user.email)
        syncBackendSession(
            user,
            role,
            user.displayName ?: displayName,
            phone = phone.trim().takeIf { it.isNotBlank() },
            storeName = sellerExtras?.businessName?.takeIf { it.isNotBlank() },
        )
        // Always send the Firebase verification email. The user is NOT
        // allowed to enter the app until they tap the link. This avoids
        // the "I created an account but my email was never checked" trap.
        runCatching { user.sendEmailVerification().await() }
            .onFailure {
                android.util.Log.w(
                    "AuthRepository",
                    "Failed to send Firebase verification email (continuing to pending screen anyway)",
                    it,
                )
            }
        // If the user is already verified (e.g. auto-verified by an IdP),
        // route them straight through. Otherwise hand off to the pending
        // screen where they tap the link and confirm.
        return if (user.isEmailVerified) {
            AuthResult.Success(role)
        } else {
            AuthResult.VerificationPending(email = email.trim())
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    /**
     * Sign in (or register) with a Google ID token.
     *
     * The Android client obtains the `idToken` via the Google Sign-In
     * SDK and passes it in. Firebase exchanges it for our app's
     * credential; if the user does not yet have a Firestore profile
     * we create one with the supplied [role] and Google-derived
     * display name / email.
     */
    suspend fun signInWithGoogle(idToken: String, expectedRole: Role): AuthResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val res = auth.signInWithCredential(credential).await()
        val user = res.user ?: return AuthResult.Failure("Google sign-in failed")

        // Best-effort profile sync. Firestore may be unreachable
        // (offline, throttled, denied rules) — none of these
        // should block the user from reaching Home.
        val existingRole = runCatching { fetchRole(user.uid) }
            .onFailure {
                android.util.Log.w(
                    "AuthRepository",
                    "Firestore role lookup failed (offline?); using caller-supplied role.",
                    it,
                )
            }
            .getOrNull()

        if (existingRole != null) {
            SessionCache.set(existingRole, user.displayName, user.email)
            syncBackendSession(user, existingRole, user.displayName)
            // Server-side role exists. Enforce separation — never let a
            // user pick a different role at the role selector and walk
            // through to the wrong dashboard. If the Firestore role
            // disagrees with what the user just tapped, bounce them to
            // the RoleMismatch screen.
            if (existingRole != expectedRole) {
                return AuthResult.RoleMismatch(actual = existingRole)
            }
            return AuthResult.Success(existingRole)
        }

        // First-time Google sign-in: create the profile document.
        // If Firestore is unreachable we still let the user in.
        val displayName = user.displayName ?: "Google User"
        val email = user.email ?: ""
        val profileMap = buildMap {
            put("uid", user.uid)
            put("displayName", displayName)
            put("email", email)
            put("phone", "")
            put("role", expectedRole.name.lowercase())
            put("signInProvider", "google")
            put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
        }
        val profileWrite = runCatching {
            db.collection("users").document(user.uid)
                .set(profileMap, SetOptions.merge()).await()
        }.onFailure {
            android.util.Log.w(
                "AuthRepository",
                "Firestore profile write failed; profile will be retried on next sign-in.",
                it,
            )
        }
        SessionCache.set(expectedRole, displayName, email)
        syncBackendSession(user, expectedRole, displayName)
        // Note: we still return Success even if profileWrite failed.
        // The user is authenticated with Firebase Auth; the profile
        // document can be retried later. Blocking sign-in here
        // would be worse UX than the transient offline state.
        return AuthResult.Success(expectedRole)
    }

    suspend fun fetchRole(uid: String): Role? {
        val snap = db.collection("users").document(uid).get().await()
        if (!snap.exists()) return null
        val raw = snap.getString("role") ?: return null
        return if (raw.equals("seller", ignoreCase = true)) Role.SELLER else Role.BUYER
    }

    /**
     * Look up the role of a profile by email rather than uid. Used during
     * sign-up to detect "this email is already a seller / buyer" before
     * the Firebase Auth createUserWithEmailAndPassword throws.
     *
     * NOTE: this requires a Firestore `where` query against an indexed
     * `email` field on the users collection. If the index is missing this
     * will throw, which the caller wraps in runCatching.
     */
    suspend fun fetchRoleByEmail(email: String): Role? {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return null
        val snap = db.collection("users")
            .whereEqualTo("email", normalized)
            .limit(1)
            .get()
            .await()
        if (snap.isEmpty) return null
        val raw = snap.documents.firstOrNull()?.getString("role") ?: return null
        return if (raw.equals("seller", ignoreCase = true)) Role.SELLER else Role.BUYER
    }

}

/**
 * Seller-only fields collected on sign-up. Kept separate from common
 * fields so the Buyer flow can ignore it.
 */
data class SellerExtras(
    val businessName: String,
    val businessType: String,
    val storeLocation: String,
    val nin: String,
    val yearsInBusiness: String,
    val bio: String,
)

sealed class AuthResult {
    data class Success(val role: Role) : AuthResult()
    data class Failure(val message: String) : AuthResult()

    /**
     * Account was created in Firebase Auth, a verification email was sent,
     * but the user has not yet confirmed the link. The UI must navigate
     * to [com.scottsx.app.ui.screens.VerifyEmailPendingScreen] so the
     * user can tap the link, then return and tap "I've verified —
     * continue" to call Firebase's `reload()` and recheck the flag.
     *
     * @param email The address we sent the verification link to.
     */
    data class VerificationPending(val email: String) : AuthResult()

    /**
     * The user is authenticated but the role on the server does not match
     * the role the user picked at the role-selection screen. For example,
     * they tapped "Sign in as Buyer" but their Firestore profile says
     * `role = "seller"`. The Compose layer should route the user to a
     * Wrong-Role screen with the actual server role so they can pick the
     * right dashboard.
     */
    data class RoleMismatch(val actual: Role) : AuthResult()
}

/**
 * Lightweight in-memory cache for the current user's role, so the UI
 * can display "Logging in as Buyer / Seller" without an extra
 * Firestore round-trip.
 */
object SessionCache {
    @Volatile var role: Role? = null
}