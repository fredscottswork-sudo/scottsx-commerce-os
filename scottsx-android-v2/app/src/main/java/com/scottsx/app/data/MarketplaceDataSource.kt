package com.scottsx.app.data

import com.scottsx.app.data.domain.BannerBackground
import com.scottsx.app.data.domain.Benefit
import com.scottsx.app.data.domain.BenefitIcon
import com.scottsx.app.data.domain.Brand
import com.scottsx.app.data.domain.BuyerProfile
import com.scottsx.app.data.domain.HeroBanner
import com.scottsx.app.data.domain.DeliveryOption
import com.scottsx.app.data.domain.Message
import com.scottsx.app.data.domain.MessageThread
import com.scottsx.app.data.domain.NearbySeller
import com.scottsx.app.data.domain.Notification
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.domain.ProductImage
import com.scottsx.app.data.domain.ProductSpec
import com.scottsx.app.data.domain.ProductVariant
import com.scottsx.app.data.domain.RatingDistribution
import com.scottsx.app.data.domain.Review
import com.scottsx.app.data.domain.Seller
import com.scottsx.app.data.domain.SellerCategoryRow
import com.scottsx.app.data.domain.SellerStorefront
import com.scottsx.app.data.domain.StockStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stage-2 in-memory marketplace data source.
 *
 * The brief says "Each slide must be configurable from backend
 * data later" and "Do not hardcode the promotional content into
 * the architecture." We honor that by keeping the data layer
 * behind this single object — a future commit can replace
 * [MarketplaceDataSource] with a Firestore / REST client without
 * touching the UI layer.
 *
 * Prices are real UGX (Ugandan Shillings) values, locations are
 * Uganda-relevant (Kampala, Entebbe, Jinja, Mbarara, Gulu), and
 * the products are sketched to be plausible Uganda marketplace
 * listings rather than random.
 */
object MarketplaceDataSource {

    // ---------------------------------------------------------------
    // Brands & sellers (kept tiny — each product reuses a stable id)
    // ---------------------------------------------------------------
    private val brandSamsung = Brand("samsung", "Samsung")
    private val brandApple = Brand("apple", "Apple")
    private val brandHp = Brand("hp", "HP")
    private val brandNike = Brand("nike", "Nike")
    private val brandAdidas = Brand("adidas", "Adidas")
    private val brandSafaricom = Brand("safaricom", "Safaricom")
    private val brandNewBalance = Brand("nb", "New Balance")
    private val brandKampalaCrafts = Brand("kc", "Kampala Crafts")
    private val brandPearl = Brand("pearl", "Pearl")
    private val brandHisense = Brand("hisense", "Hisense")
    private val brandMukwano = Brand("mukwano", "Mukwano")
    private val brandVivo = Brand("vivo", "Vivo")

    private val sellerTechHub = Seller("tech-hub", "Tech Hub Uganda", rating = 4.7f, location = "Kampala", verified = true)
    private val sellerFashionHouse = Seller("fashion-house", "Fashion House", rating = 4.5f, location = "Entebbe", verified = true)
    private val sellerSneakerKing = Seller("sneaker-king", "Sneaker King", rating = 4.4f, location = "Jinja")
    private val sellerHomeAppliances = Seller("home-appliances", "Home & Beyond", rating = 4.3f, location = "Kampala")
    private val sellerGlamour = Seller("glamour", "Glamour Cosmetics", rating = 4.6f, location = "Kampala", verified = true)
    private val sellerSporting = Seller("sporting-life", "Sporting Life", rating = 4.2f, location = "Mbarara")
    private val sellerPearlFresh = Seller("pearl-fresh", "Pearl Fresh Mart", rating = 4.4f, location = "Kampala")
    private val sellerAutoParts = Seller("auto-parts", "Auto Parts Pro", rating = 4.1f, location = "Kampala")
    private val sellerUgandaCrafts = Seller("uganda-crafts", "Uganda Crafts", rating = 4.8f, location = "Jinja", verified = true)

