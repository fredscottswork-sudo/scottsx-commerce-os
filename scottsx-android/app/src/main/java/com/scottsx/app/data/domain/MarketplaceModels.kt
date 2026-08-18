package com.scottsx.app.data.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * ScottsTechX — shared domain models.
 *
 * Every model knows how to parse itself from the backend's JSON so V2Client
 * can return typed objects and no raw JSON leaks into screens.
 */

enum class ProductCategory(val displayName: String, val emoji: String) {
    All("All", "🛍️"),
    Electronics("Electronics", "📱"),
    Fashion("Fashion", "👗"),
    HomeLiving("Home & Living", "🏠"),
    Beauty("Beauty", "💄"),
    Sports("Sports", "👟"),
    Groceries("Groceries", "🍚"),
    Automotive("Automotive", "🚗"),
    More("More", "✨");

    companion object {
        fun fromApiName(name: String?): ProductCategory =
            values().firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: All

        fun display(name: String?): String =
            fromApiName(name).displayName
    }
}

data class Seller(
    val id: String,
    val name: String,
    val rating: Double = 0.0,
    val location: String = "",
    val verified: Boolean = false,
    val logoUrl: String? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): Seller = Seller(
            id = o.optString("id"),
            name = o.optString("name", o.optString("storeName")),
            rating = o.optDouble("rating", 0.0),
            location = o.optString("location", o.optString("city")),
            verified = o.optBoolean("verified"),
            logoUrl = o.optStringOrNull("logoUrl"),
        )
    }
}

data class Product(
    val id: String,
    val title: String,
    val description: String = "",
    val priceMinor: Long = 0,
    val oldPriceMinor: Long? = null,
    val currency: String = "UGX",
    val stockQuantity: Int = 1,
    val imageUrl: String = "",
    val category: String = "Other",
    val brand: String = "",
    val seller: Seller = Seller("", "Unknown"),
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val isFlashDeal: Boolean = false,
    val discountPercent: Int = 0,
    val location: String = "",
) {
    val priceUgx: Long get() = priceMinor

    companion object {
        fun fromJson(o: JSONObject): Product = Product(
            id = o.optString("id"),
            title = o.optString("title"),
            description = o.optString("description"),
            priceMinor = o.optLong("priceMinor", 0),
            oldPriceMinor = if (o.isNull("oldPriceMinor")) null else o.optLong("oldPriceMinor"),
            currency = o.optString("currency", "UGX"),
            stockQuantity = o.optInt("stockQuantity", 1),
            imageUrl = o.optString("imageUrl"),
            category = o.optString("category", "Other"),
            brand = o.optString("brand"),
            seller = Seller.fromJson(o.optJSONObject("seller") ?: JSONObject()),
            rating = o.optDouble("rating", 0.0),
            ratingCount = o.optInt("ratingCount", 0),
            isFlashDeal = o.optBoolean("isFlashDeal"),
            discountPercent = o.optInt("discountPercent", 0),
            location = o.optString("location"),
        )

        fun fromJsonArray(arr: JSONArray): List<Product> =
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }
}

data class NearbySeller(
    val id: String,
    val name: String,
    val storeName: String = "",
    val description: String = "",
    val city: String = "",
    val address: String = "",
    val verified: Boolean = false,
    val rating: Double = 0.0,
    val logoUrl: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val serviceRadiusKm: Int = 20,
    val productCount: Int = 0,
    val distanceKm: Double = 0.0,
) {
    companion object {
        fun fromJson(o: JSONObject): NearbySeller = NearbySeller(
            id = o.optString("id"),
            name = o.optString("name", o.optString("storeName")),
            storeName = o.optString("storeName"),
            description = o.optString("description"),
            city = o.optString("city"),
            address = o.optString("address"),
            verified = o.optBoolean("verified"),
            rating = o.optDouble("rating", 0.0),
            logoUrl = o.optStringOrNull("logoUrl"),
            lat = o.optDouble("lat"),
            lng = o.optDouble("lng"),
            serviceRadiusKm = o.optInt("serviceRadiusKm", 20),
            productCount = o.optInt("productCount", 0),
            distanceKm = o.optDouble("distanceKm", 0.0),
        )
    }
}

// ── Messaging ───────────────────────────────────────────────────────────────

