package com.scottsx.app.data.remote

import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.Address
import com.scottsx.app.data.domain.optStringSafe
import com.scottsx.app.data.domain.AiReply
import com.scottsx.app.data.domain.AppNotification
import com.scottsx.app.data.domain.AuthResult
import com.scottsx.app.data.domain.ChatMessage
import com.scottsx.app.data.domain.Cart
import com.scottsx.app.data.domain.CartCheckoutResult
import com.scottsx.app.BuildConfig
import com.scottsx.app.data.domain.CheckoutResult
import com.scottsx.app.data.domain.CmsPage
import com.scottsx.app.data.domain.Conversation
import com.scottsx.app.data.domain.CurrentUserPayload
import com.scottsx.app.data.domain.Faq
import com.scottsx.app.data.domain.Inbox
import com.scottsx.app.data.domain.InboxCounts
import com.scottsx.app.data.domain.AiSearchResult
import com.scottsx.app.data.domain.NearbyResult
import com.scottsx.app.data.domain.NearbySeller
import com.scottsx.app.data.domain.NewProductPayload
import com.scottsx.app.data.domain.Order
import com.scottsx.app.data.domain.PaymentMethod
import com.scottsx.app.data.domain.Place
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.QuickReplyItem
import com.scottsx.app.data.domain.Refund
import com.scottsx.app.data.domain.SellerDashboard
import com.scottsx.app.data.domain.SellerDashboardStats
import com.scottsx.app.data.domain.SellerLocationState
import com.scottsx.app.data.domain.SellerProfile
import com.scottsx.app.data.domain.StoreSettings
import com.scottsx.app.data.domain.SupportTicket
import com.scottsx.app.data.domain.Transcript
import com.scottsx.app.data.domain.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ScottsTechX — ALL network calls live here.
 *
 * One OkHttpClient, one base URL, one bearer-token source (SessionCache).
 * Every function returns a typed model (or null). No raw JSON escapes this file.
 */
object V2Client {

    // Set at build time from app/build.gradle.kts. Defaults to the emulator
    // loopback (10.0.2.2 -> the host machine); override per build with
    //   ./gradlew assembleRelease -PapiBaseUrl=https://api.example.com/api/v1
    // For a physical phone on your LAN, pass your PC's IP the same way.
    private val BASE_URL = BuildConfig.API_BASE_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ── low-level request helper (blocking; callers use withContext(IO)) ──────