    // ---------------------------------------------------------------
    // Products
    // ---------------------------------------------------------------
    private val products: List<Product> = listOf(
        Product(
            id = "p-samsung-a15",
            name = "Samsung Galaxy A15",
            shortDescription = "128GB · 6.5\" AMOLED · 50MP",
            description = "Latest Samsung Galaxy A15 with AMOLED display and a 50MP triple camera. Long-lasting 5000mAh battery and 25W fast charging.",
            priceUgx = 1_150_000,
            oldPriceUgx = 1_350_000,
            category = ProductCategory.Electronics,
            brand = brandSamsung,
            seller = sellerTechHub,
            imageUrl = "https://images.unsplash.com/photo-1610792516775-01de03eae630?w=800",
            rating = 4.6f,
            ratingCount = 184,
            isFlashDeal = true,
            discountPercent = 15,
            location = "Kampala",
        ),
        Product(
            id = "p-apple-iphone13",
            name = "iPhone 13 (Refurb)",
            shortDescription = "128GB · Midnight · Unlocked",
            description = "Certified refurbished iPhone 13 with new battery, original Apple parts, 1-year warranty.",
            priceUgx = 2_950_000,
            oldPriceUgx = 3_400_000,
            category = ProductCategory.Electronics,
            brand = brandApple,
            seller = sellerTechHub,
            imageUrl = "https://images.unsplash.com/photo-1592286927505-1def25115558?w=800",
            rating = 4.7f,
            ratingCount = 96,
            isFlashDeal = true,
            discountPercent = 13,
            location = "Kampala",
        ),
        Product(
            id = "p-hp-laptop",
            name = "HP Pavilion 15 Laptop",
            shortDescription = "i5 12th · 8GB · 512GB SSD",
            description = "HP Pavilion 15 with 12th gen Intel i5, 8GB RAM, fast 512GB SSD and a Full HD display. Great for work and study.",
            priceUgx = 2_650_000,
            oldPriceUgx = 3_050_000,
            category = ProductCategory.Electronics,
            brand = brandHp,
            seller = sellerTechHub,
            imageUrl = "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800",
            rating = 4.4f,
            ratingCount = 67,
            isFlashDeal = true,
            discountPercent = 13,
            location = "Entebbe",
        ),
        Product(
            id = "p-nike-airmax",
            name = "Nike Air Max 270",
            shortDescription = "Men's · Black/White · Size 42",
            description = "Iconic Air Max 270 with full-length Air unit for all-day comfort. Premium leather and mesh upper.",
            priceUgx = 580_000,
            oldPriceUgx = 720_000,
            category = ProductCategory.Fashion,
            brand = brandNike,
            seller = sellerSneakerKing,
            imageUrl = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800",
            rating = 4.5f,
            ratingCount = 142,
            discountPercent = 19,
            location = "Kampala",
        ),
        Product(
            id = "p-adidas-ultraboost",
            name = "Adidas Ultraboost 22",
            shortDescription = "Running · Core Black · Size 41",
            description = "Adidas Ultraboost 22 with responsive Boost midsole and Primeknit upper. Built for serious runners.",
            priceUgx = 720_000,
            oldPriceUgx = 850_000,
            category = ProductCategory.Sports,
            brand = brandAdidas,
            seller = sellerSporting,
            imageUrl = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800",
            rating = 4.7f,
            ratingCount = 89,
            isFlashDeal = true,
            discountPercent = 15,
            location = "Jinja",
        ),
        Product(
            id = "p-newbalance-574",
            name = "New Balance 574 Core",
            shortDescription = "Grey · Unisex · Size 40",
            description = "The timeless New Balance 574 — versatile, comfortable and built to last.",
            priceUgx = 410_000,
            category = ProductCategory.Fashion,
            brand = brandNewBalance,
            seller = sellerSneakerKing,
            imageUrl = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800",
            rating = 4.3f,
            ratingCount = 54,
            location = "Kampala",
        ),
        Product(
            id = "p-basmati-rice",
            name = "Mukwano Basmati Rice 5kg",
            shortDescription = "Premium long-grain",
            description = "Long-grain aromatic basmati rice from Mukwano. Perfect for pilau, biryani and everyday meals.",
            priceUgx = 42_000,
            oldPriceUgx = 48_000,
            category = ProductCategory.Groceries,
            brand = brandMukwano,
            seller = sellerPearlFresh,
            imageUrl = "https://images.unsplash.com/photo-1586201375761-83885001b20f?w=800",
            rating = 4.5f,
            ratingCount = 312,
            isFlashDeal = true,
            discountPercent = 13,
            location = "Kampala",
        ),
        Product(
            id = "p-cooking-oil",
            name = "Mukwano Cooking Oil 3L",
            shortDescription = "Pure sunflower",
            description = "Premium refined sunflower oil — perfect for deep-frying and everyday cooking.",
            priceUgx = 38_000,
            category = ProductCategory.Groceries,
            brand = brandMukwano,
            seller = sellerPearlFresh,
            imageUrl = "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=800",
            rating = 4.4f,
            ratingCount = 224,
            location = "Kampala",
        ),
        Product(
            id = "p-hisense-tv",
            name = "Hisense 55\" 4K Smart TV",
            shortDescription = "ULED · Android TV · HDR",
            description = "55-inch 4K ULED smart TV with Android TV, Dolby Vision HDR, built-in Chromecast and Bluetooth.",
            priceUgx = 1_980_000,
            oldPriceUgx = 2_300_000,
            category = ProductCategory.HomeLiving,
            brand = brandHisense,
            seller = sellerHomeAppliances,
            imageUrl = "https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=800",
            rating = 4.5f,
            ratingCount = 78,
            isFlashDeal = true,
            discountPercent = 14,
            location = "Kampala",
        ),
        Product(
            id = "p-vivo-y21",
            name = "Vivo Y21 (128GB)",
            shortDescription = "Midnight Blue · 5000mAh",
            description = "Vivo Y21 with 128GB storage, 5000mAh battery and a vibrant 6.5\" HD+ display. Reliable everyday performer.",
            priceUgx = 850_000,
            category = ProductCategory.Electronics,
            brand = brandVivo,
            seller = sellerTechHub,
            imageUrl = "https://images.unsplash.com/photo-1598327105666-5b89351aff97?w=800",
            rating = 4.2f,
            ratingCount = 41,
            location = "Mbarara",
        ),
        Product(
            id = "p-lipstick",
            name = "Glamour Matte Lipstick",
            shortDescription = "Long-lasting · 12 shades",
            description = "Highly pigmented matte lipstick that stays put for 12+ hours. Available in 12 on-trend shades.",
            priceUgx = 28_000,
            oldPriceUgx = 35_000,
            category = ProductCategory.Beauty,
            brand = brandPearl,
            seller = sellerGlamour,
            imageUrl = "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=800",
            rating = 4.4f,
            ratingCount = 156,
            isFlashDeal = true,
            discountPercent = 20,
            location = "Kampala",
        ),
        Product(
            id = "p-barsoap",
            name = "Pearl Bar Soap (Pack of 6)",
            shortDescription = "Family size · 200g each",
            description = "Mild family bar soap with a fresh fragrance. 6-pack value bundle for everyday use.",
            priceUgx = 18_000,
            category = ProductCategory.Beauty,
            brand = brandPearl,
            seller = sellerGlamour,
            imageUrl = "https://images.unsplash.com/photo-1600857544200-b2f666a9a2ec?w=800",
            rating = 4.3f,
            ratingCount = 88,
            location = "Kampala",
        ),
        Product(
            id = "p-smartwatch",
            name = "Safaricom Smart Watch X1",
            shortDescription = "Heart rate · GPS · 7-day battery",
            description = "Feature-rich smartwatch with continuous heart-rate monitoring, built-in GPS, sleep tracking and a 7-day battery.",
            priceUgx = 320_000,
            oldPriceUgx = 380_000,
            category = ProductCategory.Electronics,
            brand = brandSafaricom,
            seller = sellerTechHub,
            imageUrl = "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800",
            rating = 4.4f,
            ratingCount = 73,
            discountPercent = 16,
            location = "Kampala",
        ),
        Product(
            id = "p-anker-powerbank",
            name = "20,000mAh Power Bank",
            shortDescription = "Fast charge · Dual USB-C",
            description = "Slim 20,000mAh power bank with 22.5W fast charging and dual USB-C ports. Charges a phone 4-5 times.",
            priceUgx = 110_000,
            oldPriceUgx = 145_000,
            category = ProductCategory.Electronics,
            brand = brandHp,
            seller = sellerTechHub,
            imageUrl = "https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=800",
            rating = 4.6f,
            ratingCount = 117,
            isFlashDeal = true,
            discountPercent = 24,
            location = "Entebbe",
        ),
        Product(
            id = "p-headphones",
            name = "Wireless Headphones Pro",
            shortDescription = "ANC · 30h battery · Bluetooth 5.3",
            description = "Over-ear wireless headphones with active noise cancellation, 30-hour battery and Bluetooth 5.3.",
            priceUgx = 280_000,
            oldPriceUgx = 360_000,
            category = ProductCategory.Electronics,
            brand = brandSamsung,
            seller = sellerTechHub,
            imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800",
            rating = 4.5f,
            ratingCount = 156,
            discountPercent = 22,
            location = "Kampala",
        ),
        Product(
            id = "p-basket",
            name = "Hand-woven Market Basket",
            shortDescription = "Natural fibres · 35cm",
            description = "Hand-woven by Ugandan artisans using sustainable local materials. Perfect for shopping or storage.",
            priceUgx = 65_000,
            category = ProductCategory.HomeLiving,
            brand = brandKampalaCrafts,
            seller = sellerUgandaCrafts,
            imageUrl = "https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=800",
            rating = 4.7f,
            ratingCount = 64,
            location = "Jinja",
        ),
        Product(
            id = "p-soybean-oil",
            name = "Fortified Soybean Oil 2L",
            shortDescription = "Vitamin A & D fortified",
            description = "Locally fortified cooking oil rich in Vitamin A and D. Affordable nutrition for everyday meals.",
            priceUgx = 24_000,
            category = ProductCategory.Groceries,
            brand = brandMukwano,
            seller = sellerPearlFresh,
            imageUrl = "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=800",
            rating = 4.4f,
            ratingCount = 102,
            location = "Gulu",
        ),
        Product(
            id = "p-tire",
            name = "All-Season Tire 205/55 R16",
            shortDescription = "Tubeless · 50,000km warranty",
            description = "All-season tubeless tire with 50,000km warranty. Excellent wet and dry grip.",
            priceUgx = 380_000,
            oldPriceUgx = 450_000,
            category = ProductCategory.Automotive,
            brand = brandHp,
            seller = sellerAutoParts,
            imageUrl = "https://images.unsplash.com/photo-1568844293986-8d0400bd4745?w=800",
            rating = 4.3f,
            ratingCount = 41,
            discountPercent = 16,
            location = "Kampala",
        ),
    )