data class OtherParty(
    val id: String,
    val name: String,
    val role: String = "buyer",
    val photoUrl: String? = null,
    val verified: Boolean = false,
    val location: String? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): OtherParty = OtherParty(
            id = o.optString("id"),
            name = o.optString("name"),
            role = o.optString("role", "buyer"),
            photoUrl = o.optStringOrNull("photoUrl"),
            verified = o.optBoolean("verified", false),
            location = o.optStringOrNull("location"),
        )
    }
}

data class Conversation(
    val id: String,
    val otherParty: OtherParty,
    val lastMessage: String = "",
    val lastTime: String = "",
    val unread: Int = 0,
    val productId: String? = null,
    val productTitle: String? = null,
    val productImageUrl: String? = null,
    val productPriceMinor: Long? = null,
    val mySide: String = "buyer",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    val pendingOffers: Int = 0,
    val messageCount: Int = 0,
    val lastSenderId: String? = null,
    /** Only populated by GET /conversations/:id. */
    val otherTyping: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject): Conversation = Conversation(
            id = o.optString("id"),
            otherParty = OtherParty.fromJson(o.optJSONObject("otherParty") ?: JSONObject()),
            lastMessage = o.optStringSafe("lastMessage"),
            lastTime = o.optStringSafe("lastTime"),
            unread = o.optInt("unread", 0),
            productId = o.optStringOrNull("productId"),
            productTitle = o.optStringOrNull("productTitle"),
            productImageUrl = o.optStringOrNull("productImageUrl"),
            productPriceMinor = if (o.isNull("productPriceMinor")) null else o.optLong("productPriceMinor"),
            mySide = o.optString("mySide", "buyer"),
            pinned = o.optBoolean("pinned", false),
            archived = o.optBoolean("archived", false),
            muted = o.optBoolean("muted", false),
            pendingOffers = o.optInt("pendingOffers", 0),
            messageCount = o.optInt("messageCount", 0),
            lastSenderId = o.optStringOrNull("lastSenderId"),
            otherTyping = o.optBoolean("otherTyping", false),
        )
    }
}

/** Inbox filter counts returned alongside GET /conversations. */
data class InboxCounts(
    val all: Int = 0,
    val unread: Int = 0,
    val pinned: Int = 0,
    val archived: Int = 0,
    val offers: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): InboxCounts = InboxCounts(
            all = o.optInt("all", 0),
            unread = o.optInt("unread", 0),
            pinned = o.optInt("pinned", 0),
            archived = o.optInt("archived", 0),
            offers = o.optInt("offers", 0),
        )
    }
}

/** GET /conversations response: the list plus whole-inbox counters. */
data class Inbox(
    val conversations: List<Conversation> = emptyList(),
    val counts: InboxCounts = InboxCounts(),
    val totalUnread: Int = 0,
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String = "",
    val imageUrl: String? = null,
    val attachmentName: String? = null,
    /** text | image | offer | system */
    val kind: String = "text",
    val productId: String? = null,
    val productTitle: String? = null,
    val offerMinor: Long? = null,
    /** pending | accepted | declined | countered | withdrawn */
    val offerStatus: String? = null,
    val offerQuantity: Int = 1,
    val replyToId: String? = null,
    val deletedAt: String? = null,
    val readByOther: Boolean = false,
    val createdAt: String = "",
) {
    val timeLabel: String
        get() = createdAt.takeIf { it.isNotBlank() }?.substringAfter("T")?.substring(0, 5) ?: ""

    val isOffer: Boolean get() = kind == "offer"
    val isSystem: Boolean get() = kind == "system"
    val isRetracted: Boolean get() = deletedAt != null
    val offerPending: Boolean get() = offerStatus == "pending"

    companion object {
        fun fromJson(o: JSONObject): ChatMessage = ChatMessage(
            id = o.optString("id"),
            senderId = o.optString("senderId"),
            text = o.optStringSafe("text"),
            imageUrl = o.optStringOrNull("imageUrl"),
            attachmentName = o.optStringOrNull("attachmentName"),
            kind = o.optString("kind", "text").ifBlank { "text" },
            productId = o.optStringOrNull("productId"),
            productTitle = o.optStringOrNull("productTitle"),
            offerMinor = if (o.isNull("offerMinor")) null else o.optLong("offerMinor"),
            offerStatus = o.optStringOrNull("offerStatus"),
            offerQuantity = o.optInt("offerQuantity", 1),
            replyToId = o.optStringOrNull("replyToId"),
            deletedAt = o.optStringOrNull("deletedAt"),
            readByOther = o.optBoolean("readByOther", false),
            createdAt = o.optStringSafe("createdAt"),
        )
    }
}

