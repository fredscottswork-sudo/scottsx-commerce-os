package com.scottsx.app.data.remote

import com.scottsx.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stage 5 — REST client for the Firebase-backed backend.
 *
 * Talks to /api/v1/{auth/firebase, ai/v2, settings/v2, memory/v2,
 * chat/v2, products/v2, sellers/v2} using the HS256 JWT from
 * [Session.token] as a Bearer token. All calls are best-effort
 * and return null/empty on failure so the UI never crashes.
 */
object V2Client {

    private const val TAG = "V2Client"

    // Default backend base URL — override at runtime via setBaseUrl().
    // Real device + adb reverse tcp:3001 tcp:3001 → reaches the host backend.
    // For Android emulator without adb reverse, use 10.0.2.2 instead.
    private const val DEFAULT_BASE_URL = "http://127.0.0.1:3001"
    @Volatile private var baseUrlOverride: String? = null
    fun setBaseUrl(url: String) { baseUrlOverride = url }

    /** Base URL — same as the existing RemoteAssistantClient. */
    private val baseUrl: String get() = DEFAULT_BASE_URL

    private suspend fun <T> apiCall(
        method: String,
        path: String,
        body: JSONObject? = null,
        parse: (JSONObject) -> T,
    ): T? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(baseUrl.trimEnd('/') + path)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 6000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/json")
            Session.tokenOrNull()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                android.util.Log.w(TAG, "$method $path -> $code")
                return@withContext null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.isBlank()) null else parse(JSONObject(text))
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "$method $path failed: ${t.message}")
            null
        }
    }

    private suspend fun <T> apiCallArray(
        method: String,
        path: String,
        body: JSONObject? = null,
        parse: (JSONArray) -> T,
    ): T? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(baseUrl.trimEnd('/') + path)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 6000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/json")
            Session.tokenOrNull()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                android.util.Log.w(TAG, "$method $path -> $code")
                return@withContext null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.isBlank()) null else parse(JSONArray(text))
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "$method $path failed: ${t.message}")
            null
        }
    }

    // ----------------------------------------------------------------
    // Auth helpers
    // ----------------------------------------------------------------

    /**
     * Promote the current buyer to a seller. The backend requires a
     * verified email — if the caller hasn't verified yet, the
     * server returns 403 email_not_verified. The caller can recover
     * by triggering the email verification flow and retrying.
     */
    suspend fun upgradeToSeller(): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/auth/firebase/upgrade-to-seller",
            body = JSONObject(),
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    // ============================================================
    // GOOGLE SIGN-IN
    // ============================================================

    /** The `user` half of POST /api/v1/auth/google (see backend publicUser()). */
    data class GoogleUser(
        val id: String,
        val email: String,
        val displayName: String,
        val phone: String,
        val role: String,
        val emailVerified: Boolean,
        val profilePhotoUrl: String?,
        val city: String,
    )

    data class GoogleSignInResult(
        val token: String,
        val user: GoogleUser,
    )

    /**
     * Exchange a Google ID token (Credential Manager) for a ScottsTechX
     * session. The server verifies the token against Google's JWKS —
     * signature, issuer and audience — so the client just forwards it.
     * Google accounts arrive email-verified and skip the verification gate.
     */
    suspend fun signInWithGoogle(idToken: String): GoogleSignInResult? =
        apiCall<GoogleSignInResult?>(
            method = "POST",
            path = "/api/v1/auth/google",
            body = JSONObject().put("idToken", idToken),
            parse = { o ->
                val token = o.optString("token")
                val u = o.optJSONObject("user")
                if (token.isBlank() || u == null) {
                    null
                } else {
                    GoogleSignInResult(
                        token = token,
                        user = GoogleUser(
                            id = u.optString("id"),
                            email = u.optString("email"),
                            displayName = u.optString("displayName"),
                            phone = u.optString("phone"),
                            role = u.optString("role", "buyer"),
                            emailVerified = u.optBoolean("emailVerified", false),
                            // org.json can yield the literal "null" for a JSON
                            // null — guard both that and the empty string.
                            profilePhotoUrl = u.optString("profilePhotoUrl")
                                .takeIf { it.isNotBlank() && it != "null" },
                            city = u.optString("city"),
                        ),
                    )
                }
            },
        )

    /**
     * Register this device's FCM token so order/message notifications can be
     * pushed to it. POST /api/v1/me/devices — upserts server-side, so a
     * refreshed token simply overwrites the old row.
     */
    suspend fun registerDevice(token: String, platform: String = "android"): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/me/devices",
            body = JSONObject().put("token", token).put("platform", platform),
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    /**
     * Change the password on a local (bcrypt) account. The backend rejects a
     * wrong current password with 401 — that surfaces here as `false`, which
     * the UI renders as "check your current password".
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/me/change-password",
            body = JSONObject()
                .put("oldPassword", oldPassword)
                .put("newPassword", newPassword),
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    // ============================================================
    // PRODUCTS
    // ============================================================

    /**
     * Fetch the full product catalogue (public endpoint, no auth
     * required). The backend returns `{ products: [...], total, ... }` —
     * a JSON **object** — so this reads the `products` array out of it.
     * (This method once parsed the body as a bare array, so the home
     * feed was silently empty against a perfectly healthy backend.)
     */
    /**
     * Same feed as [fetchProductsList] but nullable: null means the
     * request failed (network, non-2xx or an unexpected envelope) so
     * the UI can show its error state; an EMPTY list means the server
     * genuinely has no products. Never conflate the two — an error
     * must not masquerade as an empty marketplace.
     */
    suspend fun fetchProductsListOrNull(): List<com.scottsx.app.data.domain.Product>? {
        val obj = apiCall(
            method = "GET",
            path = "/api/v1/products?pageSize=50",
            body = null,
            parse = { it },
        ) ?: return null
        val arr = obj.optJSONArray("products") ?: return null
        val out = ArrayList<com.scottsx.app.data.domain.Product>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            out += jsonToProduct(row)
        }
        return out
    }

    suspend fun fetchProductsList(): List<com.scottsx.app.data.domain.Product> =
        fetchProductsListOrNull() ?: emptyList()

    /**
     * Decode one backend product row (see
     * 12_Backend/src/modules/products/products.service.ts):
     *   id, title, description, category, brand, priceMinor,
     *   oldPriceMinor, stockQuantity, imageUrl, mediaUrls[], rating,
     *   ratingCount, isFlashDeal, discountPercent, location, status,
     *   seller: { id, name, rating, location, verified }
     *
     * Every value is read from the response — nothing is fabricated.
     * Missing optional fields fall back to neutral defaults.
     */
    private fun jsonToProduct(o: org.json.JSONObject): com.scottsx.app.data.domain.Product {
        val title = o.optString("title")
        val description = o.optString("description")
        val category = com.scottsx.app.data.domain.ProductCategory.fromApiName(o.optString("category"))
            ?: com.scottsx.app.data.domain.ProductCategory.All
        val priceUgx = o.optLong("priceMinor", 0L)
        val oldPriceRaw = o.optLong("oldPriceMinor", 0L)
        val oldPriceUgx = if (oldPriceRaw > 0L && oldPriceRaw > priceUgx) oldPriceRaw else null
        val imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() } ?: ""
        // Real seller object from the backend — never hardcoded.
        val sellerJson = o.optJSONObject("seller")
        val seller = if (sellerJson != null) {
            com.scottsx.app.data.domain.Seller(
                id = sellerJson.optString("id"),
                name = sellerJson.optString("name").ifEmpty { title },
                rating = sellerJson.optDouble("rating", 0.0).toFloat(),
                location = sellerJson.optString("location").ifEmpty { o.optString("location") },
                verified = sellerJson.optBoolean("verified", false),
            )
        } else {
            com.scottsx.app.data.domain.Seller(
                id = o.optString("id"),
                name = "ScottsTechX Seller",
                rating = 0f,
                location = o.optString("location").ifEmpty { "Uganda" },
                verified = false,
            )
        }
        val brandName = o.optString("brand").ifEmpty { "Unbranded" }
        val brand = com.scottsx.app.data.domain.Brand(
            id = brandName.lowercase().replace(' ', '-'),
            name = brandName,
        )
        return com.scottsx.app.data.domain.Product(
            id = o.optString("id"),
            name = title,
            shortDescription = description.take(80),
            description = description,
            priceUgx = priceUgx,
            oldPriceUgx = oldPriceUgx,
            category = category,
            brand = brand,
            seller = seller,
            imageUrl = imageUrl,
            stock = o.optInt("stockQuantity", 1),
            rating = o.optDouble("rating", 0.0).toFloat(),
            ratingCount = o.optInt("ratingCount", 0),
            isFlashDeal = o.optBoolean("isFlashDeal", false),
            discountPercent = o.optInt("discountPercent", 0),
            location = o.optString("location").ifEmpty { "Kampala" },
        )
    }

    // ============================================================
    // USER PROFILE / ADDRESSES / PAYMENT METHODS / ETC.
    // ============================================================

    suspend fun fetchUserProfile(): JSONObject? = apiCall(
        method = "GET", path = "/api/v1/user/profile", body = null,
        parse = { o -> o },
    )

    suspend fun updateUserProfile(patch: JSONObject): Boolean = apiCall(
        method = "PATCH", path = "/api/v1/user/profile", body = patch,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    suspend fun updateAvatar(avatarUrl: String): Boolean = apiCall(
        method = "POST",
        path = "/api/v1/user/profile/avatar",
        body = JSONObject().put("avatarUrl", avatarUrl),
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    // ============================================================
    // SELLER PROFILE & STORE SETTINGS
    // ============================================================

    /**
     * GET /api/v1/seller/profile — returns the current seller's full
     * seller profile (business name, address, etc.). Returns null on
     * 404 (seller not yet on-boarded) or any non-2xx.
     */
    suspend fun fetchSellerProfile(): org.json.JSONObject? = apiCall(
        method = "GET", path = "/api/v1/seller/profile", body = null,
        parse = { it },
    )

    /**
     * PATCH /api/v1/seller/profile — incremental update of the
     * seller's business profile. The [patch] object's keys are mapped
     * 1:1 to the backend schema.
     */
    suspend fun updateSellerProfile(patch: JSONObject): Boolean = apiCall(
        method = "PATCH", path = "/api/v1/seller/profile", body = patch,
        parse = { o -> o.optBoolean("ok", true) },
    ) ?: false

    /**
     * GET /api/v1/seller/store-settings — current store branding
     * (storeName, logoUrl, bannerUrl, opening hours, social links).
     * Returns null if the seller has no settings yet.
     */
    suspend fun fetchStoreSettings(): org.json.JSONObject? = apiCall(
        method = "GET", path = "/api/v1/seller/store-settings", body = null,
        parse = { it },
    )

    /**
     * PATCH /api/v1/seller/store-settings — store branding update.
     * Supports logoUrl, bannerUrl, storeName, storeDescription, social
     * links, opening hours JSON, etc.
     */
    suspend fun updateStoreSettings(patch: JSONObject): Boolean = apiCall(
        method = "PATCH", path = "/api/v1/seller/store-settings", body = patch,
        parse = { o -> o.optBoolean("ok", true) },
    ) ?: false

    // Addresses
    data class Address(
        val id: String, val label: String, val recipient: String, val phone: String?,
        val line1: String, val line2: String?, val city: String, val region: String?,
        val country: String, val postalCode: String?, val isDefault: Boolean,
    )

    suspend fun fetchAddresses(): List<Address> {
        val arr = apiCallArray(method = "GET", path = "/api/v1/user/addresses", body = null, parse = { it }) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            Address(
                id = a.optString("id"),
                label = a.optString("label"),
                recipient = a.optString("recipient"),
                phone = a.optString("phone").takeIf { it.isNotBlank() },
                line1 = a.optString("line1"),
                line2 = a.optString("line2").takeIf { it.isNotBlank() },
                city = a.optString("city"),
                region = a.optString("region").takeIf { it.isNotBlank() },
                country = a.optString("country"),
                postalCode = a.optString("postalCode").takeIf { it.isNotBlank() },
                isDefault = a.optBoolean("isDefault", false),
            )
        }
    }

    suspend fun createAddress(body: JSONObject): String? = apiCall(
        method = "POST", path = "/api/v1/user/addresses", body = body,
        parse = { o -> o.optString("id") },
    )

    suspend fun updateAddress(id: String, body: JSONObject): Boolean = apiCall(
        method = "PATCH", path = "/api/v1/user/addresses/$id", body = body,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    suspend fun deleteAddress(id: String): Boolean = apiCall(
        method = "DELETE", path = "/api/v1/user/addresses/$id", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    // Payment methods
    data class PaymentMethod(
        val id: String, val kind: String, val provider: String?, val label: String,
        val account: String, val isDefault: Boolean, val expiresAt: String?,
    )

    suspend fun fetchPaymentMethods(): List<PaymentMethod> {
        val arr = apiCallArray(method = "GET", path = "/api/v1/user/payment-methods", body = null, parse = { it }) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            PaymentMethod(
                id = p.optString("id"),
                kind = p.optString("kind"),
                provider = p.optString("provider").takeIf { it.isNotBlank() },
                label = p.optString("label"),
                account = p.optString("account"),
                isDefault = p.optBoolean("isDefault", false),
                expiresAt = p.optString("expiresAt").takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun createPaymentMethod(body: JSONObject): String? = apiCall(
        method = "POST", path = "/api/v1/user/payment-methods", body = body,
        parse = { o -> o.optString("id") },
    )

    suspend fun deletePaymentMethod(id: String): Boolean = apiCall(
        method = "DELETE", path = "/api/v1/user/payment-methods/$id", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    // Saved products
    suspend fun fetchSavedProducts(): JSONArray? = apiCallArray(
        method = "GET", path = "/api/v1/user/saved-products", body = null,
        parse = { it },
    )

    suspend fun saveProduct(productId: String): Boolean = apiCall(
        method = "POST", path = "/api/v1/user/saved-products/$productId", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    suspend fun unsaveProduct(productId: String): Boolean = apiCall(
        method = "DELETE", path = "/api/v1/user/saved-products/$productId", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    // Saved sellers
    suspend fun fetchSavedSellers(): JSONArray? = apiCallArray(
        method = "GET", path = "/api/v1/user/saved-sellers", body = null,
        parse = { it },
    )

    suspend fun saveSeller(sellerId: String): Boolean = apiCall(
        method = "POST", path = "/api/v1/user/saved-sellers/$sellerId", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    suspend fun unsaveSeller(sellerId: String): Boolean = apiCall(
        method = "DELETE", path = "/api/v1/user/saved-sellers/$sellerId", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    // Refunds
    suspend fun fetchRefunds(): JSONArray? = apiCallArray(
        method = "GET", path = "/api/v1/user/refunds", body = null,
        parse = { it },
    )

    suspend fun createRefund(body: JSONObject): String? = apiCall(
        method = "POST", path = "/api/v1/user/refunds", body = body,
        parse = { o -> o.optString("id") },
    )

    // Returns
    suspend fun fetchReturns(): JSONArray? = apiCallArray(
        method = "GET", path = "/api/v1/user/returns", body = null,
        parse = { it },
    )

    suspend fun createReturn(body: JSONObject): String? = apiCall(
        method = "POST", path = "/api/v1/user/returns", body = body,
        parse = { o -> o.optString("id") },
    )

    // Support tickets
    suspend fun fetchTickets(): JSONArray? = apiCallArray(
        method = "GET", path = "/api/v1/support/tickets", body = null,
        parse = { it },
    )

    suspend fun createTicket(category: String, subject: String, message: String, attachmentUrl: String? = null): String? = apiCall(
        method = "POST", path = "/api/v1/support/tickets",
        body = JSONObject().apply {
            put("category", category)
            put("subject", subject)
            put("message", message)
            if (attachmentUrl != null) put("attachmentUrl", attachmentUrl)
        },
        parse = { o -> o.optString("id") },
    )

    // CMS
    suspend fun fetchCms(slug: String, locale: String = "en"): JSONObject? = apiCall(
        method = "GET", path = "/api/v1/cms/$slug?locale=$locale", body = null,
        parse = { o -> o },
    )

    // Reports
    suspend fun createReport(
        resourceType: String, resourceId: String, reason: String,
        description: String? = null,
    ): String? = apiCall(
        method = "POST", path = "/api/v1/reports",
        body = JSONObject().apply {
            put("resourceType", resourceType)
            put("resourceId", resourceId)
            put("reason", reason)
            if (description != null) put("description", description)
        },
        parse = { o -> o.optString("id") },
    )

    // Notifications (user-specific)
    suspend fun fetchNotifications(): JSONArray? = apiCallArray(
        method = "GET", path = "/api/v1/user/notifications", body = null,
        parse = { it },
    )

    suspend fun markAllNotificationsRead(): Boolean = apiCall(
        method = "POST", path = "/api/v1/user/notifications/mark-all-read", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    suspend fun markNotificationRead(id: String): Boolean = apiCall(
        method = "POST", path = "/api/v1/user/notifications/$id/read", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    // Audit log
    suspend fun fetchMyAudit(): JSONArray? = apiCallArray(
        method = "GET", path = "/api/v1/audit/me", body = null,
        parse = { it },
    )


    // ----------------------------------------------------------------
    // AI
    // ----------------------------------------------------------------

    data class AiReply(val text: String, val provider: String)

    suspend fun ask(message: String, screen: String? = null): AiReply? =
        apiCall(
            method = "POST",
            path = "/api/v1/ai/v2/ask",
            body = JSONObject().apply {
                put("message", message)
                if (screen != null) put("context", JSONObject().put("screen", screen))
            },
            parse = { o ->
                AiReply(
                    text = o.optString("reply"),
                    provider = o.optJSONObject("sources")?.optString("aiProvider") ?: "",
                )
            },
        )

    // ----------------------------------------------------------------
    // Memory signals — fire and forget from anywhere
    // ----------------------------------------------------------------

    suspend fun recordSignal(kind: String, value: String) {
        try {
            apiCall(
                method = "POST",
                path = "/api/v1/memory/v2/ai/signal",
                body = JSONObject().apply {
                    put("kind", kind)
                    put("value", value)
                },
                parse = { o -> o.optBoolean("ok", false) },
            )
        } catch (_: Throwable) { }
    }

    suspend fun clearAiMemory(): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/memory/v2/ai/clear",
            body = null,
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    // ----------------------------------------------------------------
    // Settings
    // ----------------------------------------------------------------

    data class Settings(
        val theme: String,
        val language: String,
        val notificationsEnabled: Boolean,
        val notificationSound: Boolean,
        val locationSharing: String,
        val privacyShowReceipts: Boolean,
        val privacyShowTransactions: Boolean,
        val aiPersonalizationEnabled: Boolean,
        val preferredLanguage: String,
        val preferredCurrency: String,
    )

    suspend fun loadSettings(): Settings? =
        apiCall(
            method = "GET",
            path = "/api/v1/settings/v2",
            body = null,
            parse = { o ->
                Settings(
                    theme = o.optString("theme", "system"),
                    language = o.optString("language", "en"),
                    notificationsEnabled = o.optBoolean("notificationsEnabled", true),
                    notificationSound = o.optBoolean("notificationSound", true),
                    locationSharing = o.optString("locationSharing", "approximate"),
                    privacyShowReceipts = o.optBoolean("privacyShowReceipts", true),
                    privacyShowTransactions = o.optBoolean("privacyShowTransactions", true),
                    aiPersonalizationEnabled = o.optBoolean("aiPersonalizationEnabled", true),
                    preferredLanguage = o.optString("preferredLanguage", "en"),
                    preferredCurrency = o.optString("preferredCurrency", "UGX"),
                )
            },
        )

    suspend fun saveSettings(patch: JSONObject): Boolean =
        apiCall(
            method = "PUT",
            path = "/api/v1/settings/v2",
            body = patch,
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    // Stage 5.5: user + store profile updates. These are thin wrappers
    // around the V2 endpoints (mirrored to Firestore by the backend).
    suspend fun updateStoreProfile(patch: JSONObject): Boolean =
        apiCall(
            method = "PATCH",
            path = "/api/v1/seller/store-settings",
            body = patch,
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    /**
     * Upload a product image by URL. The backend stores the URL in
     * [product_media] and the new product's [image_url] is updated.
     * Returns the persisted URL on success, or null on failure.
     */
    suspend fun uploadProductImageUrl(productId: String, url: String, position: Int = 0): String? =
        apiCall(
            method = "POST",
            path = "/api/v1/products/v2/$productId/media",
            body = JSONObject().apply {
                put("url", url)
                put("position", position)
                put("mediaType", "image")
            },
            parse = { o -> o.optString("url").ifBlank { url } },
        )

    // ============================================================
    // SELLER DASHBOARD + STORE STATE (real web-shared endpoints)
    // ============================================================

    /**
     * `GET /api/v1/seller/dashboard/stats` →
     * `{ stats, topProducts, recentOrders, salesSeries }` — the exact
     * payload the web seller dashboard renders. Aggregates are computed
     * by Postgres, never on-device. Null on failure → caller shows its
     * error/retry state instead of fabricated numbers.
     */
    suspend fun fetchSellerDashboard(): com.scottsx.app.data.domain.SellerDashboardData? =
        apiCall(
            method = "GET",
            path = "/api/v1/seller/dashboard/stats",
            body = null,
            parse = { o ->
                val s = o.optJSONObject("stats") ?: return@apiCall null
                val pbs = s.optJSONObject("productsByStatus")
                val stats = com.scottsx.app.data.domain.SellerStats(
                    revenueUgx = s.optLong("revenueUgx", 0L),
                    revenue30Ugx = s.optLong("revenue30Ugx", 0L),
                    orders = s.optInt("orders", 0),
                    orders30 = s.optInt("orders30", 0),
                    avgOrderValueUgx = s.optLong("avgOrderValueUgx", 0L),
                    totalProducts = s.optInt("totalProducts", 0),
                    lowStock = s.optInt("lowStock", 0),
                    outOfStock = s.optInt("outOfStock", 0),
                    topProduct = s.optString("topProduct").takeIf { it.isNotBlank() },
                    unreadMessages = s.optInt("unreadMessages", 0),
                    followers = s.optInt("followers", 0),
                    totalViews = s.optInt("totalViews", 0),
                    draft = pbs?.optInt("draft", 0) ?: 0,
                    pending = pbs?.optInt("pending", 0) ?: 0,
                    approved = pbs?.optInt("approved", 0) ?: 0,
                    rejected = pbs?.optInt("rejected", 0) ?: 0,
                    suspended = pbs?.optInt("suspended", 0) ?: 0,
                    pendingApproval = s.optInt("pendingApproval", 0),
                )
                fun <T> listOf(arr: org.json.JSONArray?, map: (org.json.JSONObject) -> T?): List<T> =
                    (0 until (arr?.length() ?: 0)).mapNotNull { i -> arr?.optJSONObject(i)?.let(map) }
                val topProducts = listOf(o.optJSONArray("topProducts")) { t ->
                    com.scottsx.app.data.domain.SellerTopProduct(
                        title = t.optString("title"),
                        sold = t.optInt("sold", 0),
                    ).takeIf { it.title.isNotBlank() }
                }
                val recentOrders = listOf(o.optJSONArray("recentOrders")) { r ->
                    com.scottsx.app.data.domain.SellerRecentOrder(
                        id = r.optString("id"),
                        buyerId = r.optString("buyerId"),
                        productTitle = r.optString("productTitle"),
                        amount = r.optLong("amount", 0L),
                        quantity = r.optInt("quantity", 1),
                        status = r.optString("status", "pending"),
                        createdAt = r.optString("createdAt"),
                        buyerName = r.optString("buyerName").ifEmpty { "Buyer" },
                    ).takeIf { it.id.isNotBlank() }
                }
                val salesSeries = listOf(o.optJSONArray("salesSeries")) { p ->
                    com.scottsx.app.data.domain.SellerSalesPoint(
                        date = p.optString("date"),
                        orders = p.optInt("orders", 0),
                        revenue = p.optLong("revenue", 0L),
                    ).takeIf { it.date.isNotBlank() }
                }
                com.scottsx.app.data.domain.SellerDashboardData(
                    stats = stats,
                    topProducts = topProducts,
                    recentOrders = recentOrders,
                    salesSeries = salesSeries,
                )
            },
        )

    /**
     * `GET /api/v1/seller/location` → `{ location: { lat, lng, sharing,
     * isOpen, ... } | null }` — the toggle source of truth shared with
     * the web seller dashboard. isOpen lives here (not in stats).
     */
    suspend fun fetchStoreOpenState(): Boolean? =
        apiCall(
            method = "GET",
            path = "/api/v1/seller/location",
            body = null,
            parse = { o -> o.optJSONObject("location")?.optBoolean("isOpen") },
        )

    /** `PATCH /api/v1/seller/open-state` — flip the store open/closed. */
    suspend fun setStoreOpen(isOpen: Boolean): Boolean? =
        apiCall(
            method = "PATCH",
            path = "/api/v1/seller/open-state",
            body = org.json.JSONObject().put("isOpen", isOpen),
            parse = { o -> if (o.has("isOpen")) o.optBoolean("isOpen") else null },
        )

    /**
     * `GET /api/v1/me/notifications/unread-count` → `{ unread: n }` —
     * header badge source; the web uses the same endpoint.
     */
    suspend fun fetchUnreadNotificationCount(): Int =
        apiCall(
            method = "GET",
            path = "/api/v1/me/notifications/unread-count",
            body = null,
            parse = { o -> o.optInt("unread", 0) },
        ) ?: 0

    /**
     * Upload avatar URL for the current user. The backend stores it on
     * the users.avatar_url column (and mirrors to Firestore).
     */
    suspend fun uploadAvatarUrl(avatarUrl: String): Boolean =
        apiCall(
            method = "PATCH",
            path = "/api/v1/user/profile",
            body = JSONObject().put("avatarUrl", avatarUrl),
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    // ----------------------------------------------------------------
    // Nearby sellers — returns JSON array
    // ----------------------------------------------------------------

    data class NearbySeller(
        val sellerId: String,
        val storeName: String,
        val lat: Double,
        val lng: Double,
        val city: String?,
        val address: String?,
        val rating: Double,
        val distanceKm: Double,
        val products: List<NearbyProduct>,
    )

    data class NearbyProduct(
        val id: String,
        val title: String,
        val priceMinor: Long,
        val image: String?,
        val stock: Int,
        val rating: Double,
        val category: String?,
    )

    suspend fun nearbySellers(
        lat: Double,
        lng: Double,
        radiusKm: Double = 25.0,
        category: String? = null,
        minPrice: Long? = null,
        maxPrice: Long? = null,
        limit: Int = 40,
    ): List<NearbySeller> {
        val qs = buildString {
            append("?lat=").append(lat)
            append("&lng=").append(lng)
            append("&radiusKm=").append(radiusKm)
            if (category != null) append("&category=").append(java.net.URLEncoder.encode(category, "UTF-8"))
            if (minPrice != null) append("&minPrice=").append(minPrice)
            if (maxPrice != null) append("&maxPrice=").append(maxPrice)
            append("&limit=").append(limit)
        }
        val arr = apiCallArray(
            method = "GET",
            path = "/api/v1/sellers/v2/nearby$qs",
            body = null,
            parse = { it },
        ) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val prods = r.optJSONArray("products")
            val products = if (prods != null) (0 until prods.length()).mapNotNull { j ->
                val p = prods.optJSONObject(j) ?: return@mapNotNull null
                NearbyProduct(
                    id = p.optString("id"),
                    title = p.optString("title"),
                    priceMinor = p.optLong("price", 0L),
                    image = p.optString("image").takeIf { it.isNotBlank() },
                    stock = p.optInt("stock", 0),
                    rating = p.optDouble("rating", 0.0),
                    category = p.optString("category").takeIf { it.isNotBlank() },
                )
            } else emptyList()
            NearbySeller(
                sellerId = r.optString("seller_id"),
                storeName = r.optString("store_name"),
                lat = r.optDouble("lat", 0.0),
                lng = r.optDouble("lng", 0.0),
                city = r.optString("city").takeIf { it.isNotBlank() },
                address = r.optString("address").takeIf { it.isNotBlank() },
                rating = r.optDouble("rating", 0.0),
                distanceKm = r.optDouble("distance_km", Double.MAX_VALUE),
                products = products,
            )
        }
    }

    suspend fun updateSellerLocation(
        lat: Double,
        lng: Double,
        city: String? = null,
        address: String? = null,
    ): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/sellers/v2/update-location",
            body = JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
                if (city != null) put("city", city)
                if (address != null) put("address", address)
            },
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    // ----------------------------------------------------------------
    // Chat v2 — typed write-through + Firestore mirror
    // ----------------------------------------------------------------

    data class ChatMessage(
        val id: String,
        val conversationId: String,
        val senderUid: String,
        val recipientUid: String?,
        val content: String,
        val role: String,
        val attachmentUrl: String?,
        val attachmentMime: String?,
        val threadParentId: String?,
        val createdAt: String,
    )

    suspend fun sendMessage(
        conversationId: String,
        content: String,
        attachmentUrl: String? = null,
        attachmentMime: String? = null,
        threadParentId: String? = null,
        productId: String? = null,
        productTitle: String? = null,
        productImageUrl: String? = null,
    ): ChatMessage? =
        apiCall(
            method = "POST",
            path = "/api/v1/chat/v2/messages",
            body = JSONObject().apply {
                put("conversationId", conversationId)
                put("content", content)
                if (attachmentUrl != null) put("attachmentUrl", attachmentUrl)
                if (attachmentMime != null) put("attachmentMime", attachmentMime)
                if (threadParentId != null) put("threadParentId", threadParentId)
                if (productId != null) put("productId", productId)
                if (productTitle != null) put("productTitle", productTitle)
                if (productImageUrl != null) put("productImageUrl", productImageUrl)
            },
            parse = { o ->
                ChatMessage(
                    id = o.optString("id"),
                    conversationId = o.optString("conversationId"),
                    senderUid = o.optString("senderUid"),
                    recipientUid = o.optString("recipientUid").takeIf { it.isNotBlank() },
                    content = o.optString("content"),
                    role = o.optString("role"),
                    attachmentUrl = o.optString("attachmentUrl").takeIf { it.isNotBlank() },
                    attachmentMime = o.optString("attachmentMime").takeIf { it.isNotBlank() },
                    threadParentId = o.optString("threadParentId").takeIf { it.isNotBlank() },
                    createdAt = o.optString("createdAt"),
                )
            },
        )

    /**
     * Fetch all messages in a conversation. Used by [MessageStream] to
     * hydrate the thread and to poll for new messages (the Android
     * client compares `createdAt` against the last seen timestamp).
     */
    suspend fun fetchMessages(
        conversationId: String,
        since: String? = null,
        limit: Int = 100,
    ): List<ChatMessage> {
        val qs = buildString {
            append("?limit=").append(limit)
            if (since != null) append("&since=").append(java.net.URLEncoder.encode(since, "UTF-8"))
        }
        val arr = apiCallArray(
            method = "GET",
            path = "/api/v1/chat/v2/conversations/$conversationId/messages$qs",
            body = null,
            parse = { it },
        ) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            ChatMessage(
                id = m.optString("id"),
                conversationId = m.optString("conversationId"),
                senderUid = m.optString("senderUid"),
                recipientUid = m.optString("recipientUid").takeIf { it.isNotBlank() },
                content = m.optString("content"),
                role = m.optString("role"),
                attachmentUrl = m.optString("attachmentUrl").takeIf { it.isNotBlank() },
                attachmentMime = m.optString("attachmentMime").takeIf { it.isNotBlank() },
                threadParentId = m.optString("threadParentId").takeIf { it.isNotBlank() },
                createdAt = m.optString("createdAt"),
            )
        }
    }

    /**
     * Inbox summary — the caller's conversations with the most recent
     * message preview, unread count, and the other party's display
     * name. Used by the MessagesScreen sidebar destination.
     */
    data class Conversation(
        val conversationId: String,
        val otherPartyId: String,
        val otherPartyDisplayName: String,
        val productId: String?,
        val productTitle: String?,
        val productImageUrl: String?,
        val lastMessagePreview: String?,
        val lastMessageAt: String?,
        val unreadCount: Int,
    )

    suspend fun fetchConversations(): List<Conversation> {
        val arr = apiCallArray(
            method = "GET",
            path = "/api/v1/chat/v2/conversations",
            body = null,
            parse = { it },
        ) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            Conversation(
                conversationId = r.optString("conversation_id"),
                otherPartyId = r.optString("other_party_id"),
                otherPartyDisplayName = r.optString("other_party_display_name").ifBlank { "Seller" },
                productId = r.optString("product_id").takeIf { it.isNotBlank() },
                productTitle = r.optString("product_title").takeIf { it.isNotBlank() },
                productImageUrl = r.optString("product_image_url").takeIf { it.isNotBlank() },
                lastMessagePreview = r.optString("last_message_preview").takeIf { it.isNotBlank() },
                lastMessageAt = r.optString("last_message_at").takeIf { it.isNotBlank() },
                unreadCount = r.optInt("unread_count", 0),
            )
        }
    }

    data class ChatUploadHandle(
        val uploadUrl: String,
        val gsPath: String,
        val publicUrl: String,
        val messageId: String,
        val expiresAt: Long,
    )

    suspend fun requestChatUploadUrl(
        conversationId: String,
        mime: String,
        ext: String,
    ): ChatUploadHandle? =
        apiCall(
            method = "POST",
            path = "/api/v1/chat/v2/upload-url",
            body = JSONObject().apply {
                put("conversationId", conversationId)
                put("mime", mime)
                put("ext", ext)
            },
            parse = { o ->
                ChatUploadHandle(
                    uploadUrl = o.optString("uploadUrl"),
                    gsPath = o.optString("gsPath"),
                    publicUrl = o.optString("publicUrl"),
                    messageId = o.optString("messageId"),
                    expiresAt = o.optLong("expiresAt", 0L),
                )
            },
        )

    // ----------------------------------------------------------------
    // Product image upload (seller)
    // ----------------------------------------------------------------

    /**
     * Request a signed upload URL for a generic upload purpose.
     *
     * The backend mints a short-lived (15 min) signed URL that grants
     * the authenticated caller direct write access to a specific
     * Storage path. The client then PUTs the file bytes to that URL
     * using OkHttp / HttpURLConnection. No Firebase SDK credentials
     * are needed on the device.
     *
     * @param purpose "product" | "avatar" | "chat" | "receipt"
     * @param filename Client-supplied filename (server infers extension).
     * @param contentType MIME type, e.g. "image/jpeg".
     * @param conversationId Required when purpose = "chat".
     * @return UploadHandle with the signed URL, gsPath, and a public
     *         downloadUrl the client should store in its DB.
     */
    data class UploadHandle(
        val uploadUrl: String,
        val gsPath: String,
        val publicUrl: String,
        val expiresAt: Long,
    )

    suspend fun requestUploadUrl(
        purpose: String,
        filename: String,
        contentType: String,
        conversationId: String? = null,
    ): UploadHandle? = apiCall(
        method = "POST",
        path = "/api/v1/uploads/signed-url",
        body = JSONObject().apply {
            put("purpose", purpose)
            put("filename", filename)
            put("contentType", contentType)
            if (conversationId != null) put("conversationId", conversationId)
        },
        parse = { o ->
            UploadHandle(
                uploadUrl = o.optString("url"),
                gsPath = o.optString("gsPath"),
                publicUrl = o.optString("downloadUrl"),
                expiresAt = o.optLong("expiresAt", 0L),
            )
        },
    )

    data class ProductImageUploadHandle(
        val uploadUrl: String,
        val gsPath: String,
        val publicUrl: String,
        val expiresAt: Long,
    )

    suspend fun requestProductImageUploadUrl(
        productId: String,
        mime: String,
        ext: String,
    ): ProductImageUploadHandle? =
        apiCall(
            method = "POST",
            path = "/api/v1/products/v2/upload-image-url",
            body = JSONObject().apply {
                put("productId", productId)
                put("mime", mime)
                put("ext", ext)
            },
            parse = { o ->
                ProductImageUploadHandle(
                    uploadUrl = o.optString("uploadUrl"),
                    gsPath = o.optString("gsPath"),
                    publicUrl = o.optString("publicUrl"),
                    expiresAt = o.optLong("expiresAt", 0L),
                )
            },
        )

    suspend fun setProductImage(productId: String, gsPath: String): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/products/v2/$productId/set-image",
            body = JSONObject().put("gsPath", gsPath),
            parse = { o -> o.optBoolean("ok", false) },
        ) ?: false

    /**
     * Create a new product owned by the caller. The caller must be a
     * seller (or admin). On success returns the new product's UUID.
     */
    suspend fun createProduct(
        title: String,
        priceMinor: Long,
        description: String? = null,
        currency: String = "UGX",
        stock: Int = 0,
        category: String? = null,
        imageUrl: String? = null,
        imageGsPath: String? = null,
        sku: String? = null,
    ): String? = apiCall(
        method = "POST",
        path = "/api/v1/products/v2/create",
        body = JSONObject().apply {
            put("title", title)
            put("priceMinor", priceMinor)
            if (description != null) put("description", description)
            put("currency", currency)
            put("stock", stock)
            if (category != null) put("category", category)
            if (imageUrl != null) put("imageUrl", imageUrl)
            if (imageGsPath != null) put("imageGsPath", imageGsPath)
            if (sku != null) put("sku", sku)
        },
        parse = { o -> o.optString("id") },
    )
}
