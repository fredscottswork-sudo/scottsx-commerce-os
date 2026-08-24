package com.scottsx.app.data.domain

/**
 * Stage-2 marketplace domain models.
 *
 * These types are intentionally backend-ready so the data layer
 * can be swapped from the in-memory [MarketplaceDataSource] (used
 * today) to a real Firestore / REST client later without touching
 * the UI layer.
 */

enum class Role(val displayName: String, val tagline: String) {
    BUYER(
        displayName = "Buyer",
        tagline = "Discover products and connect with sellers across Uganda.",
    ),
    SELLER(
        displayName = "Seller",
        tagline = "List your products, reach more customers, and grow your business.",
    );

    companion object {
        fun fromName(s: String?): Role = if (s.equals("Seller", true)) SELLER else BUYER
    }
}

enum class ProductCategory(val displayName: String, val icon: String) {
    All("All", "all"),
    Electronics("Electronics", "electronics"),
    Fashion("Fashion", "fashion"),
    HomeLiving("Home & Living", "home"),
    Beauty("Beauty", "beauty"),
    Sports("Sports", "sports"),
    Groceries("Groceries", "groceries"),
    Automotive("Automotive", "auto"),
    More("More", "more");

    companion object {
        fun fromKey(s: String?): ProductCategory =
            values().firstOrNull { it.name.equals(s, true) } ?: All

        fun fromApiName(s: String?): ProductCategory? {
            if (s.isNullOrBlank()) return null
            values().firstOrNull { it.name.equals(s, true) || it.displayName.equals(s, true) }?.let { return it }
            return when (s.lowercase()) {
                "home", "home & living", "home &living", "home&living", "home and living" -> HomeLiving
                "footwear", "shoes", "sneakers" -> Sports
                "grocery", "groceries", "food" -> Groceries
                "car", "cars", "vehicle", "vehicles", "auto" -> Automotive
                else -> null
            }
        }
    }
}

data class Brand(
    val id: String,
    val name: String,
)

data class Seller(
    val id: String,
    val name: String,
    val rating: Float = 4.5f,
    val location: String = "Kampala",
    val verified: Boolean = false,
)

data class Product(
    val id: String,
    val name: String,
    val shortDescription: String,
    val description: String,
    val priceUgx: Long,
    val oldPriceUgx: Long? = null,
    val category: ProductCategory,
    val brand: Brand,
    val seller: Seller,
    val imageUrl: String,
    val stock: Int = 10,
    val rating: Float = 4.4f,
    val ratingCount: Int = 32,
    val isFlashDeal: Boolean = false,
    val discountPercent: Int = 0,
    val location: String = "Kampala",
    // ---- Stage 3 additions (all defaulted so existing call sites
    // keep compiling) ----
    val images: List<ProductImage> = listOf(ProductImage(id = "$id-img-0", url = imageUrl, alt = name)),
    val variants: List<ProductVariant> = emptyList(),
    val specs: List<ProductSpec> = emptyList(),
    val purchases: Int = 0,
    val wishlistCount: Int = 0,
)

data class HeroBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val supportingText: String,
    val cta: String,
    val background: BannerBackground,
)

sealed class BannerBackground {
    object BluePurple : BannerBackground()
    object DarkNavy : BannerBackground()
    object GreenTeal : BannerBackground()
    object Sunset : BannerBackground()
}

data class CartItem(
    val productId: String,
    val quantity: Int = 1,
    // Stage 3 — optional variant id (color, size, storage, ...).
    // The Cart store dedupes by (productId, variantId) so two variants
    // of the same product become two cart lines.
    val variantId: String? = null,
)

data class WishlistItem(
    val productId: String,
)

data class BuyerProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    val cartCount: Int = 0,
    val notificationCount: Int = 0,
    val wishlistCount: Int = 0,
)

data class Notification(
    val id: String,
    val title: String,
    val body: String,
    val timestampLabel: String,
    val isUnread: Boolean = true,
)

data class Benefit(
    val title: String,
    val subtitle: String,
    val iconKind: BenefitIcon,
)

enum class BenefitIcon { Delivery, Security, Returns, Protection }

/**
 * In-memory session cache. Lets the app remember the
 * currently-signed-in user's role without round-tripping through
 * Firebase on every nav. Cleared on sign-out.
 */
object SessionCache {
    @Volatile var role: Role? = null
    @Volatile var displayName: String? = null
    @Volatile var email: String? = null
    @Volatile var userId: String? = null
    @Volatile var storeName: String? = null
    @Volatile var storeLocation: String? = null
    @Volatile var avatarUrl: String? = null
    @Volatile var firebaseUid: String? = null