/** GET /conversations/:id/messages */
data class Transcript(
    val messages: List<ChatMessage> = emptyList(),
    val otherTyping: Boolean = false,
)

/** A saved canned response (GET /me/quick-replies). */
data class QuickReplyItem(
    val id: String,
    val text: String,
    val sortOrder: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): QuickReplyItem = QuickReplyItem(
            id = o.optString("id"),
            text = o.optString("text"),
            sortOrder = o.optInt("sortOrder", 0),
        )
    }
}

data class MessageThread(
    val id: String,
    val sellerId: String,
    val sellerName: String,
    val productId: String? = null,
    val productName: String? = null,
    val lastMessage: String = "",
    val lastTimeLabel: String = "",
    val unread: Int = 0,
)

enum class StorefrontTab { Products, Categories, Reviews, About }

// ── User settings / data ────────────────────────────────────────────────────

data class Address(
    val id: String = "",
    val label: String = "",
    val line1: String = "",
    val city: String = "",
    val country: String = "Uganda",
    val isDefault: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject): Address = Address(
            id = o.optString("id"),
            label = o.optString("label"),
            line1 = o.optString("line1"),
            city = o.optString("city"),
            country = o.optString("country", "Uganda"),
            isDefault = o.optBoolean("isDefault"),
        )
        fun toJson(a: Address): JSONObject = JSONObject()
            .put("label", a.label)
            .put("line1", a.line1)
            .put("city", a.city)
            .put("country", a.country)
            .put("isDefault", a.isDefault)
    }
}

data class PaymentMethod(
    val id: String = "",
    val type: String = "momo",
    val label: String = "",
    val last4: String = "",
    val phone: String = "",
    val isDefault: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject): PaymentMethod = PaymentMethod(
            id = o.optString("id"),
            type = o.optString("type", "momo"),
            label = o.optString("label"),
            last4 = o.optString("last4"),
            phone = o.optString("phone"),
            isDefault = o.optBoolean("isDefault"),
        )
        fun toJson(p: PaymentMethod): JSONObject = JSONObject()
            .put("type", p.type)
            .put("label", p.label)
            .put("last4", p.last4)
            .put("phone", p.phone)
            .put("isDefault", p.isDefault)
    }
}

data class Order(
    val id: String,
    val sellerId: String = "",
    val title: String = "",
    val amount: Long = 0,
    val quantity: Int = 1,
    val status: String = "pending",
    val createdAt: String = "",
    val imageUrl: String = "",
    val storeName: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): Order = Order(
            id = o.optString("id"),
            sellerId = o.optString("sellerId"),
            title = o.optString("title"),
            amount = o.optLong("amount", 0),
            quantity = o.optInt("quantity", 1),
            status = o.optString("status", "pending"),
            createdAt = o.optString("createdAt"),
            imageUrl = o.optString("imageUrl"),
            storeName = o.optString("storeName"),
        )
    }
}

data class Refund(
    val id: String,
    val orderId: String = "",
    val reason: String = "",
    val status: String = "pending",
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): Refund = Refund(
            id = o.optString("id"),
            orderId = o.optString("orderId"),
            reason = o.optString("reason"),
            status = o.optString("status", "pending"),
            createdAt = o.optString("createdAt"),
        )
    }
}

data class SupportTicket(
    val id: String,
    val subject: String = "",
    val message: String = "",
    val status: String = "open",
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): SupportTicket = SupportTicket(
            id = o.optString("id"),
            subject = o.optString("subject"),
            message = o.optString("message"),
            status = o.optString("status", "open"),
            createdAt = o.optString("createdAt"),
        )
    }
}

data class Faq(
    val id: String,
    val question: String = "",
    val answer: String = "",
    val category: String = "General",
) {
    companion object {
        fun fromJson(o: JSONObject): Faq = Faq(
            id = o.optString("id"),
            question = o.optString("question"),
            answer = o.optString("answer"),
            category = o.optString("category", "General"),
        )
    }
}