    val allProducts: List<Product> = products
    val flashDeals: List<Product> = products.filter { it.isFlashDeal }
    val recommended: List<Product> = products.filter { it.rating >= 4.4f }.take(10)

    fun productsByCategory(category: ProductCategory): List<Product> {
        if (category == ProductCategory.All) return products
        if (category == ProductCategory.More) return emptyList()
        return products.filter { it.category == category }
    }

    // ---------------------------------------------------------------
    // Hero banners — carousel content
    // ---------------------------------------------------------------
    val heroBanners: List<HeroBanner> = listOf(
        HeroBanner(
            id = "hero-1",
            title = "BIG DEALS",
            subtitle = "Amazing deals\njust for you!",
            supportingText = "Shop the best products\nfrom trusted sellers.",
            cta = "Shop Now",
            background = BannerBackground.BluePurple,
        ),
        HeroBanner(
            id = "hero-2",
            title = "FLASH SALE",
            subtitle = "Up to 24% off\nthis week",
            supportingText = "Limited time deals on\nphones, laptops and more.",
            cta = "Grab Now",
            background = BannerBackground.DarkNavy,
        ),
        HeroBanner(
            id = "hero-3",
            title = "LOCAL PICKS",
            subtitle = "Made in\nUganda",
            supportingText = "Support Ugandan makers\nand small businesses.",
            cta = "Shop Local",
            background = BannerBackground.GreenTeal,
        ),
    )

