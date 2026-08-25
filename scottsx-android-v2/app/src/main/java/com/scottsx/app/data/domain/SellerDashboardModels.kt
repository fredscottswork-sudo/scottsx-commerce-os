package com.scottsx.app.data.domain

/**
 * Real-data models for the seller dashboard.
 *
 * These mirror the backend response shapes 1:1
 * (12_Backend/src/modules/seller/seller-public.route.ts and
 * 12_Backend/src/modules/products/products.route.ts). They are the
 * only numbers the seller home screen is allowed to display — the
 * in-memory SellerDataSource seed data is gone from the dashboard.
 */

/**
 * `GET /api/v1/seller/dashboard/stats` → `stats`.
 * All aggregates are computed by Postgres over the seller's real
 * orders and products — nothing here is derived on the device.
 */
data class SellerStats(
    val revenueUgx: Long,        // lifetime revenue (paid/shipped/delivered orders)
    val revenue30Ugx: Long,      // trailing 30-day revenue
    val orders: Int,             // lifetime paid orders
    val orders30: Int,           // trailing 30-day orders
    val avgOrderValueUgx: Long,  // backend AVG(price_minor * quantity)
    val totalProducts: Int,
    val lowStock: Int,           // listings with stock_quantity <= 5
    val outOfStock: Int,         // listings with stock_quantity = 0
    val topProduct: String?,
    val unreadMessages: Int,     // unread messages in seller conversations
    val followers: Int,          // favorite_sellers rows
    val totalViews: Int,         // SUM(view_count) across listings
    val draft: Int,
    val pending: Int,
    val approved: Int,
    val rejected: Int,
    val suspended: Int,
    val pendingApproval: Int,
)

/** One row of `topProducts` — `{ title, sold }`. */
data class SellerTopProduct(
    val title: String,
    val sold: Int,
)

/**
 * One row of `recentOrders` — `{ id, buyerId, productTitle, amount,
 * quantity, status, createdAt, buyerName }`. `status` is one of the
 * backend order states: pending | paid | shipped | delivered |
 * cancelled | refunded.
 */
data class SellerRecentOrder(
    val id: String,
    val buyerId: String,
    val productTitle: String,
    val amount: Long,
    val quantity: Int,
    val status: String,
    val createdAt: String,
    val buyerName: String,
) {
    val buyerInitial: String
        get() = buyerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    /** Maps a raw backend status to the dashboard's display state. */
    val displayStatus: String
        get() = when (status) {
            "pending" -> "Pending"
            "paid" -> "Paid"
            "shipped" -> "Shipped"
            "delivered" -> "Delivered"
            "cancelled" -> "Cancelled"
            "refunded" -> "Refunded"
            else -> status.replaceFirstChar { it.uppercase() }
        }
}

/**
 * One row of `salesSeries` — exactly 14 entries (yesterday-13 → today),
 * each `{ date: "YYYY-MM-DD", orders: int, revenue: number }`.
 */
data class SellerSalesPoint(
    val date: String,
    val orders: Int,
    val revenue: Long,
)

/**
 * Full `GET /api/v1/seller/dashboard/stats` payload:
 * `{ stats, topProducts, recentOrders, salesSeries }`.
 */
data class SellerDashboardData(
    val stats: SellerStats,
    val topProducts: List<SellerTopProduct>,
    val recentOrders: List<SellerRecentOrder>,
    val salesSeries: List<SellerSalesPoint>,
)

/**
 * One row of `GET /api/v1/seller/orders` → `{ orders: [...] }`:
 * `{ id, buyerId, title, amount, quantity, status, createdAt, buyerName }`.
 *
 * Backend order statuses: pending, paid, shipped, delivered,
 * cancelled, refunded.
 */
data class SellerApiOrder(
    val id: String,
    val buyerId: String,
    val title: String,
    val amount: Long,
    val quantity: Int,
    val status: String,
    val createdAt: String,
    val buyerName: String,
) {
    val buyerInitial: String
        get() = buyerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    /** Maps a raw backend status to the dashboard's display state. */
    val displayStatus: String
        get() = when (status) {
            "pending" -> "Pending"
            "paid" -> "Paid"
            "shipped" -> "Shipped"
            "delivered" -> "Delivered"
            "cancelled" -> "Cancelled"
            "refunded" -> "Refunded"
            else -> status.replaceFirstChar { it.uppercase() }
        }
}

/**
 * `GET /api/v1/seller/products?status=` →
 * `{ products: [...], counts: {draft,pending,approved,rejected,suspended} }`.
 * Products decode with the same mapper as the public catalogue, so
 * they carry the full [Product] shape (status, stock, gallery, ...).
 */
data class SellerProductList(
    val products: List<Product>,
    val counts: Map<String, Int>,
) {
    val lowStockProducts: List<Product>
        get() = products.filter { it.stock in 1..5 }

    val outOfStockProducts: List<Product>
        get() = products.filter { it.stock == 0 }
}