data class AppNotification(
    val id: String,
    val title: String = "",
    val body: String = "",
    val type: String = "general",
    val read: Boolean = false,
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): AppNotification = AppNotification(
            id = o.optString("id"),
            title = o.optString("title"),
            body = o.optString("body"),
            type = o.optString("type", "general"),
            read = o.optBoolean("read"),
            createdAt = o.optString("createdAt"),
        )
    }
}

data class UserSettings(
    val theme: String = "system",
    val language: String = "en",
    val currency: String = "UGX",
    val notifyOrderUpdates: Boolean = true,
    val notifyMessages: Boolean = true,
    val notifyMarketing: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject): UserSettings = UserSettings(
            theme = o.optString("theme", "system"),
            language = o.optString("language", "en"),
            currency = o.optString("currency", "UGX"),
            notifyOrderUpdates = o.optBoolean("notifyOrderUpdates", true),
            notifyMessages = o.optBoolean("notifyMessages", true),
            notifyMarketing = o.optBoolean("notifyMarketing"),
        )
    }
}

// ── Seller ──────────────────────────────────────────────────────────────────

data class StoreSettings(
    val storeName: String = "",
    val storeDescription: String = "",
    val storeLogoUrl: String = "",
    val legalName: String = "",
    val tin: String = "",
    val businessEmail: String = "",
    val businessPhone: String = "",
    val address: String = "",
    val pickupInstructions: String = "",
    val serviceRadiusKm: Int = 20,
    val deliveryFeeUgx: Long = 0,
    val freeAboveUgx: Long = 0,
    val codEnabled: Boolean = true,
    val momoNumber: String = "",
    val bankName: String = "",
    val bankAccount: String = "",
    val notifOrderUpdates: Boolean = true,
    val notifBuyerMessages: Boolean = true,
    val notifMarketing: Boolean = false,
    val notifWeeklyDigest: Boolean = true,
    val twoFactorEnabled: Boolean = false,
    val returnsWindowDays: Int = 7,
    val refundPolicy: String = "",
    val terms: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val city: String = "",
    val verified: Boolean = false,
    val rating: Double = 0.0,
) {
    companion object {
        fun fromJson(o: JSONObject): StoreSettings = StoreSettings(
            storeName = o.optString("storeName"),
            storeDescription = o.optString("storeDescription"),
            storeLogoUrl = o.optString("storeLogoUrl"),
            legalName = o.optString("legalName"),
            tin = o.optString("tin"),
            businessEmail = o.optString("businessEmail"),
            businessPhone = o.optString("businessPhone"),
            address = o.optString("address"),
            pickupInstructions = o.optString("pickupInstructions"),
            serviceRadiusKm = o.optInt("serviceRadiusKm", 20),
            deliveryFeeUgx = o.optLong("deliveryFeeUgx", 0),
            freeAboveUgx = o.optLong("freeAboveUgx", 0),
            codEnabled = o.optBoolean("codEnabled", true),
            momoNumber = o.optString("momoNumber"),
            bankName = o.optString("bankName"),
            bankAccount = o.optString("bankAccount"),
            notifOrderUpdates = o.optBoolean("notifOrderUpdates", true),
            notifBuyerMessages = o.optBoolean("notifBuyerMessages", true),
            notifMarketing = o.optBoolean("notifMarketing"),
            notifWeeklyDigest = o.optBoolean("notifWeeklyDigest", true),
            twoFactorEnabled = o.optBoolean("twoFactorEnabled"),
            returnsWindowDays = o.optInt("returnsWindowDays", 7),
            refundPolicy = o.optString("refundPolicy"),
            terms = o.optString("terms"),
            contactEmail = o.optString("contactEmail"),
            contactPhone = o.optString("contactPhone"),
            city = o.optString("city"),
            verified = o.optBoolean("verified"),
            rating = o.optDouble("rating", 0.0),
        )
    }
}

data class SellerProfile(
    val id: String = "",
    val name: String = "",
    val storeName: String = "",
    val description: String = "",
    val city: String = "",
    val address: String = "",
    val verified: Boolean = false,
    val rating: Double = 0.0,
    val logoUrl: String? = null,
    val phone: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val momoNumber: String = "",
    val bankName: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): SellerProfile = SellerProfile(
            id = o.optString("id"),
            name = o.optString("name"),
            storeName = o.optString("storeName"),
            description = o.optString("description"),
            city = o.optString("city"),
            address = o.optString("address"),
            verified = o.optBoolean("verified"),
            rating = o.optDouble("rating", 0.0),
            logoUrl = o.optStringOrNull("logoUrl"),
            phone = o.optString("phone"),
            contactEmail = o.optString("contactEmail"),
            contactPhone = o.optString("contactPhone"),
            momoNumber = o.optString("momoNumber"),
            bankName = o.optString("bankName"),
        )
    }
}