    // ---------------------------------------------------------------
    // Marketplace benefits (per brief: 4 cards)
    // ---------------------------------------------------------------
    val benefits: List<Benefit> = listOf(
        Benefit("FREE DELIVERY", "On orders over\nUGX 50,000", BenefitIcon.Delivery),
        Benefit("SECURE PAYMENTS", "100% safe &\nreliable", BenefitIcon.Security),
        Benefit("EASY RETURNS", "7-day return\npolicy", BenefitIcon.Returns),
        Benefit("BUYER PROTECTION", "Shop with\nconfidence", BenefitIcon.Protection),
    )

    // ---------------------------------------------------------------
    // Mock profile, cart, notifications
    // ---------------------------------------------------------------
    fun profileFor(displayName: String?, email: String?): BuyerProfile {
        val dn = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@") ?: "ScottstechX User"
        return BuyerProfile(
            uid = "u-self",
            displayName = dn,
            email = email ?: "",
            avatarUrl = null,
            cartCount = 0,
            notificationCount = 3,
            wishlistCount = 0,
        )
    }

    val notifications: List<Notification> = listOf(
        Notification(
            id = "n-1",
            title = "Flash Deal just started!",
            body = "Up to 24% off electronics — ends in 02:45:30.",
            timestampLabel = "Just now",
            isUnread = true,
        ),
        Notification(
            id = "n-2",
            title = "Your order #2043 has shipped",
            body = "Track your delivery in real-time.",
            timestampLabel = "2h ago",
            isUnread = true,
        ),
        Notification(
            id = "n-3",
            title = "New seller near you",
            body = "Uganda Crafts is open in Jinja.",
            timestampLabel = "Yesterday",
            isUnread = true,
        ),
    )