    /**
     * Atomically set the role + display name + email for the current
     * session. Safe to call from any thread.
     *
     * Empty / null displayName is replaced with a non-empty placeholder
     * ("Buyer" / "Seller" / "Google User") so route construction that
     * uses [displayName] never produces a path with an empty segment
     * (which Navigation Compose rejects with IllegalArgumentException
     * "cannot be found in the navigation graph").
     *
     * Empty / null email is preserved as "" — the route argument
     * already defaults to "" and the encoder encodes "" to "".
     */
    fun set(
        role: Role,
        displayName: String?,
        email: String?,
        userId: String? = null,
        storeName: String? = null,
        storeLocation: String? = null,
        avatarUrl: String? = null,
        firebaseUid: String? = null,
    ) {
            this.role = role
            this.displayName = when {
                displayName.isNullOrBlank() -> when (role) {
                    Role.SELLER -> "Seller"
                    Role.BUYER -> "Buyer"
                }
                else -> displayName
            }
            this.email = email ?: ""
            this.userId = userId ?: this.userId
            this.storeName = storeName ?: this.storeName
            this.storeLocation = storeLocation ?: this.storeLocation
            this.avatarUrl = avatarUrl ?: this.avatarUrl
            this.firebaseUid = firebaseUid ?: this.firebaseUid
        }

        fun clear() {
            role = null
            displayName = null
            email = null
            userId = null
            storeName = null
            storeLocation = null
            avatarUrl = null
            firebaseUid = null
        }

        fun userIdOrNull(): String? = userId
        fun roleOrNull(): Role? = role
        fun displayNameOrEmpty(): String = displayName ?: ""
        fun storeNameOrEmpty(): String = storeName ?: when (role) {
            Role.SELLER -> "ScottsTechX Store"
            else -> "ScottsTechX"
        }
        fun locationOrEmpty(): String = storeLocation ?: "Kampala"
        fun firebaseUidOrNull(): String? = firebaseUid
    }
// =====================================================================================
// Seller domain models — Stage 3.2 seller dashboard.
// =====================================================================================

/** Whether a seller is currently accepting new orders. */
enum class StoreStatus(val label: String) {
    Online("Online"),
    Away("Away"),
}

/** A single buyer order as seen by the seller. */
data class SellerOrder(
    val id: String,                      // e.g. "STX-10482"
    val productName: String,
    val productImageUrl: String? = null,
    val itemsCount: Int,
    val totalUgx: Long,
    val placedAtLabel: String,           // e.g. "10:30 AM"
    val status: OrderStatus,
    val buyerName: String,
    val buyerInitial: String = buyerName.firstOrNull()?.uppercase() ?: "?",
)

enum class OrderStatus(val label: String) {
    Pending("Pending"),
    Processing("Processing"),
    Ready("Ready"),
    Completed("Completed"),
    Cancelled("Cancelled");

    companion object {
        fun fromLabel(label: String): OrderStatus =
            values().firstOrNull { it.label.equals(label, ignoreCase = true) } ?: Pending
    }
}

/** Recent orders overview counters (Pending / Processing / Ready / Completed). */
data class SellerOrdersOverview(
    val pending: Int,
    val processing: Int,
    val ready: Int,
    val completed: Int,
)

/** One sales bar in the Sales Performance chart. */
data class SalesPoint(
    val label: String,                    // "Mon", "Tue", ...
    val amountUgx: Long,
)

/** Result of the Seller AI Assistant. */
data class SellerAiInsight(
    val headline: String,
    val body: String,
    val bestProduct: String,
    val trendLabel: String,               // e.g. "+18% this week"
)

/** Low-stock alert row. */
data class LowStockAlert(
    val productId: String,
    val productName: String,
    val remaining: Int,
    val threshold: Int,
)

/**
 * A snapshot of the seller's dashboard data. All numbers below are
 * seeded in `SellerDataSource`; once the backend lands, swap them
 * for Firestore queries that read from the existing `users`,
 * `products`, `orders` collections.
 */
data class SellerDashboardSnapshot(
    val displayName: String,
    val storeName: String,
    val storeId: String,
    val email: String,
    val status: StoreStatus,
    val salesTodayUgx: Long,
    val salesTodayDeltaPct: Float,
    val ordersToday: Int,
    val ordersTodayDelta: Int,
    val customersTotal: Int,
    val customersDelta: Int,
    val rating: Float,
    val ratingLabel: String,
    val ordersOverview: SellerOrdersOverview,
    val recentOrders: List<SellerOrder>,
    val sales: List<SalesPoint>,
    val aiInsight: SellerAiInsight,
    val lowStock: List<LowStockAlert>,
)


// =====================================================================================
// Stage 3 — product discovery, variants, reviews, storefront, messages
// =====================================================================================