data class SellerDashboardStats(
    val revenueUgx: Long = 0,
    val orders: Int = 0,
    val totalProducts: Int = 0,
    val lowStock: Int = 0,
    val topProduct: String? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): SellerDashboardStats = SellerDashboardStats(
            revenueUgx = o.optLong("revenueUgx", 0),
            orders = o.optInt("orders", 0),
            totalProducts = o.optInt("totalProducts", 0),
            lowStock = o.optInt("lowStock", 0),
            topProduct = o.optStringOrNull("topProduct"),
        )
    }
}

// ── AI / CMS / auth ─────────────────────────────────────────────────────────

data class AiReply(
    val text: String = "",
    val provider: String = "openrouter",
    val model: String = "",
    val screen: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): AiReply = AiReply(
            text = o.optString("text"),
            provider = o.optString("provider", "openrouter"),
            model = o.optString("model"),
            screen = o.optString("screen"),
        )
    }
}

data class CmsPage(
    val slug: String = "",
    val title: String = "",
    val body: String = "",
    val updatedAt: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): CmsPage = CmsPage(
            slug = o.optString("slug"),
            title = o.optString("title"),
            body = o.optString("body"),
            updatedAt = o.optString("updatedAt"),
        )
    }
}

data class AuthResult(
    val token: String,
    val user: CurrentUserPayload,
)

data class CurrentUserPayload(
    val id: String,
    val email: String,
    val displayName: String,
    val phone: String = "",
    val role: String = "buyer",
    val emailVerified: Boolean = false,
    val profilePhotoUrl: String? = null,
    val city: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): CurrentUserPayload = CurrentUserPayload(
            id = o.optString("id"),
            email = o.optString("email"),
            displayName = o.optString("displayName"),
            phone = o.optString("phone"),
            role = o.optString("role", "buyer"),
            emailVerified = o.optBoolean("emailVerified"),
            profilePhotoUrl = o.optStringOrNull("profilePhotoUrl"),
            city = o.optString("city"),
        )
    }
}

data class NewProductPayload(
    val title: String,
    val description: String = "",
    val category: String = "Other",
    val brand: String = "",
    val priceMinor: Long,
    val oldPriceMinor: Long? = null,
    val stockQuantity: Int = 1,
    val imageUrl: String = "",
    val mediaUrls: List<String> = emptyList(),
    val location: String = "",
    val isFlashDeal: Boolean = false,
    val discountPercent: Int = 0,
)

/**
 * Result of POST /orders/checkout via Nylon Pay.
 *
 * mode "invoice" = hosted payment link (live keys); the app opens/copies the link.
 * mode "collect" = mobile-money push to the buyer's phone (sandbox & live);
 *                  the app tells the buyer to check their phone for the MoMo prompt.
 */
data class CheckoutResult(
    val orderId: String = "",
    val paymentMode: String = "invoice",
    val paymentLink: String = "",
    val invoiceNumber: String = "",
    val paymentReference: String = "",
    val status: String = "pending",
) {
    companion object {
        fun fromJson(o: JSONObject): CheckoutResult = CheckoutResult(
            orderId = o.optString("orderId", o.optJSONObject("order")?.optString("id") ?: ""),
            paymentMode = o.optString("paymentMode", o.optJSONObject("order")?.optString("paymentMode") ?: "invoice"),
            paymentLink = o.optString("paymentLink", o.optJSONObject("order")?.optString("paymentLink") ?: ""),
            invoiceNumber = o.optString("invoiceNumber", o.optJSONObject("order")?.optString("invoiceNumber") ?: ""),
            paymentReference = o.optString("paymentReference", ""),
            status = o.optString("status", "pending"),
        )
    }
}

// ── small JSON helpers ──────────────────────────────────────────────────────

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || optString(key).isBlank()) null else optString(key)

/**
 * org.json's [JSONObject.optString] returns the literal string "null" when the
 * value is a JSON null — verified against org.json 1.8. That leaks "null" into
 * the UI, so anything reading a nullable column must use this instead.
 */
internal fun JSONObject.optStringSafe(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)

internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)