    // ----- Sidebar badge counters -----
    // Stage 3.1 placeholder counts. Once the backend lands, swap these
    // for real Firestore queries (collections/users/{uid}/unreadCount).
    fun unreadMessagesCount(): Int = 2
    fun unreadNotificationsCount(): Int = notifications.count { it.isUnread }
    fun pendingOrdersCount(): Int = 3
    // =====================================================================================
    // Stage 3 — product augmentation + queries
    // =====================================================================================

    /** Standard variants, specs, nearby sellers, delivery for a product. */
    private fun augment(base: Product): Product {
        val colorVariants = if (base.id in productIdsWithColorVariants) listOf(
            ProductVariant(id = "${base.id}-v-black", axis = "Color", value = "Black", stock = base.stock, imageIndex = 0),
            ProductVariant(id = "${base.id}-v-blue", axis = "Color", value = "Blue", stock = (base.stock - 2).coerceAtLeast(0), imageIndex = 1),
            ProductVariant(id = "${base.id}-v-white", axis = "Color", value = "White", stock = (base.stock - 1).coerceAtLeast(0), imageIndex = 2),
        ) else emptyList()
        val sizeVariants = if (base.id in productIdsWithSizeVariants) listOf(
            ProductVariant(id = "${base.id}-v-s", axis = "Size", value = "S", stock = 4, priceDeltaUgx = 0),
            ProductVariant(id = "${base.id}-v-m", axis = "Size", value = "M", stock = 7, priceDeltaUgx = 0),
            ProductVariant(id = "${base.id}-v-l", axis = "Size", value = "L", stock = 5, priceDeltaUgx = 0),
            ProductVariant(id = "${base.id}-v-xl", axis = "Size", value = "XL", stock = 2, priceDeltaUgx = 0),
        ) else emptyList()
        val storageVariants = if (base.id in productIdsWithStorageVariants) listOf(
            ProductVariant(id = "${base.id}-v-128", axis = "Storage", value = "128GB", stock = 5, priceDeltaUgx = 0),
            ProductVariant(id = "${base.id}-v-256", axis = "Storage", value = "256GB", stock = 3, priceDeltaUgx = 120_000),
            ProductVariant(id = "${base.id}-v-512", axis = "Storage", value = "512GB", stock = 1, priceDeltaUgx = 280_000),
        ) else emptyList()
        val variants = colorVariants + sizeVariants + storageVariants
        val images = (1..3).map { idx ->
            ProductImage(id = "${base.id}-img-$idx", url = base.imageUrl, alt = "${base.name} - image $idx")
        }
        val specs = if (base.id in productIdsWithSpecs) listOf(
            ProductSpec("Brand", base.brand.name),
            ProductSpec("Category", base.category.displayName),
            ProductSpec("Location", base.location),
            ProductSpec("Seller", base.seller.name),
            ProductSpec("Stock", "${base.stock} units"),
            ProductSpec("Warranty", if (base.category == ProductCategory.Electronics) "1 Year" else "Seller Warranty"),
        ) else emptyList()
        val nearby = buildNearby(base)
        val purchases = (base.ratingCount * 3).coerceAtLeast(10)
        return base.copy(
            images = images,
            variants = variants,
            specs = specs,
            purchases = purchases,
            wishlistCount = (base.ratingCount / 8).coerceAtLeast(2),
        )
    }

