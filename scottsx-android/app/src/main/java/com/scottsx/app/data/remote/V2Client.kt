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

    // Default backend base URL — the REAL production API every shipped APK
    // must reach: the same Render origin the web client falls back to
    // (web/src/api/client.ts FALLBACK_API). The origin is BAKED IN at
    // build time from -PapiBaseUrl (see app/build.gradle.kts) so the
    // release workflow's URL choice actually reaches the shipped APK —
    // previously the property was ignored and every build silently used
    // this hardcoded value. Route paths below carry the /api/v1 prefix
    // themselves, so the baked origin omits it. Dev loops override with
    // setBaseUrl("http://127.0.0.1:3001"); on a real phone the old
    // localhost default looped requests back to the phone itself — the
    // live "can't reach the marketplace" failure on the home feed.
    private val DEFAULT_BASE_URL: String = com.scottsx.app.BuildConfig.API_BASE_URL
    @Volatile private var baseUrlOverride: String? = null
    fun setBaseUrl(url: String) { baseUrlOverride = url }

    /** Base URL — honours [setBaseUrl]; defaults to the production origin. */
    private val baseUrl: String get() = baseUrlOverride?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL

    /** Public accessor for sibling clients (RemoteAssistantClient). */
    fun currentBaseUrl(): String = baseUrl

    /**
     * The backend stores some media as API-relative paths
     * ("/api/v1/uploads/images/<key>" — the DB-backed image store) and the
     * web resolves those against the site origin. Coil cannot load a
     * scheme-less path, so every media URL parsed from the API is pushed
     * through here: absolute URLs pass unchanged, relative ones get the
     * current base origin prepended. This is the fix for products whose
     * photos never rendered in the app.
     */
    fun absoluteMediaUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (!url.startsWith("/")) return "https://$url"
        val origin = currentBaseUrl().let { b ->
            val i = b.indexOf("/", "https://".length)
            if (i > 0) b.substring(0, i) else b
        }
        return origin + url
    }

    /**
     * REAL image upload: multipart POST /api/v1/uploads/images (field
     * "image"), the only upload route the canonical backend serves.
     * Returns the absolute public URL to store on the product — either a
     * GCS link or the API-relative path absolutized by [absoluteMediaUrl].
     */
    suspend fun uploadImage(bytes: ByteArray, mime: String, filename: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val boundary = "----scottsx${System.nanoTime()}"
                val crlf = "\r\n"
                val url = java.net.URL(baseUrl.trimEnd('/') + "/api/v1/uploads/images")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 30000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                Session.tokenOrNull()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                val out = java.io.DataOutputStream(conn.outputStream)
                out.writeBytes("--$boundary$crlf")
                out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"$filename\"$crlf")
                out.writeBytes("Content-Type: $mime$crlf$crlf")
                out.write(bytes)
                out.writeBytes("$crlf--$boundary--$crlf")
                out.flush()
                out.close()
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                if (code !in 200..299) return@withContext null
                absoluteMediaUrl(JSONObject(text).optString("url").takeIf { it.isNotBlank() })
            } catch (_: Throwable) { null }
        }

    private suspend fun <T> apiCall(
        method: String,
        path: String,
        body: JSONObject? = null,
        parse: (JSONObject) -> T,
    ): T? {
        // GETs are idempotent: give them the wide cold-server lane with
        // one automatic retry so dashboards "just work" while the API
        // wakes from idle sleep. Mutating calls keep the tight budget
        // and never replay.
        val wideLane = method == "GET" || path == "/api/v1/auth/firebase/sign-in"
        if (wideLane) {
            return apiCallInternal(method, path, body, parse, 30000, 45000)
                ?: run {
                    kotlinx.coroutines.delay(1200)
                    apiCallInternal(method, path, body, parse, 30000, 45000)
                }
        }
        return apiCallInternal(method, path, body, parse, 6000, 12000)
    }

    private suspend fun <T> apiCallInternal(
        method: String,
        path: String,
        body: JSONObject? = null,
        parse: (JSONObject) -> T,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): T? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(baseUrl.trimEnd('/') + path)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
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
            parse = { o -> parseGoogleSignInResult(o) },
        )

    /**
     * Exchange a FIREBASE ID token (from `FirebaseUser.getIdToken`) for a
     * ScottsTechX session via POST /api/v1/auth/firebase/sign-in.
     *
     * This is the bridge every sign-in path uses after Firebase Auth
     * succeeds — email/password, Google-via-Firebase, and cold-start
     * session restore — so the app ALWAYS holds a valid backend JWT and
     * every /api/v1 call goes out authenticated. Without this exchange
     * the bearer token was null and every authenticated feature
     * (dashboard, messaging, notifications, receipts) silently 401'd.
     *
     * The optional profile fields are only applied when the backend
     * creates the user row for the first time; later calls cannot
     * overwrite an existing profile.
     */
    suspend fun signInWithFirebase(
        idToken: String,
        displayName: String? = null,
        phone: String? = null,
        role: String? = null,
        storeName: String? = null,
    ): GoogleSignInResult? =
        apiCall<GoogleSignInResult?>(
            method = "POST",
            path = "/api/v1/auth/firebase/sign-in",
            body = JSONObject()
                .put("idToken", idToken)
                .put("displayName", displayName?.takeIf { it.isNotBlank() })
                .put("phone", phone?.takeIf { it.isNotBlank() })
                .put("role", role?.takeIf { it == "buyer" || it == "seller" })
                .put("storeName", storeName?.takeIf { it.isNotBlank() }),
            parse = { o -> parseGoogleSignInResult(o) },
        )

    private fun parseGoogleSignInResult(o: JSONObject): GoogleSignInResult? {
        val token = o.optString("token")
        val u = o.optJSONObject("user")
        if (token.isBlank() || u == null) return null
        return GoogleSignInResult(
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
        flashOnly: Boolean = false,
        sort: String? = null,   // relevance|newest|price_asc|price_desc|rating|popular
    ): List<com.scottsx.app.data.domain.Product>? {
        val qs = buildString {
            append("?pageSize=").append(pageSize)
            if (!query.isNullOrBlank()) append("&q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
            if (!category.isNullOrBlank()) append("&category=").append(java.net.URLEncoder.encode(category, "UTF-8"))
            if (flashOnly) append("&flashOnly=1")
            if (!sort.isNullOrBlank()) append("&sort=").append(sort)
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
        // Moderation state; public catalog rows are approved-by-definition,
        // seller rows carry the real state.
        val status = o.optString("status").ifBlank { "approved" }
        val oldPriceRaw = o.optLong("oldPriceMinor", 0L)
        val oldPriceUgx = if (oldPriceRaw > 0L && oldPriceRaw > priceUgx) oldPriceRaw else null
        val imageUrl = absoluteMediaUrl(o.optString("imageUrl").takeIf { it.isNotBlank() }) ?: ""
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
            images = parseProductImages(o, imageUrl, title),
            status = status,
            rejectionReason = o.optString("rejectionReason").takeIf { it.isNotBlank() },
        )
    }

    /** Full media gallery from the backend's mediaUrls/media arrays — the PDP
     *  gallery used to show only the primary photo no matter how many the
     *  seller uploaded. Every entry runs through [absoluteMediaUrl]. */
    private fun parseProductImages(
        o: org.json.JSONObject,
        fallbackUrl: String,
        alt: String,
    ): List<com.scottsx.app.data.domain.ProductImage> {
        val productId = o.optString("id")
        fun urlAt(arr: org.json.JSONArray, i: Int): String? {
            val any = arr.opt(i) ?: return null
            val raw: String? = when (any) {
                is String -> any
                is org.json.JSONObject -> any.optString("url")
                else -> null
            }
            return raw?.takeIf { it.isNotBlank() }
        }
        val urls = mutableListOf<String>()
        for (key in listOf("mediaUrls", "media", "images")) {
            val arr = o.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) urlAt(arr, i)?.let { urls += it }
            if (urls.isNotEmpty()) break
        }
        if (urls.isEmpty() && fallbackUrl.isNotBlank()) urls += fallbackUrl
        return urls.distinct().mapIndexed { i, u ->
            com.scottsx.app.data.domain.ProductImage(
                id = "$productId-img-$i",
                url = absoluteMediaUrl(u) ?: "",
                alt = alt,
            )
        }
    }

    // ============================================================
    // EMAIL / PASSWORD AUTH (backend-native — THE web accounts)
    //
    // These hit /api/v1/auth/* directly — the SAME credential store the
    // website uses. A shopper who registered on the web with email +
    // password can sign in here byte-for-byte; Firebase is never asked.
    // ============================================================

    enum class EmailLoginStatus { SUCCESS, INVALID_CREDENTIALS, DISABLED, NETWORK }

    /** Outcome of POST /auth/login — token+user on success, reason otherwise. */
    data class EmailLoginResult(
        val status: EmailLoginStatus,
        val token: String? = null,
        val user: GoogleUser? = null,
    )

    /** POST /api/v1/auth/login {email, password} — identifier may be a phone. */
    suspend fun loginEmail(identifier: String, password: String): EmailLoginResult {
        val body = JSONObject().put("email", identifier).put("password", password)
        val (code, json) = rawPost("/api/v1/auth/login", body)
            ?: return EmailLoginResult(EmailLoginStatus.NETWORK)
        if (code in 200..299) {
            val token = json.optString("token")
            val u = parseUserJson(json.optJSONObject("user"))
            if (token.isNotBlank() && u != null) {
                return EmailLoginResult(EmailLoginStatus.SUCCESS, token, u)
            }
        }
        val msg = (json.optString("message") + " " + json.optString("error")).lowercase()
        return when {
            code == 403 || msg.contains("disabled") || msg.contains("deactivat") ->
                EmailLoginResult(EmailLoginStatus.DISABLED)
            code == 401 || msg.contains("invalid") ->
                EmailLoginResult(EmailLoginStatus.INVALID_CREDENTIALS)
            else -> EmailLoginResult(EmailLoginStatus.NETWORK)
        }
    }

    /** Outcome of POST /auth/register. */
    data class EmailRegisterResult(
        val ok: Boolean,
        val token: String? = null,
        val user: GoogleUser? = null,
        /** True when the email already exists — the UI should offer "Sign in instead". */
        val emailTaken: Boolean = false,
        /** Server could not send mail (misconfigured) — surface the message. */
        val serviceMessage: String? = null,
    )

    /** POST /api/v1/auth/register — creates the web-compatible account AND
     * returns a live JWT, so the app is signed in immediately while the
     * verification email flies. */
    suspend fun registerEmail(
        email: String,
        password: String,
        displayName: String,
        phone: String,
        role: String,
        storeName: String? = null,
        city: String? = null,
    ): EmailRegisterResult {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("displayName", displayName)
            .put("phone", phone)
            .put("role", role)
            .put("storeName", storeName ?: "")
            .put("city", city ?: "")
        val (code, json) = rawPost("/api/v1/auth/register", body)
            ?: return EmailRegisterResult(ok = false, serviceMessage = "No connection. Try again.")
        if (code in 200..299) {
            val token = json.optString("token")
            val u = parseUserJson(json.optJSONObject("user"))
            if (token.isNotBlank() && u != null) {
                return EmailRegisterResult(ok = true, token = token, user = u)
            }
        }
        val msg = json.optString("message")
            .ifBlank { json.optString("error") }
        return if (code == 409 || msg.contains("already", ignoreCase = true)) {
            EmailRegisterResult(ok = false, emailTaken = true, serviceMessage = msg.ifBlank { "Email already registered" })
        } else {
            EmailRegisterResult(ok = false, serviceMessage = msg.ifBlank { "Could not create the account right now. Try again." })
        }
    }

    /** POST /api/v1/auth/forgot-password {identifier} — mails a reset
     * token (single use, 30 min). Always {ok:true} server-side. */
    suspend fun forgotPassword(identifier: String): Boolean {
        val pair = rawPost(
            "/api/v1/auth/forgot-password",
            JSONObject().put("identifier", identifier),
        ) ?: return false
        return pair.second.optBoolean("ok", true)
    }

    /** POST /api/v1/auth/reset-password {token, password} — redeems the
     * emailed token natively (no browser round-trip). */
    suspend fun resetPasswordWithToken(token: String, newPassword: String): Pair<Boolean, String?> {
        val pair = rawPost(
            "/api/v1/auth/reset-password",
            JSONObject().put("token", token).put("password", newPassword),
        ) ?: return false to "No connection. Try again."
        val (code, json) = pair
        if (code in 200..299 && json.optBoolean("ok", false)) return true to null
        val msg = json.optString("message").ifBlank { json.optString("error") }
        return false to msg.ifBlank { "That reset link is invalid or expired — request a new one." }
    }

    /** POST /api/v1/auth/verify/request (auth) — resend the verification
     * email (the one containing the LINK). */
    suspend fun requestVerificationCode(): Boolean {
        val (code, json) = rawPost("/api/v1/auth/verify/request", JSONObject())
            ?: return false
        return code in 200..299 && (json.optBoolean("sent", false) || json.optBoolean("alreadyVerified", true))
    }

    /** POST /api/v1/auth/verify/confirm {code} (auth) → verified. */
    suspend fun confirmVerificationCode(code: String): Boolean {
        val (httpCode, json) = rawPost(
            "/api/v1/auth/verify/confirm",
            JSONObject().put("code", code),
        ) ?: return false
        return httpCode in 200..299 && json.optBoolean("verified", false)
    }

    /**
     * GET /api/v1/auth/me (auth) — the same poll the website's
     * verification page runs: "has the emailed verification LINK been
     * tapped yet?" Returns null when the call fails (the poll simply
     * keeps going) and false while the address is still unverified.
     *
     * The verification email contains a LINK, not a typed code — the
     * user taps it in their mail app (which verifies the account on
     * the shared backend) and this screen detects the flip.
     */
    suspend fun fetchEmailVerified(): Boolean? {
        val me = fetchUserProfile() ?: return null
        return me.optBoolean("emailVerified", false)
    }

    /**
     * Fire-and-forget nudge that wakes the production API. The server
     * sleeps when idle (free tier) and its wake-up burns 20-60 s —
     * long enough for a sign-in to look frozen. Issuing this cheap
     * public GET the moment an auth screen appears means the server is
     * already awake by the time the user taps the button. The response
     * is irrelevant; the wake is the point.
     */
    suspend fun wakeServer() {
        runCatching {
            withContext(Dispatchers.IO) {
                val url = java.net.URL(baseUrl.trimEnd('/') + "/api/v1/products?page=1&limit=1")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/json")
                runCatching { conn.responseCode }
                conn.disconnect()
            }
        }
    }

    /** Minimal POST that returns (HTTP status, parsed body) so auth
     * surfaces can read backend error messages instead of just null. */
    /**
     * Cold-server-safe transport. The production API sleeps when idle
     * (Render free tier) and its wake-up interstitial burns 20-60 s, so
     * the old 6 s budget turned a waking server into "everything is
     * broken" across the app. Auth-critical routes ride wider budgets
     * with one retry; transport failures only — HTTP responses (incl.
     * errors) never replay, so idempotency stays intact.
     */
    private suspend fun rawPost(path: String, body: JSONObject): Pair<Int, JSONObject>? {
        val authCritical = path.startsWith("/api/v1/auth/login") ||
            path.startsWith("/api/v1/auth/register") ||
            path.startsWith("/api/v1/auth/verify/request")
        if (!authCritical) return rawPostInternal(path, body, 6000, 12000)
        return rawPostInternal(path, body, 30000, 45000)
            ?: run { kotlinx.coroutines.delay(1200); rawPostInternal(path, body, 30000, 45000) }
    }

    private suspend fun rawPostInternal(
        path: String,
        body: JSONObject,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): Pair<Int, JSONObject>? =
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL(baseUrl.trimEnd('/') + path)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.setRequestProperty("Accept", "application/json")
                Session.tokenOrNull()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                code to (if (text.isBlank()) JSONObject() else JSONObject(text))
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "POST $path failed: ${t.message}")
                null
            }
        }

    /** Parse a backend `users` row (camelCase publicUser shape). */
    private fun parseUserJson(u: JSONObject?): GoogleUser? {
        if (u == null) return null
        val id = u.optString("id")
        if (id.isBlank()) return null
        return GoogleUser(
            id = id,
            email = u.optString("email"),
            displayName = u.optString("displayName"),
            phone = u.optString("phone"),
            role = u.optString("role", "buyer"),
            emailVerified = u.optBoolean("emailVerified", false),
            profilePhotoUrl = u.optString("profilePhotoUrl")
                .takeIf { it.isNotBlank() && it != "null" },
            city = u.optString("city"),
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
        // The backend wraps in { settings: {...} } — unwrap so callers
        // read fields directly (optString("storeName") etc.).
        parse = { o -> o.optJSONObject("settings") ?: o },
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

    /**
     * Products from stores the buyer follows — the web buyer dashboard's
     * "From sellers you follow" feed tab.
     */
    suspend fun fetchFavoritesFeed(): List<com.scottsx.app.data.domain.Product>? {
        val obj = apiCall(
            method = "GET",
            path = "/api/v1/me/favorites/feed?pageSize=24",
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

    /** FAQ entries for the help center (same payload as the web). */
    suspend fun fetchFaqs(): JSONArray? = apiCall(
        method = "GET", path = "/api/v1/me/faqs", body = null,
        parse = { o -> o.optJSONArray("faqs") },
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

    // Reports — the backend has no dedicated /reports route; abuse and issue
    // reports are delivered as SUPPORT TICKETS (POST /me/support/tickets,
    // {subject, message}), which the staff inbox actually reads. Returns the
    // ticket id on success.
    suspend fun createReport(
        resourceType: String, resourceId: String, reason: String,
        description: String? = null,
    ): String? = apiCall(
        method = "POST", path = "/api/v1/me/support/tickets",
        body = JSONObject().apply {
            put("subject", "Report: $resourceType $resourceId — $reason")
            put("message", description ?: "Reported $resourceType $resourceId: $reason")
        },
        parse = { o -> o.optJSONObject("ticket")?.optString("id") },
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

    /** One grounded catalog row the assistant attached to its answer. */
    data class AiProduct(
        val id: String,
        val title: String,
        val priceMinor: Long,
        val imageUrl: String?,
        val rating: Double,
        val city: String?,
        val verified: Boolean,
        val discountPercent: Int,
    )

    /** Agent directory entry from GET /api/v1/ai/agents (starters drive the
     *  AI screen's suggestion chips — real backend content, never invented). */
    data class AiAgent(
        val id: String,
        val name: String,
        val tagline: String,
        val starters: List<String>,
    )

    data class AiAskReply(
        val text: String,
        val provider: String,
        val model: String,
        val agentId: String,
        val agentName: String,
        val agentTagline: String,
        val products: List<AiProduct>,
    )

    suspend fun fetchAiAgents(): List<AiAgent> {
        val arr = apiCall(
            method = "GET", path = "/api/v1/ai/agents", body = null,
            parse = { o -> o.optJSONArray("agents") },
        ) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            val startersArr = a.optJSONArray("starters")
            AiAgent(
                id = a.optString("id"),
                name = a.optString("name"),
                tagline = a.optString("tagline"),
                starters = if (startersArr == null) emptyList()
                else (0 until startersArr.length()).map { startersArr.optString(it) }.filter { it.isNotBlank() },
            )
        }
    }

    private fun parseAiReply(o: JSONObject): AiAskReply {
        val prodArr = o.optJSONArray("products")
        val products = mutableListOf<AiProduct>()
        if (prodArr != null) {
            for (i in 0 until prodArr.length()) {
                val p = prodArr.optJSONObject(i) ?: continue
                products += AiProduct(
                    id = p.optString("id"),
                    title = p.optString("title"),
                    priceMinor = p.optLong("priceMinor", 0L),
                    imageUrl = absoluteMediaUrl(p.optString("imageUrl").takeIf { it.isNotBlank() }),
                    rating = p.optDouble("rating", 0.0),
                    city = p.optString("city").takeIf { it.isNotBlank() },
                    verified = p.optBoolean("verified", false),
                    discountPercent = p.optInt("discountPercent", 0),
                )
            }
        }
        val agent = o.optJSONObject("agent")
        return AiAskReply(
            text = o.optString("text").ifBlank { o.optString("reply") },
            provider = o.optString("provider"),
            model = o.optString("model"),
            agentId = agent?.optString("id") ?: "",
            agentName = agent?.optString("name") ?: "",
            agentTagline = agent?.optString("tagline") ?: "",
            products = products,
        )
    }

    /**
     * The real, catalog-grounded assistant. POST /api/v1/ai/v2/ask takes
     * {prompt, screen, agent?, history:[{role,content}]} — the backend does
     * its OWN live retrieval, so callers no longer ship a catalog blob.
     * Returns the reply text plus provider/metadata and the grounded
     * products the web chat shows as cards.
     */
    suspend fun askV2(
        prompt: String,
        screen: String? = null,
        agent: String? = null,
        history: List<Pair<String, String>> = emptyList(),
    ): AiAskReply? = apiCall(
        method = "POST",
        path = "/api/v1/ai/v2/ask",
        body = JSONObject().apply {
            put("prompt", prompt)
            if (screen != null) put("screen", screen)
            if (agent != null) put("agent", agent)
            if (history.isNotEmpty()) {
                put("history", org.json.JSONArray().apply {
                    history.takeLast(10).forEach { (r, c) ->
                        put(JSONObject().apply {
                            put("role", r)
                            put("content", c.take(800))
                        })
                    }
                })
            }
        },
        parse = { o -> parseAiReply(o) },
    )

    suspend fun ask(message: String, screen: String? = null): AiReply? =
        apiCall(
            method = "POST",
            path = "/api/v1/ai/v2/ask",
            body = JSONObject().apply {
                // Backend contract: {prompt, screen, agent?, history} →
                // {text, provider, model, agent, products, grounded}.
                put("prompt", message)
                if (screen != null) put("screen", screen)
            },
            parse = { o ->
                AiReply(
                    text = o.optString("text").ifBlank { o.optString("reply") },
                    provider = o.optString("provider"),
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
        /** Server-side notifyMarketing — powers the Promotions toggle + web parity. */
        val notifyMarketing: Boolean = false,
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
                    notifyMarketing = p.optBoolean("notifyMarketing", false),
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

    /** Full location object (sharing state + last fix) for the store controls. */
    data class SellerLocationState(
        val isOpen: Boolean,
        val sharing: Boolean,
        val updatedAt: String?,
    )

    /**
     * `GET /api/v1/seller/location` → `{ location: { lat, lng, sharing,
     * isOpen, updatedAt } | null }` — state behind the web dashboard's
     * "Live location" control.
     */
    suspend fun fetchSellerLocationState(): SellerLocationState? =
        apiCall(
            method = "GET",
            path = "/api/v1/seller/location",
            body = null,
            parse = { o ->
                o.optJSONObject("location")?.let { loc ->
                    SellerLocationState(
                        isOpen = loc.optBoolean("isOpen", true),
                        sharing = loc.optBoolean("sharing", false),
                        updatedAt = loc.optString("updatedAt").ifBlank { null },
                    )
                }
            },
        )

    /** DELETE /api/v1/seller/location — stop live sharing (pin stays put). */
    suspend fun stopSellerLocationSharing(): Boolean =
        apiCall(
            method = "DELETE",
            path = "/api/v1/seller/location",
            body = null,
            parse = { true },
        ) ?: false

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
        val products: List<NearbyProduct> = emptyList(),
        // ── Web-contract fields (GET /api/v1/sellers/nearby rows) ──────
        val verified: Boolean = false,
        val logoUrl: String? = null,
        val live: Boolean = false,
        val locationSharing: Boolean = false,
        val locationAgeMinutes: Int? = null,
        val isOpen: Boolean = true,
        val productCount: Int = 0,
        val newThisWeek: Int = 0,
        val etaMinutes: Int = 0,
        val serviceRadiusKm: Int = 20,
        val deliveryFeeUgx: Long = 0,
        val freeAboveUgx: Long = 0,
        val codEnabled: Boolean = false,
        val withinServiceRadius: Boolean = false,
        /** Human place for the store's pin: "Kireka, Kampala, Central Region". */
        val placeLabel: String = "",
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

    /** Buyer position resolved to a human place (from /geo/reverse or the
     *  nearby envelope). */
    data class GeoPlace(
        val label: String,
        val village: String? = null,
        val city: String? = null,
        val region: String? = null,
        val country: String? = null,
    )

    /** Wrapper for /api/v1/sellers/nearby: rows + stats + the place. */
    data class NearbyResult(
        val sellers: List<NearbySeller>,
        val total: Int,
        val liveCount: Int,
        val place: GeoPlace?,
    )

    private fun parseGeoPlace(o: org.json.JSONObject?): GeoPlace? =
        o?.let {
            GeoPlace(
                label = it.optString("label").ifBlank { it.optString("shortLabel") },
                village = it.optString("village").takeIf { v -> v.isNotBlank() },
                city = it.optString("city").takeIf { v -> v.isNotBlank() },
                region = it.optString("region").takeIf { v -> v.isNotBlank() },
                country = it.optString("country").takeIf { v -> v.isNotBlank() },
            )
        }

    /**
     * Live "stores near me" — the same canonical call the web Nearby page
     * makes: `GET /api/v1/sellers/nearby?lat&lng[&q][&sort][&verifiedOnly]
     * [&openOnly]`. There is NO radius control (the server returns every
     * store, distance-sorted) and distances/ETA are computed server-side, so
     * results re-sort simply by re-calling as the buyer moves.
     */
    suspend fun nearbySellers(
        lat: Double,
        lng: Double,
        q: String? = null,
        sort: String = "distance",   // distance | rating | products | newest
        verifiedOnly: Boolean = false,
        openOnly: Boolean = false,
        limit: Int = 60,
        // Legacy signature kept for source compat — never sent to the server.
        radiusKm: Double? = null,
        category: String? = null,
        minPrice: Long? = null,
        maxPrice: Long? = null,
    ): NearbyResult {
        val qs = buildString {
            append("?lat=").append(lat)
            append("&lng=").append(lng)
            if (!q.isNullOrBlank()) append("&q=").append(java.net.URLEncoder.encode(q, "UTF-8"))
            if (sort != "distance") append("&sort=").append(sort)
            if (verifiedOnly) append("&verifiedOnly=true")
            if (openOnly) append("&openOnly=true")
            if (category != null && category != "All")
                append("&category=").append(java.net.URLEncoder.encode(category, "UTF-8"))
            append("&limit=").append(limit)
        }
        val obj = apiCall(
            method = "GET",
            path = "/api/v1/sellers/nearby$qs",
            body = null,
            parse = { it },
        ) ?: return NearbyResult(emptyList(), 0, 0, null)
        val arr = obj.optJSONArray("sellers")
        val sellers = mutableListOf<NearbySeller>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                sellers += NearbySeller(
                    sellerId = r.optString("id"),
                    storeName = r.optString("storeName").ifBlank { r.optString("name") },
                    lat = r.optDouble("lat", 0.0),
                    lng = r.optDouble("lng", 0.0),
                    city = r.optString("city").takeIf { it.isNotBlank() },
                    address = r.optString("address").takeIf { it.isNotBlank() },
                    rating = r.optDouble("rating", 0.0),
                    distanceKm = r.optDouble("distanceKm", Double.MAX_VALUE),
                    verified = r.optBoolean("verified", false),
                    logoUrl = absoluteMediaUrl(r.optString("logoUrl").takeIf { it.isNotBlank() }),
                    live = r.optBoolean("live", false),
                    locationSharing = r.optBoolean("locationSharing", false),
                    locationAgeMinutes = if (r.has("locationAgeMinutes") && !r.isNull("locationAgeMinutes")) r.optInt("locationAgeMinutes") else null,
                    isOpen = r.optBoolean("isOpen", true),
                    productCount = r.optInt("productCount", 0),
                    newThisWeek = r.optInt("newThisWeek", 0),
                    etaMinutes = r.optInt("etaMinutes", 0),
                    serviceRadiusKm = r.optInt("serviceRadiusKm", 20),
                    deliveryFeeUgx = r.optLong("deliveryFeeUgx", 0L),
                    freeAboveUgx = r.optLong("freeAboveUgx", 0L),
                    codEnabled = r.optBoolean("codEnabled", false),
                    withinServiceRadius = r.optBoolean("withinServiceRadius", false),
                    placeLabel = r.optString("placeLabel"),
                )
            }
        }
        // The web card relies on client-side haversine re-sort between
        // polls; dist is present server-side but we keep it authoritative.
        return NearbyResult(
            sellers = sellers,
            total = obj.optInt("total", sellers.size),
            liveCount = obj.optInt("liveCount", sellers.count { it.live }),
            place = parseGeoPlace(obj.optJSONObject("place")),
        )
    }

    /** `GET /api/v1/geo/reverse?lat=&lng=` → the buyer's named place. */
    suspend fun geoReverse(lat: Double, lng: Double): GeoPlace? = apiCall(
        method = "GET", path = "/api/v1/geo/reverse?lat=$lat&lng=$lng", body = null,
        parse = { o -> parseGeoPlace(o.optJSONObject("place")) },
    )

    /** `POST /api/v1/me/location {lat,lng,accuracyM?}` — persist the signed-in
     *  buyer's fix so the account knows where they are (web parity). */
    suspend fun saveMyLocation(lat: Double, lng: Double, accuracyM: Double? = null): GeoPlace? = apiCall(
        method = "POST", path = "/api/v1/me/location",
        body = JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            if (accuracyM != null) put("accuracyM", accuracyM)
        },
        parse = { o -> parseGeoPlace(o.optJSONObject("place")) },
    )

    /** `GET /api/v1/me/location` → the buyer's saved last position, if any. */
    data class SavedPosition(val lat: Double, val lng: Double, val place: GeoPlace?)
    suspend fun fetchMyLocation(): SavedPosition? = apiCall(
        method = "GET", path = "/api/v1/me/location", body = null,
        parse = { o ->
            val pos = o.optJSONObject("position")
                ?: o.optJSONObject("location")
            if (pos == null) null else SavedPosition(
                lat = pos.optDouble("lat", 0.0),
                lng = pos.optDouble("lng", 0.0),
                place = parseGeoPlace(o.optJSONObject("place")),
            )
        },
    )

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
            attachmentUrl = absoluteMediaUrl(m.optString("imageUrl").ifBlank { m.optString("attachmentUrl") }.takeIf { it.isNotBlank() }),
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

    /** ✓✓ read receipts + typing indicator metadata for a thread. */
    data class ThreadStatus(
        /** ISO timestamp of the other party's last read — our messages at/before show "Seen". */
        val otherLastReadAt: String?,
        /** True while the other party is composing (server TTL ~6s). */
        val otherTyping: Boolean,
    )

    /**
     * GET the whole thread AND the read/typing state in one round-trip
     * (the canonical route already returns both — fetchMessages threw the
     * status half away). The thread screen polls this to draw ✓✓ and the
     * "typing…" presence line like the web does.
     */
    suspend fun fetchMessagesWithStatus(
        conversationId: String,
        limit: Int = 200,
    ): Pair<List<ChatMessage>, ThreadStatus>? {
        val obj = apiCall(
            method = "GET",
            path = "/api/v1/conversations/$conversationId/messages",
            body = null,
            parse = { it },
        ) ?: return null
        val arr = obj.optJSONArray("messages")
        var list = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { chatMessageFromJson(it, conversationId) }
        }
        if (list.size > limit) list = list.takeLast(limit)
        fun statusStr(vararg keys: String): String? {
            for (k in keys) {
                val v = obj.optString(k)
                if (v.isNotBlank() && v != "null") return v
            }
            return null
        }
        val status = ThreadStatus(
            otherLastReadAt = statusStr("otherLastReadAt", "otherLastRead", "otherReadAt"),
            otherTyping = obj.optBoolean("otherTyping", obj.optBoolean("typing", false)),
        )
        return list to status
    }

    /** POST /conversations/:id/read — flips the other party's ticks to ✓✓. */
    suspend fun markConversationRead(conversationId: String): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/conversations/$conversationId/read",
            body = JSONObject(),
            parse = { o -> o.optBoolean("ok", true) },
        ) ?: false

    /** POST /conversations/:id/typing — the other party sees "typing…" for ~6s. */
    suspend fun postTyping(conversationId: String, typing: Boolean = true): Boolean =
        apiCall(
            method = "POST",
            path = "/api/v1/conversations/$conversationId/typing",
            body = if (typing) JSONObject() else JSONObject().put("typing", false),
            parse = { true },
        ) ?: false

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
        /** otherParty.photoUrl — the counterparty's real avatar (web parity). */
        val otherPartyPhotoUrl: String? = null,
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
                productImageUrl = absoluteMediaUrl(r.optString("productImageUrl").takeIf { it.isNotBlank() }),
                lastMessagePreview = r.optString("lastMessage").takeIf { it.isNotBlank() },
                lastMessageAt = r.optString("lastTime").takeIf { it.isNotBlank() },
                unreadCount = r.optInt("unread", 0),
                otherPartyPhotoUrl = other?.optString("photoUrl")?.takeIf { it.isNotBlank() },
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
     * Kept for call-site compatibility: chat images always travel via
     * [uploadImage] (multipart POST /api/v1/uploads/images), never the
     * retired signed-url pipeline. Returning null makes any legacy
     * caller fail visibly instead of 404-spamming.
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
     * Create a new product owned by the caller. The caller must be a
     * seller (or admin). On success returns the new product's UUID.
     */
    /**
     * Create a listing. POST /api/v1/seller/products (the ONLY create
     * endpoint the backend serves — the old "/api/v1/products/v2/create"
     * path does not exist, which made every in-app Add-Product publish a
     * guaranteed 404 masked by the toast). Returns the new product id; the
     * row lands as 'pending' unless [asDraft] is set.
     */
    suspend fun createProduct(
        title: String,
        priceMinor: Long,
        description: String? = null,
        currency: String = "UGX",
        stock: Int = 1,
        category: String? = null,
        brand: String? = null,
        imageUrl: String? = null,
        mediaUrls: List<String>? = null,
        oldPriceMinor: Long? = null,
        location: String? = null,
        isFlashDeal: Boolean = false,
        discountPercent: Int = 0,
        asDraft: Boolean = false,
        imageGsPath: String? = null,
        sku: String? = null,
    ): String? = apiCall(
        method = "POST",
        path = "/api/v1/seller/products",
        body = JSONObject().apply {
            put("title", title)
            put("description", description ?: "")
            put("category", category ?: "Other")
            put("brand", brand ?: "")
            put("priceMinor", priceMinor)
            if (oldPriceMinor != null && oldPriceMinor > 0) put("oldPriceMinor", oldPriceMinor)
            put("stockQuantity", stock)
            put("imageUrl", imageUrl ?: mediaUrls?.firstOrNull() ?: "")
            if (!mediaUrls.isNullOrEmpty()) put("mediaUrls", org.json.JSONArray(mediaUrls))
            put("location", location ?: "")
            put("isFlashDeal", isFlashDeal)
            put("discountPercent", discountPercent)
            put("asDraft", asDraft)
            if (imageGsPath != null) put("imageGsPath", imageGsPath)
            if (sku != null) put("sku", sku)
        },
        parse = { o ->
            o.optJSONObject("product")?.optString("id")?.ifBlank { null }
                ?: o.optString("id").ifBlank { null }
        },
    )

    /**
     * Partial update of an owned listing — PATCH /api/v1/seller/products/:id.
     * Only the keys placed in [patch] are written (the backend's update
     * schema intentionally has no defaults so partial stays partial).
     * Content edits knock an 'approved' row back to the review queue.
     */
    suspend fun updateSellerProduct(id: String, patch: JSONObject): Boolean = apiCall(
        method = "PATCH",
        path = "/api/v1/seller/products/$id",
        body = patch,
        parse = { true },
    ) ?: false

    /** Delete an owned listing — DELETE /api/v1/seller/products/:id. */
    suspend fun deleteSellerProduct(id: String): Boolean = apiCall(
        method = "DELETE",
        path = "/api/v1/seller/products/$id",
        body = null,
        parse = { true },
    ) ?: false

    /**
     * Submit an owned draft for admin review — POST /api/v1/seller/products/:id/submit.
     * Returns the backend's message (e.g. "submitted for review").
     */
    suspend fun submitSellerProductForReview(id: String): Boolean = apiCall(
        method = "POST",
        path = "/api/v1/seller/products/$id/submit",
        body = null,
        parse = { true },
    ) ?: false

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
                logoUrl = absoluteMediaUrl(s.optString("logoUrl").takeIf { it.isNotBlank() }),
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
                        logoUrl = absoluteMediaUrl(r.optString("logoUrl").takeIf { it.isNotBlank() }),
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
        /** Needed to file a review — backend exposes it as productId. */
        val productId: String?,
        val title: String,
        val amountUgx: Long,
        val quantity: Int,
        val status: String,
        val createdAt: String,
        val imageUrl: String,
        val storeName: String,
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
                        productId = r.optString("productId").takeIf { it.isNotBlank() },
                        title = r.optString("title"),
                        amountUgx = r.optLong("amount", 0L),
                        quantity = r.optInt("quantity", 1),
                        status = r.optString("status", "pending"),
                        createdAt = r.optString("createdAt"),
                        imageUrl = r.optString("imageUrl"),
                        storeName = r.optString("storeName"),
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
        imageUrl = absoluteMediaUrl(o.optString("imageUrl").takeIf { it.isNotBlank() }),
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
