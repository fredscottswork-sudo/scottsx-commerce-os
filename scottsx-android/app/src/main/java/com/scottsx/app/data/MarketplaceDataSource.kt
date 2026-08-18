package com.scottsx.app.data

import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.domain.Seller

/**
 * Local fallback catalog used when the API is unreachable (offline / first
 * launch). Uses the SAME real Unsplash URLs as the backend seed — never
 * placeholder text, or Coil silently fails.
 */
object MarketplaceDataSource {

    private fun u(id: String) = "https://images.unsplash.com/$id?w=800"

    private fun seller(name: String, rating: Double, city: String, verified: Boolean = false) =
        Seller(id = name.lowercase().replace(" ", "-"), name = name, rating = rating, location = city, verified = verified)

    private val techHub = seller("Tech Hub Uganda", 4.7, "Kampala", verified = true)
    private val fashionHouse = seller("Fashion House", 4.5, "Entebbe", verified = true)
    private val sneakerKing = seller("Sneaker King", 4.4, "Jinja")
    private val homeBeyond = seller("Home & Beyond", 4.3, "Kampala")
    private val glamour = seller("Glamour Cosmetics", 4.6, "Kampala", verified = true)
    private val ugandaCrafts = seller("Uganda Crafts", 4.8, "Jinja", verified = true)

    private fun p(
        title: String,
        category: String,
        price: Long,
        old: Long?,
        img: String,
        seller: Seller,
        rating: Double,
        count: Int,
        flash: Boolean = false,
        discount: Int = 0,
    ) = Product(
        id = title.lowercase().replace(Regex("[^a-z0-9]+"), "-"),
        title = title,
        description = "Genuine product — inspected before listing. Fast delivery in ${seller.location} and across Uganda, cash on delivery available.",
        priceMinor = price,
        oldPriceMinor = old,
        stockQuantity = 20,
        imageUrl = img,
        category = category,
        brand = seller.name,
        seller = seller,
        rating = rating,
        ratingCount = count,
        isFlashDeal = flash,
        discountPercent = discount,
        location = seller.location,
    )

