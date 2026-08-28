package com.scottsx.app.data.remote

import com.scottsx.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stage 5 — REST client for the Firebase-backed backend.
 *
 * Talks to the canonical /api/v1 routes ({auth/me, me/…, conversations,
 * sellers/nearby, products}) using the HS256 JWT from
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

    /** GET /products?query — full-text catalogue search. Null = request failed. */
    suspend fun searchProducts(
        query: String? = null,
        category: String? = null,
        pageSize: Int = 50,
    ): List<com.scottsx.app.data.domain.Product>? {
        val qs = buildString {
            append("?pageSize=").append(pageSize)
            if (!query.isNullOrBlank()) append("&q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
            if (!category.isNullOrBlank()) append("&category=").append(java.net.URLEncoder.encode(category, "UTF-8"))
        }
        val obj = apiCall(
            method = "GET", path = "/api/v1/products/search$qs", body = null,
            parse = { it },
        ) ?: return null
        val arr = obj.optJSONArray("products") ?: return null
        val out = ArrayList<com.scottsx.app.data.domain.Product>(arr.length())
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += jsonToProduct(it) }
        return out
    }

    /** GET /products/:id → a single domain product. Null = miss or failure. */
    suspend fun fetchProductById(id: String): com.scottsx.app.data.domain.Product? = apiCall(
        method = "GET", path = "/api/v1/products/$id", body = null,
        parse = { o -> o.optJSONObject("product")?.let { jsonToProduct(it) } },
    )

    /** GET /products/:id/related → related domain products. */
    suspend fun fetchRelatedProducts(id: String): List<com.scottsx.app.data.domain.Product>? {
        val obj = apiCall(
            method = "GET", path = "/api/v1/products/$id/related", body = null,
            parse = { it },
        ) ?: return null
        val arr = obj.optJSONArray("products") ?: return null
        val out = ArrayList<com.scottsx.app.data.domain.Product>(arr.length())
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += jsonToProduct(it) }
        return out
    }

    /** `GET /products/facets` — real category/brand/popularity chips for Search. */
    data class Facet(val name: String, val count: Int)

    data class CatalogFacets(
        val categories: List<Facet>,
        val brands: List<Facet>,
    )

    suspend fun fetchCatalogFacets(): CatalogFacets? = apiCall(
        method = "GET", path = "/api/v1/products/facets", body = null,
        parse = { o ->
            fun list(name: String): List<Facet> {
                val arr = o.optJSONArray(name) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let {
                        Facet(it.optString("name"), it.optInt("count", 0))
                    }?.takeIf { it.name.isNotBlank() }
                }
            }
            CatalogFacets(categories = list("categories"), brands = list("brands"))
        },
    )

    /** `GET /products/suggest?q=` — server typeahead for the search bar. */
    suspend fun fetchSearchSuggestions(q: String, limit: Int = 8): List<String>? = apiCall(
        method = "GET",
        path = "/api/v1/products/suggest?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&limit=$limit",
        body = null,
        parse = { o ->
            val arr = o.optJSONArray("suggestions")
            if (arr == null) emptyList()
            else (0 until arr.length()).mapNotNull { i ->
                arr.opt(i)?.toString()?.takeIf { s -> s.isNotBlank() }
            }
        },
    )

    // ── Ratings (reviews) ───────────────────────────────────────────────
    data class ProductRating(
        val id: String,
        val stars: Int,
        val comment: String,
        val createdAt: String,
        val authorName: String,
    )

    data class RatingSummary(val average: Double, val count: Int)

    data class ProductRatingsPage(val ratings: List<ProductRating>, val summary: RatingSummary)

    /** Map a backend rating row to the review UI model — no fabrication. */
    fun ProductRating.toDomainReview(productId: String): com.scottsx.app.data.domain.Review =
        com.scottsx.app.data.domain.Review(
            id = id,
            productId = productId,
            authorName = authorName,
            rating = stars.coerceIn(1, 5),
            dateLabel = createdAt.replace('T', ' ').take(10),
            text = comment,
        )

    /** Star histogram derived from the real rows (used by PDP + reviews screen). */
    fun ProductRatingsPage.toDistribution(): com.scottsx.app.data.domain.RatingDistribution {
        fun n(star: Int) = ratings.count { it.stars == star }
        return com.scottsx.app.data.domain.RatingDistribution(
            five = n(5), four = n(4), three = n(3), two = n(2), one = n(1),
        )
    }

    suspend fun fetchProductRatings(productId: String): ProductRatingsPage? = apiCall(
        method = "GET", path = "/api/v1/products/$productId/ratings", body = null,
        parse = { o ->
            val arr = o.optJSONArray("ratings")
            val ratings = if (arr != null) (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { r ->
                    ProductRating(
                        id = r.optString("id"),
                        stars = r.optInt("stars", 0),
                        comment = r.optString("comment"),
                        createdAt = r.optString("createdAt"),
                        authorName = r.optString("authorName").ifBlank { "Buyer" },
                    )
                }
            } else emptyList()
            val s = o.optJSONObject("summary")
            ProductRatingsPage(
                ratings = ratings,
                summary = RatingSummary(
                    average = s?.optDouble("average", 0.0) ?: ratings.map { it.stars }.average().let { if (it.isNaN()) 0.0 else it },
                    count = s?.optInt("count", ratings.size) ?: ratings.size,
                ),
            )
        },
    )

    suspend fun rateProduct(productId: String, stars: Int, comment: String = ""): Boolean = apiCall(
        method = "POST", path = "/api/v1/products/$productId/ratings",
        body = JSONObject().apply {
            put("stars", stars)
            put("comment", comment)
        },
        parse = { o -> o.optJSONObject("rating") != null || o.optBoolean("ok", true) },
    ) ?: false

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
    /** Public so cart/wishlist/detail surfaces reuse one honest mapper. */
    fun jsonToProduct(o: org.json.JSONObject): com.scottsx.app.data.domain.Product {
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
        method = "GET", path = "/api/v1/auth/me", body = null,
        parse = { o -> o.optJSONObject("user") },
    )

    /** PATCH /auth/me only accepts displayName / phone / city / profilePhotoUrl. */
    suspend fun updateUserProfile(patch: JSONObject): Boolean = apiCall(
        method = "PATCH", path = "/api/v1/auth/me", body = remapProfilePatch(patch),
        parse = { o -> o.optJSONObject("user") != null || o.optBoolean("ok", false) },
    ) ?: false

    private fun remapProfilePatch(patch: JSONObject): JSONObject {
        val out = JSONObject()
        fun str(vararg keys: String): String? {
            for (k in keys) {
                val v = patch.optString(k)
                if (v.isNotBlank()) return v
            }
            return null
        }
        str("displayName", "name")?.let { out.put("displayName", it) }
        str("phone")?.let { out.put("phone", it) }
        str("city")?.let { out.put("city", it) }
        str("profilePhotoUrl", "avatarUrl", "avatar_url", "photoUrl")?.let { out.put("profilePhotoUrl", it) }
        return out
    }

    suspend fun updateAvatar(avatarUrl: String): Boolean = apiCall(
        method = "PATCH",
        path = "/api/v1/auth/me",
        body = JSONObject().put("profilePhotoUrl", avatarUrl),
        parse = { o -> o.optJSONObject("user") != null || o.optBoolean("ok", false) },
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
        val arr = apiCall(method = "GET", path = "/api/v1/me/addresses", body = null, parse = { o -> o.optJSONArray("addresses") }) ?: return emptyList()
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
        method = "POST", path = "/api/v1/me/addresses", body = body,
        parse = { o -> o.optJSONObject("address")?.optString("id") ?: o.optString("id") },
    )

    suspend fun updateAddress(id: String, body: JSONObject): Boolean = apiCall(
        method = "PATCH", path = "/api/v1/me/addresses/$id", body = body,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    suspend fun deleteAddress(id: String): Boolean = apiCall(
        method = "DELETE", path = "/api/v1/me/addresses/$id", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    // Payment methods
    data class PaymentMethod(
        val id: String, val kind: String, val provider: String?, val label: String,
        val account: String, val isDefault: Boolean, val expiresAt: String?,
    )

    suspend fun fetchPaymentMethods(): List<PaymentMethod> {
        val arr = apiCall(method = "GET", path = "/api/v1/me/payment-methods", body = null, parse = { o -> o.optJSONArray("paymentMethods") }) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            PaymentMethod(
                id = p.optString("id"),
                kind = p.optString("type").ifBlank { p.optString("kind") },
                provider = p.optString("provider").takeIf { it.isNotBlank() },
                label = p.optString("label"),
                account = p.optString("account").ifBlank {
                    // Server stores `last4` (cards) or `phone` (mobile money).
                    val last4 = p.optString("last4")
                    if (last4.isNotBlank()) "•••• $last4" else p.optString("phone")
                },
                isDefault = p.optBoolean("isDefault", false),
                expiresAt = p.optString("expiresAt").takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun createPaymentMethod(body: JSONObject): String? = apiCall(
        method = "POST", path = "/api/v1/me/payment-methods", body = remapPaymentMethodBody(body),
        parse = { o -> o.optJSONObject("paymentMethod")?.optString("id") ?: o.optString("id") },
    )

    suspend fun deletePaymentMethod(id: String): Boolean = apiCall(
        method = "DELETE", path = "/api/v1/me/payment-methods/$id", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    /** Server schema: type | label | last4 | phone | isDefault. */
    private fun remapPaymentMethodBody(body: JSONObject): JSONObject {
        val out = JSONObject()
        val type = body.optString("type").ifBlank { body.optString("kind") }
        if (type.isNotBlank()) out.put("type", type)
        val label = body.optString("label")
        if (label.isNotBlank()) out.put("label", label)
        val account = body.optString("account").ifBlank { body.optString("last4") }
        if (account.isNotBlank()) {
            val digits = account.filter { it.isDigit() }
            out.put("last4", digits.takeLast(4).ifBlank { account.takeLast(4) })
        }
        val phone = body.optString("phone")
        if (phone.isNotBlank()) out.put("phone", phone)
        if (body.has("isDefault")) out.put("isDefault", body.optBoolean("isDefault", false))
        return out
    }

    // Saved products (bookmarks)
    suspend fun fetchSavedProducts(): JSONArray? = apiCall(
        method = "GET", path = "/api/v1/me/bookmarks", body = null,
        parse = { o -> o.optJSONArray("products") },
    )

    /** The backend exposes bookmarks via a single toggle endpoint. */
    private suspend fun toggleBookmark(productId: String, want: Boolean): Boolean =
        apiCall(
            method = "POST", path = "/api/v1/me/bookmarks/toggle",
            body = JSONObject().put("productId", productId),
            parse = { o -> o.optBoolean("bookmarked") == want },
        ) ?: false

    suspend fun saveProduct(productId: String): Boolean = toggleBookmark(productId, want = true)

    suspend fun unsaveProduct(productId: String): Boolean = toggleBookmark(productId, want = false)

    // Saved sellers
    suspend fun fetchSavedSellers(): JSONArray? = apiCall(
        method = "GET", path = "/api/v1/me/favorites", body = null,
        parse = { o -> o.optJSONArray("sellers") },
    )

    suspend fun saveSeller(sellerId: String): Boolean = apiCall(
        method = "POST", path = "/api/v1/me/favorites/$sellerId", body = null,
        parse = { o -> o.optBoolean("ok", false) || o.optBoolean("following") },
    ) ?: false

    suspend fun unsaveSeller(sellerId: String): Boolean = apiCall(
        method = "DELETE", path = "/api/v1/me/favorites/$sellerId", body = null,
        parse = { o -> o.optBoolean("ok", false) || !o.optBoolean("following", true) },
    ) ?: false

    // Refunds
    suspend fun fetchRefunds(): JSONArray? = apiCall(
        method = "GET", path = "/api/v1/me/refunds", body = null,
        parse = { o -> o.optJSONArray("refunds") },
    )

    suspend fun createRefund(body: JSONObject): String? = apiCall(
        method = "POST", path = "/api/v1/me/refunds", body = body,
        parse = { o -> o.optJSONObject("refund")?.optString("id") ?: o.optString("id") },
    )

    // Returns
    /**
     * There is no returns API on the canonical backend — returns are
     * handled through refund claims. Real no-ops so callers degrade
     * honestly instead of hammering a 404 route.
     */
    suspend fun fetchReturns(): JSONArray? = null

    suspend fun createReturn(body: JSONObject): String? = null

    // Support tickets
    suspend fun fetchTickets(): JSONArray? = apiCall(
        method = "GET", path = "/api/v1/me/support/tickets", body = null,
        parse = { o -> o.optJSONArray("tickets") },
    )

    suspend fun createTicket(category: String, subject: String, message: String, attachmentUrl: String? = null): String? = apiCall(
        method = "POST", path = "/api/v1/me/support/tickets",
        body = JSONObject().apply {
            // Canonical schema is {subject, message} — fold the caller's
            // category into the subject so it still reaches the inbox.
            put("subject", if (category.isBlank()) subject else "[$category] $subject")
            val full = if (attachmentUrl != null) "$message\n\nAttachment: $attachmentUrl" else message
            put("message", full)
        },
        parse = { o -> o.optJSONObject("ticket")?.optString("id") ?: o.optString("id") },
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
    suspend fun fetchNotifications(): JSONArray? = apiCall(
        method = "GET", path = "/api/v1/me/notifications", body = null,
        parse = { o -> o.optJSONArray("notifications") },
    )

    suspend fun markAllNotificationsRead(): Boolean = apiCall(
        method = "POST", path = "/api/v1/me/notifications/read-all", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    suspend fun markNotificationRead(id: String): Boolean = apiCall(
        method = "POST", path = "/api/v1/me/notifications/$id/read", body = null,
        parse = { o -> o.optBoolean("ok", false) },
    ) ?: false

    /** No audit-log route exists on the canonical backend. */
    suspend fun fetchMyAudit(): JSONArray? = null


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

    /**
     * The /memory/v2 AI-signal pipeline no longer exists on the backend.
     * These stay as silent no-ops so existing call sites compile and the
     * app doesn't spam 404s; "Clear AI memory" reports false so the UI can
     * show that the feature is unavailable.
     */
    suspend fun recordSignal(kind: String, value: String) {
        // Retired — no-op.
    }

    suspend fun clearAiMemory(): Boolean = false

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

    /** Canonical settings live at GET/PATCH /me/preferences. */
    suspend fun loadSettings(): Settings? =
        apiCall(
            method = "GET",
            path = "/api/v1/me/preferences",
            body = null,
            parse = { o ->
                val p = o.optJSONObject("preferences") ?: o
                Settings(
                    theme = p.optString("theme", "system"),
                    language = p.optString("language", "en"),
                    notificationsEnabled = p.optBoolean("notifyMessages", true) || p.optBoolean("notifyOrderUpdates", true),
                    notificationSound = true,
                    locationSharing = "approximate",
                    privacyShowReceipts = true,
                    privacyShowTransactions = true,
                    aiPersonalizationEnabled = false,
                    preferredLanguage = p.optString("language", "en"),
                    preferredCurrency = p.optString("currency", "UGX"),
                )
            },
        )

    suspend fun saveSettings(patch: JSONObject): Boolean =
        apiCall(
            method = "PATCH",
            path = "/api/v1/me/preferences",
            body = remapSettingsPatch(patch),
            parse = { o -> o.optJSONObject("preferences") != null || o.optBoolean("ok", false) },
        ) ?: false

    private fun remapSettingsPatch(patch: JSONObject): JSONObject {
        val out = JSONObject()
        if (patch.has("theme")) out.put("theme", patch.optString("theme", "system"))
        if (patch.has("language")) out.put("language", patch.optString("language", "en"))
        if (patch.has("currency")) out.put("currency", patch.optString("currency", "UGX"))
        else if (patch.has("preferredCurrency")) out.put("currency", patch.optString("preferredCurrency", "UGX"))
        if (patch.has("notifyOrderUpdates")) out.put("notifyOrderUpdates", patch.optBoolean("notifyOrderUpdates"))
        if (patch.has("notifyMessages")) out.put("notifyMessages", patch.optBoolean("notifyMessages"))
        if (patch.has("notifyMarketing")) out.put("notifyMarketing", patch.optBoolean("notifyMarketing"))
        if (patch.has("notificationsEnabled")) {
            val on = patch.optBoolean("notificationsEnabled")
            if (!out.has("notifyOrderUpdates")) out.put("notifyOrderUpdates", on)
            if (!out.has("notifyMessages")) out.put("notifyMessages", on)
        }
        return out
    }

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
    suspend fun uploadAvatarUrl(avatarUrl: String): Boolean = updateAvatar(avatarUrl)

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
        // Canonical route: GET /api/v1/sellers/nearby → the server already
        // computes distance/ETA/live flags. It returns productCount rather
        // than a products array, so NearbySeller.products stays empty and
        // the map/list surfaces show real store metadata instead.
        val arr = apiCall(
            method = "GET",
            path = "/api/v1/sellers/nearby$qs",
            body = null,
            parse = { o -> o.optJSONArray("sellers") },
        ) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            NearbySeller(
                sellerId = r.optString("id"),
                storeName = r.optString("storeName").ifBlank { r.optString("name") },
                lat = r.optDouble("lat", 0.0),
                lng = r.optDouble("lng", 0.0),
                city = r.optString("city").takeIf { it.isNotBlank() },
                address = r.optString("address").takeIf { it.isNotBlank() },
                rating = r.optDouble("rating", 0.0),
                distanceKm = r.optDouble("distanceKm", Double.MAX_VALUE),
                products = emptyList(),
            )
        }
    }

    /** POST /api/v1/seller/location { lat, lng, sharing, city? } — sellers only. */
    suspend fun updateSellerLocation(
        lat: Double,
        lng: Double,
        city: String? = null,
        address: String? = null,
    ): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/seller/location",
            body = JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
                put("sharing", true)
                if (city != null) put("city", city)
            },
            parse = { o -> o.optJSONObject("location") != null || o.optBoolean("ok", false) },
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
            path = "/api/v1/conversations/$conversationId/messages",
            body = JSONObject().apply {
                put("text", content)
                if (attachmentUrl != null) put("imageUrl", attachmentUrl)
                if (threadParentId != null) put("replyToId", threadParentId)
                if (productId != null) put("productId", productId)
            },
            parse = { o ->
                val m = o.optJSONObject("message") ?: o
                chatMessageFromJson(m, conversationId)
            },
        )

    private fun chatMessageFromJson(m: JSONObject, conversationId: String): ChatMessage =
        ChatMessage(
            id = m.optString("id"),
            conversationId = m.optString("conversationId").ifBlank { conversationId },
            senderUid = m.optString("senderId").ifBlank { m.optString("senderUid") },
            recipientUid = null,
            content = m.optString("text").ifBlank { m.optString("content") },
            role = m.optString("kind").ifBlank { m.optString("role") }.ifBlank { "text" },
            attachmentUrl = m.optString("imageUrl").ifBlank { m.optString("attachmentUrl") }.takeIf { it.isNotBlank() },
            attachmentMime = m.optString("attachmentName").ifBlank { m.optString("attachmentMime") }.takeIf { it.isNotBlank() },
            threadParentId = m.optString("replyToId").ifBlank { m.optString("threadParentId") }.takeIf { it.isNotBlank() },
            createdAt = m.optString("createdAt"),
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
        // The canonical route returns the whole thread (ascending). The
        // legacy `since`/`limit` windowing is applied client-side so
        // MessageStream's incremental polling keeps working — ISO-8601
        // timestamps compare correctly as strings.
        val arr = apiCall(
            method = "GET",
            path = "/api/v1/conversations/$conversationId/messages",
            body = null,
            parse = { o -> o.optJSONArray("messages") },
        ) ?: return emptyList()
        var list = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { chatMessageFromJson(it, conversationId) }
        }
        if (since != null) list = list.filter { it.createdAt > since }
        if (list.size > limit) list = list.takeLast(limit)
        return list
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
        val arr = apiCall(
            method = "GET",
            path = "/api/v1/conversations",
            body = null,
            parse = { o -> o.optJSONArray("conversations") },
        ) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val other = r.optJSONObject("otherParty")
            Conversation(
                conversationId = r.optString("id"),
                otherPartyId = other?.optString("id") ?: "",
                otherPartyDisplayName = (other?.optString("name") ?: "").ifBlank { "Seller" },
                productId = r.optString("productId").takeIf { it.isNotBlank() },
                productTitle = r.optString("productTitle").takeIf { it.isNotBlank() },
                productImageUrl = r.optString("productImageUrl").takeIf { it.isNotBlank() },
                lastMessagePreview = r.optString("lastMessage").takeIf { it.isNotBlank() },
                lastMessageAt = r.optString("lastTime").takeIf { it.isNotBlank() },
                unreadCount = r.optInt("unread", 0),
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

    /**
     * Chat image uploads have no signed-URL route on the canonical backend
     * (uploads are multipart POST /api/v1/uploads/images). Returning null
     * keeps the picker flow failing visibly instead of 404-spamming; the
     * multipart upload arrives with the image-attachment wave.
     */
    suspend fun requestChatUploadUrl(
        conversationId: String,
        mime: String,
        ext: String,
    ): ChatUploadHandle? = null

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

    // ----------------------------------------------------------------
    // Seller orders / listings / public storefront (live backend)
    // ----------------------------------------------------------------

    /** `GET /api/v1/seller/orders` → full order list for the seller home. */
    suspend fun fetchSellerOrders(): List<com.scottsx.app.data.domain.SellerApiOrder>? = apiCall(
        method = "GET", path = "/api/v1/seller/orders", body = null,
        parse = { o ->
            val arr = o.optJSONArray("orders")
            if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { r ->
                    com.scottsx.app.data.domain.SellerApiOrder(
                        id = r.optString("id"),
                        buyerId = r.optString("buyerId"),
                        title = r.optString("title"),
                        amount = r.optLong("amount", 0L),
                        quantity = r.optInt("quantity", 1),
                        status = r.optString("status"),
                        createdAt = r.optString("createdAt"),
                        buyerName = r.optString("buyerName").ifBlank { "Buyer" },
                    )
                }
            }
        },
    )

    /** `GET /api/v1/seller/products?status=` → this seller's listings + per-status counts. */
    suspend fun fetchSellerProducts(status: String? = null): com.scottsx.app.data.domain.SellerProductList? = apiCall(
        method = "GET",
        path = if (status.isNullOrBlank() || status == "all") "/api/v1/seller/products" else "/api/v1/seller/products?status=$status",
        body = null,
        parse = { o ->
            val arr = o.optJSONArray("products")
            val products = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { jsonToProduct(it) }
            }
            val c = o.optJSONObject("counts")
            val counts = mutableMapOf<String, Int>()
            c?.keys()?.forEach { k -> counts[k] = c.optInt(k, 0) }
            com.scottsx.app.data.domain.SellerProductList(products = products, counts = counts)
        },
    )

    // ----------------------------------------------------------------
    // Public storefront — `GET /api/v1/sellers/:id` → { seller, products }
    // ----------------------------------------------------------------

    data class StorefrontPage(
        val sellerId: String,
        val storeName: String,
        val description: String,
        val city: String,
        val address: String,
        val verified: Boolean,
        val rating: Double,
        val logoUrl: String?,
        val deliveryFeeUgx: Long,
        val freeAboveUgx: Long,
        val codEnabled: Boolean,
        val products: List<com.scottsx.app.data.domain.Product>,
    )

    suspend fun fetchStorefront(sellerId: String): StorefrontPage? = apiCall(
        method = "GET", path = "/api/v1/sellers/$sellerId", body = null,
        parse = { o ->
            val s = o.optJSONObject("seller") ?: return@apiCall null
            val arr = o.optJSONArray("products")
            val products = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { jsonToProduct(it) }
            }
            StorefrontPage(
                sellerId = s.optString("id"),
                storeName = s.optString("storeName").ifBlank { s.optString("name") },
                description = s.optString("description"),
                city = s.optString("city"),
                address = s.optString("address"),
                verified = s.optBoolean("verified", false),
                rating = s.optDouble("rating", 0.0),
                logoUrl = s.optString("logoUrl").takeIf { it.isNotBlank() },
                deliveryFeeUgx = s.optLong("deliveryFeeUgx", 0L),
                freeAboveUgx = s.optLong("freeAboveUgx", 0L),
                codEnabled = s.optBoolean("codEnabled", false),
                products = products,
            )
        },
    )

    /** POST /api/v1/conversations { sellerId, productId? } → conversation id (creates or re-opens). */
    suspend fun openConversation(sellerId: String, productId: String? = null): String? = apiCall(
        method = "POST", path = "/api/v1/conversations",
        body = JSONObject().apply {
            put("sellerId", sellerId)
            if (productId != null) put("productId", productId)
        },
        parse = { o -> o.optJSONObject("conversation")?.optString("id")?.takeIf { it.isNotBlank() } },
    )

    /** Favorite (followed) sellers — real stores the buyer can start chats with. */
    data class FavoriteSeller(
        val id: String,
        val storeName: String,
        val city: String,
        val rating: Double,
        val verified: Boolean,
        val logoUrl: String?,
        val productCount: Int,
    )

    suspend fun fetchFavoriteSellers(): List<FavoriteSeller>? = apiCall(
        method = "GET", path = "/api/v1/me/favorites", body = null,
        parse = { o ->
            val arr = o.optJSONArray("sellers") ?: return@apiCall emptyList()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { r ->
                    FavoriteSeller(
                        id = r.optString("id"),
                        storeName = r.optString("storeName"),
                        city = r.optString("city"),
                        rating = r.optDouble("rating", 0.0),
                        verified = r.optBoolean("verified", false),
                        logoUrl = r.optString("logoUrl").takeIf { it.isNotBlank() },
                        productCount = r.optInt("productCount", 0),
                    )
                }
            }
        },
    )

    // ----------------------------------------------------------------
    // Buyer orders — `GET /api/v1/me/orders`
    // ----------------------------------------------------------------

    data class MyOrder(
        val id: String,
        val sellerId: String,
        val title: String,
        val amountUgx: Long,
        val quantity: Int,
        val status: String,
        val createdAt: String,
    ) {
        val displayStatus: String
            get() = status.replaceFirstChar { it.uppercase() }
    }

    suspend fun fetchMyOrders(): List<MyOrder>? = apiCall(
        method = "GET", path = "/api/v1/me/orders", body = null,
        parse = { o ->
            val arr = o.optJSONArray("orders") ?: return@apiCall emptyList()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { r ->
                    MyOrder(
                        id = r.optString("id"),
                        sellerId = r.optString("sellerId"),
                        title = r.optString("title"),
                        amountUgx = r.optLong("amount", 0L),
                        quantity = r.optInt("quantity", 1),
                        status = r.optString("status", "pending"),
                        createdAt = r.optString("createdAt"),
                    )
                }
            }
        },
    )

    /** Sidebar badge — orders still waiting on the seller pipeline. */
    suspend fun fetchPendingOrderCount(): Int =
        fetchMyOrders()?.count { it.status == "pending" || it.status == "paid" } ?: 0

    /** Total unread chat count from the conversations envelope. */
    suspend fun fetchConversationUnreadCount(): Int = apiCall(
        method = "GET", path = "/api/v1/conversations", body = null,
        parse = { o -> o.optInt("totalUnread", 0) },
    ) ?: 0


    // ----------------------------------------------------------------
    // Cart — canonical /api/v1/me/cart (server is the source of truth)
    // ----------------------------------------------------------------

    data class CartLine(
        val productId: String,
        val quantity: Int,
        val title: String,
        val priceMinor: Long,
        val stockQuantity: Int,
        val imageUrl: String?,
        val status: String,
        val sellerId: String,
        val sellerName: String,
        val lineTotalMinor: Long,
    )

    data class ServerCart(
        val items: List<CartLine>,
        val subtotalMinor: Long,
        val itemCount: Int,
        val currency: String,
    )

    data class CheckoutResult(
        val orderIds: List<String>,
        val orderCount: Int,
        val totalMinor: Long,
        val currency: String,
        val message: String,
    )

    private fun cartLineFromJson(o: JSONObject): CartLine = CartLine(
        productId = o.optString("productId"),
        quantity = o.optInt("quantity", 1),
        title = o.optString("title"),
        priceMinor = o.optLong("priceMinor", 0L),
        stockQuantity = o.optInt("stockQuantity", 0),
        imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
        status = o.optString("status"),
        sellerId = o.optString("sellerId"),
        sellerName = o.optString("sellerName"),
        lineTotalMinor = o.optLong("lineTotalMinor", 0L),
    )

    private fun serverCartFromJson(o: JSONObject): ServerCart {
        val arr = o.optJSONArray("items")
        val items = if (arr != null) (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { cartLineFromJson(it) }
        } else emptyList()
        return ServerCart(
            items = items,
            subtotalMinor = o.optLong("subtotalMinor", items.sumOf { it.lineTotalMinor }),
            itemCount = o.optInt("itemCount", items.sumOf { it.quantity }),
            currency = o.optString("currency", "UGX"),
        )
    }

    suspend fun fetchCart(): ServerCart? = apiCall(
        method = "GET", path = "/api/v1/me/cart", body = null,
        parse = { o -> serverCartFromJson(o) },
    )

    suspend fun addCartItem(productId: String, quantity: Int = 1): ServerCart? = apiCall(
        method = "POST", path = "/api/v1/me/cart",
        body = JSONObject().put("productId", productId).put("quantity", quantity),
        parse = { o -> serverCartFromJson(o) },
    )

    /** quantity 0 removes the line (server semantics). */
    suspend fun setCartItemQuantity(productId: String, quantity: Int): ServerCart? = apiCall(
        method = "PATCH", path = "/api/v1/me/cart/$productId",
        body = JSONObject().put("quantity", quantity),
        parse = { o -> serverCartFromJson(o) },
    )

    suspend fun removeCartItem(productId: String): ServerCart? = apiCall(
        method = "DELETE", path = "/api/v1/me/cart/$productId", body = null,
        parse = { o -> serverCartFromJson(o) },
    )

    suspend fun clearServerCart(): ServerCart? = apiCall(
        method = "DELETE", path = "/api/v1/me/cart", body = null,
        parse = { o -> serverCartFromJson(o) },
    )

    suspend fun checkoutCart(phone: String? = null, note: String? = null, addressId: String? = null): CheckoutResult? = apiCall(
        method = "POST", path = "/api/v1/me/cart/checkout",
        body = JSONObject().apply {
            if (phone != null) put("phone", phone)
            if (note != null) put("note", note)
            if (addressId != null) put("addressId", addressId)
        },
        parse = { o ->
            val arr = o.optJSONArray("orders")
            val ids = if (arr != null) (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
            } else emptyList()
            CheckoutResult(
                orderIds = ids,
                orderCount = o.optInt("orderCount", ids.size),
                totalMinor = o.optLong("totalMinor", 0L),
                currency = o.optString("currency", "UGX"),
                message = o.optString("message").ifBlank { "Order placed." },
            )
        },
    )
}