    private fun raw(path: String, method: String, body: JSONObject?, auth: Boolean): JSONObject {
        val builder = Request.Builder()
            .url("$BASE_URL$path")
            .method(method, body?.toString()?.toRequestBody(jsonMedia))

        if (auth) {
            SessionCache.authToken()?.let { builder.header("Authorization", "Bearer $it") }
        }

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val payload = try { JSONObject(text) } catch (_: Exception) { null }
                val message = payload?.optString("error").orEmpty().ifBlank { "HTTP ${response.code}" }

                // The backend refuses every private route until the address is
                // verified. Surface that as its own type so the UI can send the
                // user to the verification screen instead of showing a generic
                // failure. The session is still valid, so it must NOT be cleared.
                if (response.code == 403 && payload?.optString("code") == "EMAIL_NOT_VERIFIED") {
                    throw EmailNotVerifiedException(
                        message = message,
                        email = payload.optString("email").takeIf { it.isNotBlank() },
                    )
                }
                throw IOException(message)
            }
            if (text.isBlank()) return JSONObject()
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject().put("raw", text)
            }
        }
    }

    private suspend fun call(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        auth: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) { raw(path, method, body, auth) }

    // ── Image upload ──────────────────────────────────────────────────────────

    /**
     * Upload product photo bytes and return the URL to store on the listing.
     *
     * Sellers work from a phone: the photo comes from the camera or the gallery,
     * so there is no public URL to paste. The backend stores the bytes (Firebase
     * Storage when configured, Postgres otherwise) and hands back a URL that
     * works for signed-out buyers too.
     *
     * The returned URL may be API-relative ("/api/v1/uploads/images/..."), so
     * use [absoluteMediaUrl] before handing it to an image loader.
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String = "photo.jpg",
        mimeType: String = "image/jpeg",
    ): String? = withContext(Dispatchers.IO) {
        var uploadedUrl: String? = null
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    fileName,
                    bytes.toRequestBody(mimeType.toMediaType(), 0, bytes.size),
                )
                .build()

            val builder = Request.Builder()
                .url("$BASE_URL/uploads/images")
                .post(body)
            SessionCache.authToken()?.let { builder.header("Authorization", "Bearer $it") }

            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val message = try {
                        JSONObject(text).optString("error").ifBlank { "HTTP ${response.code}" }
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                    throw IOException(message)
                }
                val parsed = JSONObject(text).optStringSafe("url")
                if (parsed.isNotBlank()) uploadedUrl = parsed
            }
        } catch (_: Exception) {
            uploadedUrl = null
        }
        uploadedUrl
    }

    /**
     * Product images may be stored as API-relative paths so the same row works
     * against localhost, a preview host and production. Coil needs an absolute
     * URL, so prefix anything that is not already one.
     */
    fun absoluteMediaUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (url.startsWith("/api/v1/")) return BASE_URL.removeSuffix("/api/v1") + url
        return url
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        phone: String = "",
        role: String = "buyer",
        storeName: String = "",
    ): AuthResult? = try {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("displayName", displayName)
            .put("phone", phone)
            .put("role", role)
            .put("storeName", storeName)
        val r = call("/auth/register", "POST", body, auth = false)
        AuthResult(
            token = r.optString("token"),
            user = CurrentUserPayload.fromJson(r.optJSONObject("user") ?: JSONObject()),
        )
    } catch (e: Exception) {
        null
    }

    /**
     * Email/password login. [identifier] is whatever the user typed in the
     * single "Email or Phone Number" field: the backend resolves an
     * email address or a registered phone number to the same account.
     */
    suspend fun login(identifier: String, password: String): AuthResult? = try {
        val r = call("/auth/login", "POST", JSONObject().put("email", identifier).put("password", password), auth = false)
        AuthResult(
            token = r.optString("token"),
            user = CurrentUserPayload.fromJson(r.optJSONObject("user") ?: JSONObject()),
        )
    } catch (e: Exception) {
        null
    }

    /**
     * Request a password-reset link.
     *
     * The API endpoint (`POST /auth/forgot-password`) is not deployed yet;
     * until it is, this returns false and the screen shows its
     * "not live yet" note instead of faking a success. Once the backend
     * ships the route (responding `{ "ok": true }`), this method works
     * unchanged.
     */
    suspend fun requestPasswordReset(identifier: String): Boolean = try {
        val r = call("/auth/forgot-password", "POST", JSONObject().put("identifier", identifier), auth = false)
        r.optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    suspend fun signInWithFirebase(
        idToken: String,
        displayName: String = "",
        phone: String = "",
        role: String = "buyer",
    ): AuthResult? = try {
        // The profile fields are applied by the backend only when the row is
        // first created, so re-sending them on a later sign-in is harmless.
        val body = JSONObject().put("idToken", idToken)
        if (displayName.isNotBlank()) body.put("displayName", displayName)
        if (phone.isNotBlank()) body.put("phone", phone)
        if (role == "seller") body.put("role", "seller")
        val r = call("/auth/firebase/sign-in", "POST", body, auth = false)
        AuthResult(
            token = r.optString("token"),
            user = CurrentUserPayload.fromJson(r.optJSONObject("user") ?: JSONObject()),
        )
    } catch (e: Exception) {
        null
    }

    suspend fun signInWithGoogle(idToken: String): AuthResult? = try {
        val r = call("/auth/google", "POST", JSONObject().put("idToken", idToken), auth = false)
        AuthResult(
            token = r.optString("token"),
            user = CurrentUserPayload.fromJson(r.optJSONObject("user") ?: JSONObject()),
        )
    } catch (e: Exception) {
        null
    }

    suspend fun sendFirebaseVerificationEmail(idToken: String): Boolean = try {
        call("/auth/firebase/send-verification-email", "POST", JSONObject().put("idToken", idToken), auth = false)
            .optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    suspend fun fetchFirebaseMe(): CurrentUserPayload? = try {
        val r = call("/auth/firebase/me")
        CurrentUserPayload.fromJson(r)
    } catch (e: Exception) {
        null
    }

    suspend fun upgradeToSeller(idToken: String): AuthResult? = try {
        val r = call("/auth/firebase/upgrade-to-seller", "POST", JSONObject().put("idToken", idToken))
        AuthResult(
            token = r.optString("token"),
            user = CurrentUserPayload.fromJson(r.optJSONObject("user") ?: JSONObject()),
        )
    } catch (e: Exception) {
        null
    }

    /** Local (non-Firebase) upgrade to seller — returns a fresh seller JWT. */
    suspend fun upgradeToSellerLocal(): AuthResult? = try {
        val r = call("/auth/upgrade-to-seller", "POST")
        AuthResult(
            token = r.optString("token"),
            user = CurrentUserPayload.fromJson(r.optJSONObject("user") ?: JSONObject()),
        )
    } catch (e: Exception) {
        null
    }

    suspend fun fetchMe(): CurrentUserPayload? = try {
        val r = call("/auth/me")
        CurrentUserPayload.fromJson(r.optJSONObject("user") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    // ── Email verification ────────────────────────────────────────────────────
    //
    // The backend refuses every private route until the address is proven, so
    // an account that skips this is unusable. Both of these stay reachable
    // while unverified - they are on the server's allowlist.

    /** Result of asking for a verification email. */
    data class VerificationRequest(
        val sent: Boolean,
        val alreadyVerified: Boolean,
        /** Only present when the server has no mail transport configured. */
        val devCode: String?,
        /**
         * The verification link itself, also only present when the server
         * cannot send mail. Verification is link-only, so this is what the
         * screen shows - there is no code to type.
         */
        val devLink: String?,
    )

    /** Ask the backend to send a fresh verification code/link. */
    suspend fun requestVerification(): VerificationRequest? = try {
        val r = call("/auth/verify/request", "POST")
        VerificationRequest(
            sent = r.optBoolean("sent", false),
            alreadyVerified = r.optBoolean("alreadyVerified", false),
            devCode = r.optString("devCode").takeIf { it.isNotBlank() },
            devLink = r.optString("devLink").takeIf { it.isNotBlank() },
        )
    } catch (e: Exception) {
        null
    }

    // There is deliberately no confirmVerification() here. The server still
    // exposes /auth/verify/confirm so the backend test fixtures can verify an
    // account in one call, but the app must never offer code entry: the email
    // carries a link and nothing else. Re-adding a method here would invite a
    // screen to use it, which is how the code box came back the first time.

    suspend fun updateMe(
        displayName: String? = null,
        phone: String? = null,
        city: String? = null,
        profilePhotoUrl: String? = null,
    ): Boolean = try {
        val body = JSONObject()
        displayName?.let { body.put("displayName", it) }
        phone?.let { body.put("phone", it) }
        city?.let { body.put("city", it) }
        profilePhotoUrl?.let { body.put("profilePhotoUrl", it) }
        call("/auth/me", "PATCH", body).has("user")
    } catch (e: Exception) {
        false
    }

    // ── Products ──────────────────────────────────────────────────────────────

    suspend fun fetchProductsList(): List<Product> = try {
        val r = call("/products", auth = false)
        Product.fromJsonArray(r.optJSONArray("products") ?: JSONArray())
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Filtered/sorted product feed — the same query params the web catalogue
     * uses (`GET /products?sort=…&flashOnly=…&inStock=…&category=…`), so the
     * app's "For you / Flash / Trending" tabs show exactly what the website
     * shows.
     *
     * @param sort relevance | newest | price_asc | price_desc | rating | popular
     */
    suspend fun fetchProductsFeed(
        sort: String = "newest",
        flashOnly: Boolean = false,
        inStock: Boolean = false,
        category: String? = null,
        pageSize: Int = 24,
    ): List<Product> = try {
        val params = StringBuilder("?sort=$sort&pageSize=$pageSize")
        if (flashOnly) params.append("&flashOnly=true")
        if (inStock) params.append("&inStock=true")
        category?.takeIf { it.isNotBlank() && it != "All" }?.let {
            params.append("&category=").append(java.net.URLEncoder.encode(it, "UTF-8"))
        }
        val r = call("/products$params", auth = false)
        Product.fromJsonArray(r.optJSONArray("products") ?: JSONArray())
    } catch (e: Exception) {
        emptyList()
    }

    /** New products from sellers the buyer follows (web "Following" feed). */
    suspend fun fetchFavoritesFeed(limit: Int = 24): List<Product> = try {
        val r = call("/me/favorites/feed?limit=$limit")
        Product.fromJsonArray(r.optJSONArray("products") ?: JSONArray())
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun fetchProductById(id: String): Product? = try {
        val r = call("/products/$id", auth = false)
        Product.fromJson(r.optJSONObject("product") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    /** The signed-in seller's own inventory (requires seller JWT). */
    suspend fun fetchSellerProducts(): List<Product> = try {
        val r = call("/seller/products")
        Product.fromJsonArray(r.optJSONArray("products") ?: JSONArray())
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun createSellerProduct(payload: NewProductPayload): Product? = try {
        val body = JSONObject()
            .put("title", payload.title)
            .put("description", payload.description)
            .put("category", payload.category)
            .put("brand", payload.brand)
            .put("priceMinor", payload.priceMinor)
            .put("stockQuantity", payload.stockQuantity)
            .put("imageUrl", payload.imageUrl)
            .put("location", payload.location)
            .put("isFlashDeal", payload.isFlashDeal)
            .put("discountPercent", payload.discountPercent)
        payload.oldPriceMinor?.let { body.put("oldPriceMinor", it) }
        payload.mediaUrls.takeIf { it.isNotEmpty() }?.let { urls ->
            body.put("mediaUrls", JSONArray(urls))
        }
        val r = call("/seller/products", "POST", body)
        Product.fromJson(r.optJSONObject("product") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    suspend fun deleteSellerProduct(id: String): Boolean = try {
        call("/seller/products/$id", "DELETE").optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    // ── Seller ────────────────────────────────────────────────────────────────

    suspend fun fetchSellerProfile(): SellerProfile? = try {
        val r = call("/seller/profile")
        SellerProfile.fromJson(r.optJSONObject("seller") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    suspend fun updateSellerProfile(p: SellerProfile): Boolean = try {
        val body = JSONObject()
            .put("storeName", p.storeName)
            .put("storeDescription", p.description)
            .put("contactEmail", p.contactEmail)
            .put("contactPhone", p.contactPhone)
            .put("momoNumber", p.momoNumber)
            .put("bankName", p.bankName)
            .put("city", p.city)
            .put("address", p.address)
        call("/seller/store-settings", "PATCH", body).has("settings")
    } catch (e: Exception) {
        false
    }

    suspend fun fetchStoreSettings(): StoreSettings? = try {
        val r = call("/seller/store-settings")
        StoreSettings.fromJson(r.optJSONObject("settings") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    suspend fun updateStoreSettings(s: StoreSettings): Boolean = try {
        val body = JSONObject()
            .put("storeName", s.storeName)
            .put("storeDescription", s.storeDescription)
            .put("storeLogoUrl", s.storeLogoUrl)
            .put("legalName", s.legalName)
            .put("tin", s.tin)
            .put("businessEmail", s.businessEmail)
            .put("businessPhone", s.businessPhone)
            .put("address", s.address)
            .put("pickupInstructions", s.pickupInstructions)
            .put("serviceRadiusKm", s.serviceRadiusKm)
            .put("deliveryFeeUgx", s.deliveryFeeUgx)
            .put("freeAboveUgx", s.freeAboveUgx)
            .put("codEnabled", s.codEnabled)
            .put("momoNumber", s.momoNumber)
            .put("bankName", s.bankName)
            .put("bankAccount", s.bankAccount)
            .put("notifOrderUpdates", s.notifOrderUpdates)
            .put("notifBuyerMessages", s.notifBuyerMessages)
            .put("notifMarketing", s.notifMarketing)
            .put("notifWeeklyDigest", s.notifWeeklyDigest)
            .put("twoFactorEnabled", s.twoFactorEnabled)
            .put("returnsWindowDays", s.returnsWindowDays)
            .put("refundPolicy", s.refundPolicy)
            .put("terms", s.terms)
            .put("contactEmail", s.contactEmail)
            .put("contactPhone", s.contactPhone)
            .put("city", s.city)
        call("/seller/store-settings", "PATCH", body).has("settings")
    } catch (e: Exception) {
        false
    }

    suspend fun fetchSellerDashboardStats(): SellerDashboardStats? = try {
        val r = call("/seller/dashboard/stats")
        SellerDashboardStats.fromJson(r.optJSONObject("stats") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    /**
     * The FULL seller dashboard — stats, 14-day sales series, top products
     * and recent orders. Same payload the web seller dashboard renders.
     */
    suspend fun fetchSellerDashboard(): SellerDashboard? = try {
        SellerDashboard.fromJson(call("/seller/dashboard/stats"))
    } catch (e: Exception) {
        null
    }

    /** Seller-side order list (mirror of the buyer's /me/orders). */
    suspend fun fetchSellerOrders(): List<Order> = try {
        val arr = call("/seller/orders").optJSONArray("orders") ?: JSONArray()
        (0 until arr.length()).map { Order.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    // ── Seller live location + open state (powers the buyer's Nearby map) ────

    suspend fun fetchSellerLocation(): SellerLocationState? = try {
        SellerLocationState.fromJson(call("/seller/location").optJSONObject("location"))
    } catch (e: Exception) {
        null
    }

    /** Publish a live fix — buyers see the store move in real time. */
    suspend fun publishSellerLocation(lat: Double, lng: Double, city: String? = null): Boolean = try {
        val body = JSONObject().put("lat", lat).put("lng", lng).put("sharing", true)
        city?.takeIf { it.isNotBlank() }?.let { body.put("city", it) }
        call("/seller/location", "POST", body).has("location")
    } catch (e: Exception) {
        false
    }

    /** Live sharing off — the pin stays at the last known position. */
    suspend fun stopSellerLocation(): Boolean = try {
        call("/seller/location", "DELETE").has("location")
    } catch (e: Exception) {
        false
    }

    /** Open/closed toggle shown on the Nearby cards. */
    suspend fun setStoreOpen(isOpen: Boolean): Boolean = try {
        call("/seller/open-state", "PATCH", JSONObject().put("isOpen", isOpen))
        true
    } catch (e: Exception) {
        false
    }

    /** Move a draft/rejected listing back into the admin review queue. */
    suspend fun submitProductForReview(id: String): Boolean = try {
        call("/seller/products/$id/submit", "POST").has("product")
    } catch (e: Exception) {
        false
    }

    /** Convenience wrapper. Null [radiusKm] searches the whole marketplace. */
    suspend fun fetchNearbySellers(
        lat: Double,
        lng: Double,
        radiusKm: Int? = null,
    ): List<NearbySeller> = fetchNearby(lat, lng, radiusKm).sellers

    /**
     * Nearby stores, re-sorted by distance from the buyer's current position.
     *
     * Sellers who have not enabled location sharing keep their last known pin
     * (the API coalesces last_lat/last_lng), so a store never vanishes from the
     * map — it is simply reported as not live.
     *
     * @param sort distance | rating | products | newest
     */
    suspend fun fetchNearby(
        lat: Double,
        lng: Double,
        /**
         * Kilometres to search within. **Null means no limit** — the whole
         * marketplace, nearest first — which is the default because a buyer
         * with no store inside 50 km should still see the closest ones rather
         * than an empty screen.
         */
        radiusKm: Int? = null,
        category: String? = null,
        query: String? = null,
        verifiedOnly: Boolean = false,
        openOnly: Boolean = false,
        sort: String = "distance",
        limit: Int = 60,
    ): NearbyResult = try {
        val params = StringBuilder("?lat=$lat&lng=$lng&sort=$sort&limit=$limit")
        radiusKm?.let { params.append("&radiusKm=").append(it) }
        category?.takeIf { it.isNotBlank() }?.let {
            params.append("&category=").append(java.net.URLEncoder.encode(it, "UTF-8"))
        }
        query?.takeIf { it.isNotBlank() }?.let {
            params.append("&q=").append(java.net.URLEncoder.encode(it, "UTF-8"))
        }
        if (verifiedOnly) params.append("&verifiedOnly=true")
        if (openOnly) params.append("&openOnly=true")

        val r = call("/sellers/nearby$params", auth = false)
        val arr = r.optJSONArray("sellers") ?: JSONArray()
        NearbyResult(
            sellers = (0 until arr.length()).map { NearbySeller.fromJson(arr.getJSONObject(it)) },
            count = r.optInt("count", 0),
            total = r.optInt("total", r.optInt("count", 0)),
            liveCount = r.optInt("liveCount", 0),
            place = r.optJSONObject("place")?.let { Place.fromJson(it) },
        )
    } catch (e: Exception) {
        NearbyResult()
    }

    // ── AI search (text / image / voice) ──────────────────────────────────────

    /** Natural-language search: "cheap phone under 500k in Kampala". */
    suspend fun aiSearch(query: String, limit: Int = 24): AiSearchResult = try {
        AiSearchResult.fromJson(
            call("/ai/search", "POST", JSONObject().put("q", query).put("limit", limit), auth = false),
        )
    } catch (e: Exception) {
        AiSearchResult()
    }

    /** Image search. Pass a URL and/or on-device ML Kit labels. */
    suspend fun aiImageSearch(
        imageUrl: String? = null,
        hint: String? = null,
        labels: List<String> = emptyList(),
    ): AiSearchResult = try {
        val body = JSONObject()
        imageUrl?.let { body.put("imageUrl", it) }
        hint?.let { body.put("hint", it) }
        if (labels.isNotEmpty()) body.put("labels", JSONArray(labels))
        AiSearchResult.fromJson(call("/ai/image-search", "POST", body, auth = false))
    } catch (e: Exception) {
        AiSearchResult()
    }

    /** Voice search — the device does speech-to-text, we send the transcript. */
    suspend fun aiVoiceSearch(transcript: String): AiSearchResult = try {
        AiSearchResult.fromJson(
            call("/ai/voice-search", "POST", JSONObject().put("transcript", transcript), auth = false),
        )
    } catch (e: Exception) {
        AiSearchResult()
    }


    // ── User settings (the BIG surface) ───────────────────────────────────────

    suspend fun fetchAddresses(): List<Address> = try {
        val arr = call("/me/addresses").optJSONArray("addresses") ?: JSONArray()
        (0 until arr.length()).map { Address.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun createAddress(a: Address): Boolean = try {
        call("/me/addresses", "POST", Address.toJson(a)).has("address")
    } catch (e: Exception) {
        false
    }

    suspend fun updateAddress(id: String, a: Address): Boolean = try {
        call("/me/addresses/$id", "PATCH", Address.toJson(a)).has("address")
    } catch (e: Exception) {
        false
    }

    suspend fun deleteAddress(id: String): Boolean = try {
        call("/me/addresses/$id", "DELETE").optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    suspend fun fetchPaymentMethods(): List<PaymentMethod> = try {
        val arr = call("/me/payment-methods").optJSONArray("paymentMethods") ?: JSONArray()
        (0 until arr.length()).map { PaymentMethod.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun createPaymentMethod(p: PaymentMethod): Boolean = try {
        call("/me/payment-methods", "POST", PaymentMethod.toJson(p)).has("paymentMethod")
    } catch (e: Exception) {
        false
    }

    suspend fun deletePaymentMethod(id: String): Boolean = try {
        call("/me/payment-methods/$id", "DELETE").optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    suspend fun fetchOrders(): List<Order> = try {
        val arr = call("/me/orders").optJSONArray("orders") ?: JSONArray()
        (0 until arr.length()).map { Order.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun fetchOrder(id: String): Order? = try {
        Order.fromJson(call("/me/orders/$id").optJSONObject("order") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    // ── Cart (cash on delivery) ───────────────────────────────────────────────
    //
    // These return Result<T> rather than null because the failure *message*
    // matters here: "Only 3 left in stock" is the difference between a buyer
    // fixing their order and a buyer giving up on a silent no-op.

    suspend fun fetchCart(): Result<Cart> = runCatching {
        Cart.fromJson(call("/me/cart"))
    }

    /** Adds to the existing quantity server-side (upsert), not replace. */
    suspend fun addToCart(productId: String, quantity: Int = 1): Result<Cart> = runCatching {
        Cart.fromJson(
            call("/me/cart", "POST", JSONObject().put("productId", productId).put("quantity", quantity)),
        )
    }

    /** Sets an absolute quantity. Zero or less removes the line. */
    suspend fun setCartQuantity(productId: String, quantity: Int): Result<Cart> = runCatching {
        Cart.fromJson(
            call("/me/cart/$productId", "PATCH", JSONObject().put("quantity", quantity)),
        )
    }

    suspend fun removeFromCart(productId: String): Result<Cart> = runCatching {
        Cart.fromJson(call("/me/cart/$productId", "DELETE"))
    }

    suspend fun clearCart(): Result<Cart> = runCatching {
        Cart.fromJson(call("/me/cart", "DELETE"))
    }

    /**
     * Cash-on-delivery checkout: one order per cart line, cart emptied, stock
     * decremented. Use this rather than [checkout], which needs Nylon Pay
     * credentials and returns 503 until they are configured.
     */
    suspend fun checkoutCart(
        addressId: String? = null,
        phone: String = "",
        note: String = "",
    ): Result<CartCheckoutResult> = runCatching {
        val body = JSONObject()
        addressId?.takeIf { it.isNotBlank() }?.let { body.put("addressId", it) }
        if (phone.isNotBlank()) body.put("phone", phone)
        if (note.isNotBlank()) body.put("note", note)
        CartCheckoutResult.fromJson(call("/me/cart/checkout", "POST", body))
    }

    /**
     * Create an order + hosted Nylon Pay payment link. Returns null on failure.
     *
     * Currently unused by the UI: POST /orders/checkout answers 503 until Nylon
     * Pay credentials are configured on the backend, so the buy path is the
     * cash-on-delivery cart ([checkoutCart]). Kept for when payments go live.
     */
    suspend fun checkout(productId: String, quantity: Int = 1, buyerPhone: String = ""): CheckoutResult? = try {
        val body = JSONObject()
            .put("productId", productId)
            .put("quantity", quantity)
            .put("buyerPhone", buyerPhone)
        CheckoutResult.fromJson(call("/orders/checkout", "POST", body))
    } catch (e: Exception) {
        null
    }

    /** Current Nylon Pay status for an order (from the backend's getStatus mirror). */
    suspend fun fetchPaymentStatus(orderId: String): String? = try {
        call("/orders/$orderId/payment-status").optJSONObject("order")?.optString("status")
    } catch (e: Exception) {
        null
    }

    suspend fun fetchRefunds(): List<Refund> = try {
        val arr = call("/me/refunds").optJSONArray("refunds") ?: JSONArray()
        (0 until arr.length()).map { Refund.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun createRefund(orderId: String, reason: String): Refund? = try {
        val r = call("/me/refunds", "POST", JSONObject().put("orderId", orderId).put("reason", reason))
        Refund.fromJson(r.optJSONObject("refund") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    suspend fun fetchBookmarks(): List<Product> = try {
        val arr = call("/me/bookmarks").optJSONArray("products") ?: JSONArray()
        (0 until arr.length()).map { Product.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun toggleBookmark(productId: String): Boolean = try {
        call("/me/bookmarks/toggle", "POST", JSONObject().put("productId", productId))
            .optBoolean("bookmarked", false)
    } catch (e: Exception) {
        false
    }

    suspend fun fetchNotifications(): List<AppNotification> = try {
        val arr = call("/me/notifications").optJSONArray("notifications") ?: JSONArray()
        (0 until arr.length()).map { AppNotification.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun markNotificationRead(id: String): Boolean = try {
        call("/me/notifications/$id/read", "PATCH").optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    suspend fun markAllNotificationsRead(): Boolean = try {
        call("/me/notifications/read-all", "POST").optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    suspend fun fetchUnreadNotificationCount(): Int = try {
        call("/me/notifications/unread-count").optInt("unread", 0)
    } catch (e: Exception) {
        0
    }

    suspend fun fetchSupportTickets(): List<SupportTicket> = try {
        val arr = call("/me/support/tickets").optJSONArray("tickets") ?: JSONArray()
        (0 until arr.length()).map { SupportTicket.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun createSupportTicket(subject: String, message: String): SupportTicket? = try {
        val r = call("/me/support/tickets", "POST", JSONObject().put("subject", subject).put("message", message))
        SupportTicket.fromJson(r.optJSONObject("ticket") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    suspend fun fetchFaqs(): List<Faq> = try {
        val arr = call("/me/faqs", auth = false).optJSONArray("faqs") ?: JSONArray()
        (0 until arr.length()).map { Faq.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun fetchUserSettings(): UserSettings = try {
        val r = call("/me/preferences")
        UserSettings.fromJson(r.optJSONObject("preferences") ?: JSONObject())
    } catch (e: Exception) {
        UserSettings()
    }

    suspend fun saveSettings(s: UserSettings): Boolean = try {
        val body = JSONObject()
            .put("theme", s.theme)
            .put("language", s.language)
            .put("currency", s.currency)
            .put("notifyOrderUpdates", s.notifyOrderUpdates)
            .put("notifyMessages", s.notifyMessages)
            .put("notifyMarketing", s.notifyMarketing)
        call("/me/preferences", "PATCH", body).has("preferences")
    } catch (e: Exception) {
        false
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Boolean = try {
        call("/me/change-password", "POST", JSONObject().put("oldPassword", oldPassword).put("newPassword", newPassword))
            .optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    // ── AI ────────────────────────────────────────────────────────────────────

    suspend fun ask(prompt: String, screen: String): AiReply? = try {
        val r = call(
            "/ai/v2/ask",
            "POST",
            JSONObject().put("prompt", prompt).put("screen", screen),
            auth = false,
        )
        AiReply.fromJson(r)
    } catch (e: Exception) {
        null
    }

    suspend fun generateProductFromImage(imageUrl: String, hint: String): AiReply? = try {
        val r = call(
            "/ai/v2/generate-product",
            "POST",
            JSONObject().put("imageUrl", imageUrl).put("hint", hint),
            auth = false,
        )
        AiReply(text = r.optString("title"), provider = "heuristic")
    } catch (e: Exception) {
        null
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    suspend fun fetchConversations(): List<Conversation> = fetchInbox().conversations

    /**
     * Inbox with filter counts.
     *
     * @param filter one of all | unread | pinned | archived | offers
     * @param query  free-text match on counterparty, product or last message
     */
    suspend fun fetchInbox(filter: String = "all", query: String = ""): Inbox = try {
        val params = buildList {
            if (filter.isNotBlank() && filter != "all") add("filter=$filter")
            if (query.isNotBlank()) add("q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        }
        val suffix = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        val r = call("/conversations$suffix")
        val arr = r.optJSONArray("conversations") ?: JSONArray()
        Inbox(
            conversations = (0 until arr.length()).map { Conversation.fromJson(arr.getJSONObject(it)) },
            counts = InboxCounts.fromJson(r.optJSONObject("counts") ?: JSONObject()),
            totalUnread = r.optInt("totalUnread", 0),
        )
    } catch (e: Exception) {
        Inbox()
    }

    /** Thread header: counterparty, product context, pin/mute flags, typing. */
    suspend fun fetchConversation(conversationId: String): Conversation? = try {
        val o = call("/conversations/$conversationId").optJSONObject("conversation")
        if (o == null) null else Conversation.fromJson(o)
    } catch (e: Exception) {
        null
    }

    suspend fun openConversation(sellerId: String, productId: String? = null): String? = try {
        val body = JSONObject().put("sellerId", sellerId)
        productId?.let { body.put("productId", it) }
        call("/conversations", "POST", body).optJSONObject("conversation")?.optString("id")
    } catch (e: Exception) {
        null
    }

    suspend fun fetchMessages(conversationId: String): List<ChatMessage> =
        fetchTranscript(conversationId).messages

    /** Transcript plus the other party's live typing flag. */
    suspend fun fetchTranscript(conversationId: String): Transcript = try {
        val r = call("/conversations/$conversationId/messages")
        val arr = r.optJSONArray("messages") ?: JSONArray()
        Transcript(
            messages = (0 until arr.length()).map { ChatMessage.fromJson(arr.getJSONObject(it)) },
            otherTyping = r.optBoolean("otherTyping", false),
        )
    } catch (e: Exception) {
        Transcript()
    }

    suspend fun sendMessage(conversationId: String, text: String): ChatMessage? = try {
        val r = call("/conversations/$conversationId/messages", "POST", JSONObject().put("text", text))
        ChatMessage.fromJson(r.optJSONObject("message") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    /** Share a photo in the thread. */
    suspend fun sendImageMessage(
        conversationId: String,
        imageUrl: String,
        attachmentName: String? = null,
    ): ChatMessage? = try {
        val body = JSONObject()
            .put("kind", "image")
            .put("imageUrl", imageUrl)
        attachmentName?.let { body.put("attachmentName", it) }
        val r = call("/conversations/$conversationId/messages", "POST", body)
        ChatMessage.fromJson(r.optJSONObject("message") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    /**
     * Make a price offer. The thread's product is used when [productId] is null.
     * Only the recipient can accept/decline it.
     */
    suspend fun sendOffer(
        conversationId: String,
        offerMinor: Long,
        quantity: Int = 1,
        productId: String? = null,
        note: String? = null,
    ): ChatMessage? = try {
        val body = JSONObject()
            .put("kind", "offer")
            .put("offerMinor", offerMinor)
            .put("offerQuantity", quantity)
        productId?.let { body.put("productId", it) }
        note?.takeIf { it.isNotBlank() }?.let { body.put("text", it) }
        val r = call("/conversations/$conversationId/messages", "POST", body)
        ChatMessage.fromJson(r.optJSONObject("message") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    /** @param action accept | decline | withdraw */
    suspend fun respondToOffer(
        conversationId: String,
        messageId: String,
        action: String,
    ): Boolean = try {
        call(
            "/conversations/$conversationId/offers/$messageId",
            "POST",
            JSONObject().put("action", action),
        ).optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    /** Retract one of your own messages (soft delete — the row is kept). */
    suspend fun deleteMessage(conversationId: String, messageId: String): Boolean = try {
        call("/conversations/$conversationId/messages/$messageId", "DELETE").optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    /** Typing heartbeat; the server expires it after ~6 seconds. */
    suspend fun setTyping(conversationId: String, typing: Boolean): Boolean = try {
        call(
            "/conversations/$conversationId/typing",
            "POST",
            JSONObject().put("typing", typing),
        ).optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    /** Pin / archive / mute a thread for the current user. */
    suspend fun setConversationState(
        conversationId: String,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        muted: Boolean? = null,
    ): Boolean = try {
        val body = JSONObject()
        pinned?.let { body.put("pinned", it) }
        archived?.let { body.put("archived", it) }
        muted?.let { body.put("muted", it) }
        call("/conversations/$conversationId/state", "PATCH", body).has("state")
    } catch (e: Exception) {
        false
    }

    // ── Push device tokens ────────────────────────────────────────────────────

    /**
     * Register this device's FCM token so the backend can push to it.
     * Called after sign-in and whenever FCM rotates the token.
     */
    suspend fun registerDevice(token: String, platform: String = "android"): Boolean = try {
        call(
            "/me/devices",
            "POST",
            JSONObject().put("token", token).put("platform", platform),
        ).optBoolean("ok", true)
    } catch (e: Exception) {
        false
    }

    /** Drop this device's token — called on sign-out. */
    suspend fun unregisterDevice(token: String): Boolean = try {
        call("/me/devices", "DELETE", JSONObject().put("token", token)).optBoolean("ok", true)
    } catch (e: Exception) {
        false
    }

    // ── Saved quick replies ───────────────────────────────────────────────────

    suspend fun fetchQuickReplies(): List<QuickReplyItem> = try {
        val arr = call("/me/quick-replies").optJSONArray("quickReplies") ?: JSONArray()
        (0 until arr.length()).map { QuickReplyItem.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun addQuickReply(text: String): QuickReplyItem? = try {
        val r = call("/me/quick-replies", "POST", JSONObject().put("text", text))
        val o = r.optJSONObject("quickReply")
        if (o == null) null else QuickReplyItem.fromJson(o)
    } catch (e: Exception) {
        null
    }

    suspend fun deleteQuickReply(id: String): Boolean = try {
        call("/me/quick-replies/$id", "DELETE").optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    suspend fun markConversationRead(conversationId: String): Boolean = try {
        call("/conversations/$conversationId/read", "POST").optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    // ── CMS ───────────────────────────────────────────────────────────────────

    suspend fun fetchCmsPage(slug: String): CmsPage? = try {
        val r = call("/cms/$slug", auth = false)
        CmsPage.fromJson(r.optJSONObject("page") ?: JSONObject())
    } catch (e: Exception) {
        null
    }
}