    val products: List<Product> = listOf(
        // Electronics
        p("Samsung Galaxy A55 5G 128GB", "Electronics", 1_650_000, 1_800_000, u("photo-1610792516775-01de03eae630"), techHub, 4.6, 84),
        p("iPhone 15 Pro 256GB Natural Titanium", "Electronics", 4_500_000, 4_800_000, u("photo-1592286927505-1def25115558"), techHub, 4.9, 121),
        p("MacBook Air M2 13-inch (2024)", "Electronics", 7_800_000, null, u("photo-1517336714731-489689fd1ca8"), techHub, 4.8, 47),
        p("55-inch 4K Smart TV with HDR", "Electronics", 2_900_000, 3_500_000, u("photo-1593359677879-a4bb92f829d1"), homeBeyond, 4.7, 66, flash = true, discount = 17),
        p("Power Bank 20000mAh Fast Charge", "Electronics", 185_000, null, u("photo-1609091839311-d5365f9ff1c5"), techHub, 4.5, 190),
        p("Wireless Headphones with Mic", "Electronics", 320_000, 380_000, u("photo-1505740420928-5e560c06d30e"), techHub, 4.6, 143),
        // Fashion
        p("Ankara Maxi Dress — African Print", "Fashion", 85_000, 105_000, u("photo-1591561954557-26941169b49e"), fashionHouse, 4.7, 58, flash = true, discount = 19),
        p("Kitenge Two-Piece Set", "Fashion", 95_000, null, u("photo-1591561954557-26941169b49e"), fashionHouse, 4.4, 34),
        p("Classic Leather Wristwatch", "Fashion", 240_000, 290_000, u("photo-1523275335684-37898b6baf30"), fashionHouse, 4.3, 71),
        p("Designer Ankara Gown", "Fashion", 130_000, null, u("photo-1591561954557-26941169b49e"), fashionHouse, 4.6, 26),
        // Sports
        p("Nike Air Zoom Running Shoes", "Sports", 420_000, 500_000, u("photo-1542291026-7eec264c27ff"), sneakerKing, 4.8, 132, flash = true, discount = 16),
        p("Adidas Ultraboost Trainers", "Sports", 380_000, null, u("photo-1542291026-7eec264c27ff"), sneakerKing, 4.5, 88),
        p("Basketball High-Tops", "Sports", 350_000, 390_000, u("photo-1542291026-7eec264c27ff"), sneakerKing, 4.2, 41),
        p("Trail Running Sneakers", "Sports", 310_000, null, u("photo-1542291026-7eec264c27ff"), sneakerKing, 4.4, 53),
        // Beauty
        p("Matte Lipstick Set — 6 Shades", "Beauty", 65_000, 82_000, u("photo-1586495777744-4413f21062fa"), glamour, 4.6, 96, flash = true, discount = 21),
        p("Liquid Lipstick Trio", "Beauty", 55_000, null, u("photo-1586495777744-4413f21062fa"), glamour, 4.3, 62),
        p("Shea Butter Beauty Soap", "Beauty", 18_000, 22_000, u("photo-1600857544200-b2f666a9a2ec"), glamour, 4.5, 210),
        p("Skincare Cleansing Bar", "Beauty", 15_000, null, u("photo-1600857544200-b2f666a9a2ec"), glamour, 4.4, 177),
        // Home & Living
        p("Handwoven Storage Basket", "Home & Living", 45_000, 56_000, u("photo-1556909114-f6e7ad7d3136"), ugandaCrafts, 4.9, 78, flash = true, discount = 20),
        p("Rattan Laundry Basket", "Home & Living", 38_000, null, u("photo-1556909114-f6e7ad7d3136"), ugandaCrafts, 4.7, 44),
        // Groceries
        p("Basmati Rice 5kg — Premium", "Groceries", 52_000, 58_000, u("photo-1586201375761-83885001b20f"), homeBeyond, 4.5, 150),
        p("Sunflower Cooking Oil 5L", "Groceries", 68_000, 82_000, u("photo-1474979266404-7eaacbcd87c5"), homeBeyond, 4.4, 118, flash = true, discount = 17),
        // Automotive
        p("17-inch All-Weather Tire", "Automotive", 350_000, null, u("photo-1568844293986-8d0400bd4745"), homeBeyond, 4.2, 19),
        p("SUV All-Terrain Tire Pair", "Automotive", 720_000, 800_000, u("photo-1568844293986-8d0400bd4745"), homeBeyond, 4.1, 12),
    )

    val flashDeals: List<Product> get() = products.filter { it.isFlashDeal }
    val recommended: List<Product> get() = products.filter { it.rating >= 4.4 }
    fun productsByCategory(cat: ProductCategory): List<Product> =
        if (cat == ProductCategory.All) products else products.filter { ProductCategory.fromApiName(it.category) == cat }

    data class HeroBanner(val title: String, val subtitle: String, val emoji: String)
    val heroBanners = listOf(
        HeroBanner("Flash Deals Live", "Up to 24% off today only", "⚡"),
        HeroBanner("Buy from Local Sellers", "Kampala • Entebbe • Jinja • Mbarara", "🇺🇬"),
        HeroBanner("Cash on Delivery", "Pay when it arrives", "💵"),
    )

    data class Benefit(val title: String, val subtitle: String, val emoji: String)
    val benefits = listOf(
        Benefit("Genuine Products", "Every item inspected before listing", "✅"),
        Benefit("Local Sellers", "Real Ugandan stores near you", "📍"),
        Benefit("Mobile Money", "MTN MoMo & Airtel Money", "📲"),
        Benefit("Buyer Protection", "7-day refund window", "🛡️"),
    )
}