    private val productIdsWithColorVariants = setOf(
        "p-samsung-a15", "p-apple-iphone13", "p-hp-laptop", "p-nike-airmax",
        "p-adidas-ultraboost", "p-newbalance-574", "p-hisense-tv", "p-vivo-y21",
        "p-smartwatch", "p-headphones",
    )
    private val productIdsWithSizeVariants = setOf(
        "p-nike-airmax", "p-adidas-ultraboost", "p-newbalance-574",
    )
    private val productIdsWithStorageVariants = setOf(
        "p-samsung-a15", "p-apple-iphone13", "p-hp-laptop",
    )
    private val productIdsWithSpecs = setOf(
        "p-samsung-a15", "p-apple-iphone13", "p-hp-laptop", "p-hisense-tv",
        "p-vivo-y21", "p-smartwatch", "p-headphones",
    )

    /** Build a list of nearby sellers for a product. */
    private fun buildNearby(p: Product): List<NearbySeller> {
        val me = NearbySeller(
            sellerId = p.seller.id,
            productId = p.id,
            sellerName = p.seller.name,
            priceUgx = p.priceUgx,
            distanceKm = if (p.seller.location == p.location) 1.8f else 3.5f,
            deliveryDaysLabel = "1-3 days",
            inStock = p.stock > 0,
        )
        val altSellers = sellersExcluding(p.seller.id).take(2).mapIndexed { idx, s ->
            NearbySeller(
                sellerId = s.id,
                productId = "${p.id}-alt-$idx",
                sellerName = s.name,
                priceUgx = (p.priceUgx * (1.01f + idx * 0.012f)).toLong(),
                distanceKm = 2.5f + idx * 1.4f,
                deliveryDaysLabel = if (idx == 0) "1-3 days" else "3-5 days",
                inStock = idx == 0,
            )
        }
        return listOf(me) + altSellers
    }

    /** All distinct seller storefronts in the marketplace. */
    val allSellers: List<Seller> = listOf(
        sellerTechHub, sellerFashionHouse, sellerSneakerKing, sellerHomeAppliances,
        sellerGlamour, sellerSporting, sellerPearlFresh, sellerAutoParts, sellerUgandaCrafts,
    )

    fun sellersExcluding(id: String): List<Seller> =
        allSellers.filter { it.id != id }

    // ----- Public query helpers -----

    fun productById(productId: String): Product? = products.firstOrNull { it.id == productId }

    fun productFull(productId: String): Product? = productById(productId)?.let { augment(it) }

    fun variantsFor(productId: String): List<ProductVariant> = productFull(productId)?.variants.orEmpty()

    fun stockStatusFor(product: Product): StockStatus = when {
        product.stock <= 0 -> StockStatus.OutOfStock
        product.stock <= 5 -> StockStatus.LowStock
        else -> StockStatus.InStock
    }

    fun lowStockMessage(product: Product): String? = if (product.stock in 1..5) "Only ${product.stock} left" else null

    fun nearbySellersFor(productId: String): List<NearbySeller> = productFull(productId)?.let { buildNearby(it) } ?: emptyList()

    fun deliveryOptionsFor(productId: String): List<DeliveryOption> = productFull(productId)?.let {
        listOf(
            DeliveryOption(id = "${productId}-std", label = "Standard Delivery", etaDaysLabel = "1-3 days", feeUgx = if (it.priceUgx > 100_000) 5_000 else 2_000),
            DeliveryOption(id = "${productId}-exp", label = "Express Delivery", etaDaysLabel = "Same day", feeUgx = 15_000),
        )
    } ?: emptyList()

    fun topReviewsFor(productId: String, limit: Int = 3): List<Review> = reviewsFor(productId).take(limit)

    fun reviewsFor(productId: String): List<Review> {
        val p = products.firstOrNull { it.id == productId } ?: return emptyList()
        val seed = (p.id.hashCode() and 0x7fffffff).toLong()
        val rng = java.util.Random(seed)
        val authors = listOf("Sarah K.", "David M.", "Achieng O.", "Brian N.", "Fatima A.", "Peter L.", "Joyce W.", "Daniel S.", "Rebecca T.", "Moses O.")
        val bodies = listOf(
            "Exactly as described. Delivery was fast and the packaging was solid.",
            "Great value for the price. I have been using it daily and it works perfectly.",
            "Solid build quality. The seller was responsive and shipped within a day.",
            "Worth every shilling. The colour and finish look premium.",
            "My second purchase from this store — happy customer so far!",
            "Does the job. A bit smaller than I expected but still good.",
            "Fast delivery to Kampala. Genuine product, sealed box.",
            "The product is fine, the packaging could be better.",
        )
        val variants = p.variants.map { "${it.axis}: ${it.value}" }.ifEmpty { listOf(null) }
        return (0 until (p.ratingCount.coerceAtMost(12))).map { idx ->
            val rating = (3 + rng.nextInt(3)).coerceAtMost(5)
            Review(
                id = "${p.id}-r-$idx",
                productId = p.id,
                authorName = authors[idx % authors.size],
                authorAvatarUrl = null,
                rating = rating,
                dateLabel = listOf("Just now", "2 days ago", "1 week ago", "2 weeks ago", "1 month ago", "2 months ago").let { it[idx % it.size] },
                text = bodies[idx % bodies.size],
                variantLabel = variants[idx % variants.size],
                verifiedPurchase = idx % 3 != 0,
            )
        }
    }