/**
 * One image of a product. The PDP gallery displays the first image
 * as the main hero; subsequent images are thumbnails that swap on tap
 * or swipe.
 */
data class ProductImage(
    val id: String,
    val url: String,
    val alt: String = "",
)

/**
 * A purchasable variant of a product. The Stage 3 brief calls out
 * color / size / storage buckets but the model stays generic so
 * any axis is possible (RAM, material, etc.).
 */
data class ProductVariant(
    val id: String,
    val axis: String,           // "Color", "Size", "Storage", ...
    val value: String,          // "Black", "256GB", ...
    val priceDeltaUgx: Long = 0, // added on top of [Product.priceUgx]
    val stock: Int,
    val imageIndex: Int? = null, // when selected, gallery shows this image
    val sku: String? = null,
)

/** A single row in the product specifications table. */
data class ProductSpec(
    val key: String,
    val value: String,
)

/** A buyer-submitted review for a product. */
data class Review(
    val id: String,
    val productId: String,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val rating: Int,                    // 1..5
    val dateLabel: String,              // e.g. "2 weeks ago"
    val text: String,
    val variantLabel: String? = null,   // "Black / 256GB" if buyer specifies
    val verifiedPurchase: Boolean = false,
)

/** Distribution of ratings for the chart on the PDP / Reviews screen. */
data class RatingDistribution(
    val five: Int,
    val four: Int,
    val three: Int,
    val two: Int,
    val one: Int,
) {
    val total: Int get() = five + four + three + two + one
    fun percent(stars: Int): Float = if (total == 0) 0f else when (stars) {
        5 -> five / total.toFloat()
        4 -> four / total.toFloat()
        3 -> three / total.toFloat()
        2 -> two / total.toFloat()
        1 -> one / total.toFloat()
        else -> 0f
    }
}

/** Stock availability for the PDP. */
enum class StockStatus { InStock, LowStock, OutOfStock }

/** Delivery method offered by a single seller. */
data class DeliveryOption(
    val id: String,
    val label: String,           // "Standard Delivery" / "Express"
    val etaDaysLabel: String,     // "1-3 days" / "Same day"
    val feeUgx: Long,
)

/** A single nearby-sellers row shown on the PDP. */
data class NearbySeller(
    val sellerId: String,
    val productId: String,        // may be the same productId or a sibling SKU
    val sellerName: String,
    val priceUgx: Long,
    val distanceKm: Float,
    val deliveryDaysLabel: String,
    val inStock: Boolean,
)

/** A single message inside a buyer↔seller thread. */
data class Message(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timeLabel: String,         // "10:30 AM"
    val isFromBuyer: Boolean,
    val productContextId: String? = null, // PDP auto-attached product
    val isRead: Boolean = false,
    val isDelivered: Boolean = true,
    val isTyping: Boolean = false,
)

/** A buyer↔seller conversation. */
data class MessageThread(
    val id: String,
    val sellerId: String,
    val sellerName: String,
    val productId: String? = null,
    val productName: String? = null,
    val lastMessage: String,
    val lastTimeLabel: String,
    val unread: Int = 0,
)

/** Tabs on the seller's public storefront. */
enum class StorefrontTab(val label: String) {
    Products("Products"),
    Categories("Categories"),
    Reviews("Reviews"),
    About("About"),
}

/** A category entry shown under the "Categories" tab of a storefront. */
data class SellerCategoryRow(
    val category: ProductCategory,
    val productCount: Int,
)

/** Extended product with all Stage 3 fields. Backwards compatible. */
data class ProductFull(
    val product: Product,
    val images: List<ProductImage>,
    val variants: List<ProductVariant>,
    val specs: List<ProductSpec>,
    val purchases: Int = 0,                 // "X sold"
    val wishlistCount: Int = 0,
    val nearby: List<NearbySeller> = emptyList(),
    val delivery: List<DeliveryOption> = emptyList(),
) {
    val id: String get() = product.id
    val displayImage: ProductImage
        get() = images.firstOrNull() ?: ProductImage(
            id = "${product.id}-fallback",
            url = product.imageUrl,
            alt = product.name,
        )
}

/** Aggregated storefront data for the public view. */
data class SellerStorefront(
    val seller: Seller,
    val followers: Int,
    val isFollowed: Boolean,
    val description: String,
    val location: String,
    val productCount: Int,
    val rating: Float,
    val reviewCount: Int,
    val responseRateLabel: String,
    val products: List<Product>,
    val categories: List<SellerCategoryRow>,
    val reviews: List<Review>,
    val verified: Boolean = true,
    val joinedLabel: String = "Joined March 2024",
)
