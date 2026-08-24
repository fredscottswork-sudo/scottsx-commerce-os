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
    // PRODUCTS
    // ============================================================

    /**
     * Fetch the full product catalogue (public endpoint, no auth
     * required). Returns [Product] instances decoded from the
     * v2 product response. The result is intentionally permissive —
     * unknown categories fall back to "All" so a single missing
     * enum entry cannot break the home feed.
     */
    suspend fun fetchProductsList(): List<com.scottsx.app.data.domain.Product> {
        val arr = apiCallArray(
            method = "GET",
            path = "/api/v1/products",
            body = null,
            parse = { it },
        ) ?: return emptyList()
        val out = ArrayList<com.scottsx.app.data.domain.Product>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            out += jsonToProduct(row)
        }
        return out
    }

    private fun jsonToProduct(o: org.json.JSONObject): com.scottsx.app.data.domain.Product {
        val title = o.optString("title")
        val description = o.optString("description")
        val category = com.scottsx.app.data.domain.ProductCategory.fromApiName(o.optString("category"))
            ?: com.scottsx.app.data.domain.ProductCategory.All
        val sellerId = o.optString("sellerId")
        val sellerName = o.optString("sellerBusinessName").ifEmpty { "ScottsTechX Seller" }
        val priceUgx = o.optLong("priceMinor", 0L)
        val imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() } ?: ""
        // Seller data class signature: id, name, rating, location, verified
        // The Seller.fullConstructorWithStoreName extension wraps this with extra data.
        val seller = com.scottsx.app.data.domain.Seller(
            id = sellerId,
            name = sellerName,
            rating = 4.5f,
            location = "Kampala",
            verified = true,
        )
        val brand = com.scottsx.app.data.domain.Brand(
            id = "default",
            name = "Generic",
        )
        return com.scottsx.app.data.domain.Product(
            id = o.optString("id"),
            name = title,
            shortDescription = description.take(80),
            description = description,
            priceUgx = priceUgx,
            oldPriceUgx = null,
            category = category,
            brand = brand,
            seller = seller,
            imageUrl = imageUrl,
            stock = o.optInt("stockQuantity", 1),
            rating = o.optDouble("productTrustScore", 4.4).toFloat(),
            ratingCount = 12,
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