    fun ratingDistributionFor(productId: String): RatingDistribution {
        val reviews = reviewsFor(productId)
        val dist = IntArray(6)
        for (r in reviews) dist[r.rating.coerceIn(1, 5)]++
        return RatingDistribution(five = dist[5], four = dist[4], three = dist[3], two = dist[2], one = dist[1])
    }

    fun similarProducts(productId: String, limit: Int = 6): List<Product> {
        val p = productById(productId) ?: return emptyList()
        return products.filter { it.id != productId && it.category == p.category }
            .map { augment(it) }.take(limit)
    }

    fun recommendedProducts(productId: String?, limit: Int = 8): List<Product> =
        products.filter { it.id != productId }
            .sortedByDescending { it.rating * 1000 + it.ratingCount }
            .map { augment(it) }.take(limit)

    fun topReviewed(limit: Int = 12): List<Product> =
        products.sortedByDescending { it.rating * 1000 + it.ratingCount }
            .map { augment(it) }.take(limit)

    fun topSelling(limit: Int = 5): List<Product> =
        products.sortedByDescending { (it.oldPriceUgx ?: it.priceUgx) - it.priceUgx }
            .map { augment(it) }.take(limit)

    fun productsBySeller(sellerId: String): List<Product> =
        products.filter { it.seller.id == sellerId }.map { augment(it) }

    fun storefront(sellerId: String): SellerStorefront? {
        val seller = products.map { it.seller }.firstOrNull { it.id == sellerId } ?: return null
        val sellerProducts = productsBySeller(sellerId)
        val reviews = sellerProducts.flatMap { reviewsFor(it.id) }
        val cats = sellerProducts.groupBy { it.category }
            .map { (cat, ps) -> SellerCategoryRow(cat, ps.size) }
            .sortedByDescending { it.productCount }
        return SellerStorefront(
            seller = seller,
            followers = (seller.id.hashCode() and 0x7fffffff) % 5000 + 200,
            isFollowed = followedSellers.value.contains(sellerId),
            description = "Premium ${seller.name} products delivered across Uganda. Quality guaranteed by ScottsTechX.",
            location = seller.location,
            productCount = sellerProducts.size,
            rating = sellerProducts.map { it.rating }.average().toFloat().takeIf { !it.isNaN() } ?: 4.5f,
            reviewCount = reviews.size,
            responseRateLabel = "Usually responds within 1 hour",
            products = sellerProducts,
            categories = cats,
            reviews = reviews.sortedByDescending { it.rating }.take(20),
        )
    }

    fun storeReviews(sellerId: String): List<Review> =
        productsBySeller(sellerId).flatMap { reviewsFor(it.id) }.sortedByDescending { it.rating }

    /** All distinct seller storefronts in the marketplace. Used by the AI Nearby tool. */
    fun allStores(): List<SellerStorefront> {
        val sellerIds = products.map { it.seller.id }.distinct()
        return sellerIds.mapNotNull { storefront(it) }
    }

    // ----- Seller follow state -----
    private val followedSellers = MutableStateFlow<Set<String>>(setOf("uganda-crafts"))
    val followedSellersFlow: StateFlow<Set<String>> = followedSellers.asStateFlow()
    fun toggleFollowSeller(sellerId: String): Boolean {
        var nowFollowing = false
        followedSellers.update { current ->
            nowFollowing = !current.contains(sellerId)
            if (nowFollowing) current + sellerId else current - sellerId
        }
        return nowFollowing
    }
    fun isFollowing(sellerId: String): Boolean = followedSellers.value.contains(sellerId)

