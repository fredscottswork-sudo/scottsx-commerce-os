package com.scottsx.app.data.remote

import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.Address
import com.scottsx.app.data.domain.AiReply
import com.scottsx.app.data.domain.AppNotification
import com.scottsx.app.data.domain.AuthResult
import com.scottsx.app.data.domain.ChatMessage
import com.scottsx.app.data.domain.CheckoutResult
import com.scottsx.app.data.domain.CmsPage
import com.scottsx.app.data.domain.Conversation
import com.scottsx.app.data.domain.CurrentUserPayload
import com.scottsx.app.data.domain.Faq
import com.scottsx.app.data.domain.NearbySeller
import com.scottsx.app.data.domain.NewProductPayload
import com.scottsx.app.data.domain.Order
import com.scottsx.app.data.domain.PaymentMethod
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.Refund
import com.scottsx.app.data.domain.SellerDashboardStats
import com.scottsx.app.data.domain.SellerProfile
import com.scottsx.app.data.domain.StoreSettings
import com.scottsx.app.data.domain.SupportTicket
import com.scottsx.app.data.domain.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
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

    // Local dev. The emulator maps 10.0.2.2 -> the host machine;
    // for a physical phone replace with your PC's LAN IP (e.g. http://192.168.1.10:3001).
    private const val BASE_URL = "http://127.0.0.1:3001/api/v1"

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
                val message = try {
                    JSONObject(text).optString("error").ifBlank { "HTTP ${response.code}" }
                } catch (_: Exception) {
                    "HTTP ${response.code}"
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

    suspend fun login(email: String, password: String): AuthResult? = try {
        val r = call("/auth/login", "POST", JSONObject().put("email", email).put("password", password), auth = false)
        AuthResult(
            token = r.optString("token"),
            user = CurrentUserPayload.fromJson(r.optJSONObject("user") ?: JSONObject()),
        )
    } catch (e: Exception) {
        null
    }

    suspend fun signInWithFirebase(idToken: String): AuthResult? = try {
        val r = call("/auth/firebase/sign-in", "POST", JSONObject().put("idToken", idToken), auth = false)
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

    suspend fun updateMe(displayName: String? = null, phone: String? = null): Boolean = try {
        val body = JSONObject()
        displayName?.let { body.put("displayName", it) }
        phone?.let { body.put("phone", it) }
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

    suspend fun fetchNearbySellers(
        lat: Double,
        lng: Double,
        radiusKm: Int = 50,
    ): List<NearbySeller> = try {
        val r = call("/sellers/nearby?lat=$lat&lng=$lng&radiusKm=$radiusKm", auth = false)
        val arr = r.optJSONArray("sellers") ?: JSONArray()
        (0 until arr.length()).map { NearbySeller.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
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

    /** Create an order + hosted Nylon Pay payment link. Returns null on failure. */
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

    suspend fun fetchConversations(): List<Conversation> = try {
        val arr = call("/conversations").optJSONArray("conversations") ?: JSONArray()
        (0 until arr.length()).map { Conversation.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun openConversation(sellerId: String, productId: String? = null): String? = try {
        val body = JSONObject().put("sellerId", sellerId)
        productId?.let { body.put("productId", it) }
        call("/conversations", "POST", body).optJSONObject("conversation")?.optString("id")
    } catch (e: Exception) {
        null
    }

    suspend fun fetchMessages(conversationId: String): List<ChatMessage> = try {
        val arr = call("/conversations/$conversationId/messages").optJSONArray("messages") ?: JSONArray()
        (0 until arr.length()).map { ChatMessage.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun sendMessage(conversationId: String, text: String): ChatMessage? = try {
        val r = call("/conversations/$conversationId/messages", "POST", JSONObject().put("text", text))
        ChatMessage.fromJson(r.optJSONObject("message") ?: JSONObject())
    } catch (e: Exception) {
        null
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