    // ----- Messages -----
    private val _threads = MutableStateFlow<List<MessageThread>>(listOf(
        MessageThread(
            id = "thread-tech-hub-1",
            sellerId = "tech-hub",
            sellerName = "Tech Hub Uganda",
            productId = "p-samsung-a15",
            productName = "Samsung Galaxy A15",
            lastMessage = "Sure, we have the black one in stock. Delivery tomorrow.",
            lastTimeLabel = "2m ago",
            unread = 1,
        ),
        MessageThread(
            id = "thread-sneaker-king-1",
            sellerId = "sneaker-king",
            sellerName = "Sneaker King",
            productId = "p-nike-airmax",
            productName = "Nike Air Max 270",
            lastMessage = "Yes size 42 is available. Order now?",
            lastTimeLabel = "1h ago",
            unread = 0,
        ),
    ))
    val threadsFlow: StateFlow<List<MessageThread>> = _threads.asStateFlow()
    private val _messages = MutableStateFlow<Map<String, List<Message>>>(
        mapOf(
            "thread-tech-hub-1" to listOf(
                Message("m1", "u-buyer", "You", "Hi! Is the Samsung A15 still in stock?", "10:22 AM", isFromBuyer = true, productContextId = "p-samsung-a15"),
                Message("m2", "tech-hub", "Tech Hub Uganda", "Yes, we have the black one. Would you like 128GB or 256GB?", "10:24 AM", isFromBuyer = false),
                Message("m3", "u-buyer", "You", "128GB please. Can I pay on delivery?", "10:25 AM", isFromBuyer = true),
                Message("m4", "tech-hub", "Tech Hub Uganda", "Sure, we have the black one in stock. Delivery tomorrow.", "10:27 AM", isFromBuyer = false),
            ),
            "thread-sneaker-king-1" to listOf(
                Message("m5", "u-buyer", "You", "Are the Nike Air Max 270 in size 42?", "9:00 AM", isFromBuyer = true, productContextId = "p-nike-airmax"),
                Message("m6", "sneaker-king", "Sneaker King", "Yes size 42 is available. Order now?", "9:10 AM", isFromBuyer = false),
            ),
        )
    )

    fun threadById(threadId: String): MessageThread? = _threads.value.firstOrNull { it.id == threadId }
    fun threadsFor(sellerId: String): List<MessageThread> = _threads.value.filter { it.sellerId == sellerId }
    fun messagesIn(threadId: String): List<Message> = _messages.value[threadId].orEmpty()

    fun sendMessage(threadId: String, text: String, isFromBuyer: Boolean = true) {
        val msg = Message(
            id = "${threadId}-${System.currentTimeMillis()}",
            senderId = if (isFromBuyer) "u-buyer" else threadId.split("-")[1],
            senderName = if (isFromBuyer) "You" else (threadById(threadId)?.sellerName ?: "Seller"),
            text = text,
            timeLabel = "Now",
            isFromBuyer = isFromBuyer,
        )
        _messages.update { it + (threadId to ((it[threadId].orEmpty()) + msg)) }
        _threads.update { current ->
            current.map { t -> if (t.id == threadId) t.copy(lastMessage = text, lastTimeLabel = "Now", unread = if (isFromBuyer) 0 else t.unread + 1) else t }
        }
    }

    fun openThreadWith(sellerId: String, productId: String? = null): MessageThread {
        val existing = _threads.value.firstOrNull { it.sellerId == sellerId && it.productId == productId }
        if (existing != null) return existing
        val seller = products.map { it.seller }.firstOrNull { it.id == sellerId }
        val product = productId?.let { productById(it) }
        val thread = MessageThread(
            id = "thread-${sellerId}-${productId ?: "general"}-${System.currentTimeMillis()}",
            sellerId = sellerId,
            sellerName = seller?.name ?: "Seller",
            productId = productId,
            productName = product?.name,
            lastMessage = "Started a new conversation.",
            lastTimeLabel = "Now",
            unread = 0,
        )
        _threads.update { it + thread }
        if (!_messages.value.containsKey(thread.id)) {
            _messages.update { it + (thread.id to emptyList()) }
        }
        return thread
    }

    fun searchProducts(query: String): List<Product> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return products.map { augment(it) }
        return products.filter { p ->
            p.name.lowercase().contains(q) ||
            p.description.lowercase().contains(q) ||
            p.brand.name.lowercase().contains(q) ||
            p.category.displayName.lowercase().contains(q) ||
            p.seller.name.lowercase().contains(q)
        }.map { augment(it) }
    }

    fun count(): Int = products.size

}
