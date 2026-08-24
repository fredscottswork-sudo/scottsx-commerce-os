package com.scottsx.app.data

import com.scottsx.app.data.domain.LowStockAlert
import com.scottsx.app.data.domain.OrderStatus
import com.scottsx.app.data.domain.SalesPoint
import com.scottsx.app.data.domain.SellerAiInsight
import com.scottsx.app.data.domain.SellerDashboardSnapshot
import com.scottsx.app.data.domain.SellerOrder
import com.scottsx.app.data.domain.SellerOrdersOverview
import com.scottsx.app.data.domain.StoreStatus

/**
 * Single source of truth for the seller dashboard.
 *
 * For Stage 3.2 the snapshot is generated from sample data so the
 * UI builds end-to-end. Once the Fastify backend exposes a
 * `GET /api/v1/seller/dashboard` endpoint, swap the [snapshot]
 * factory for a real network call. The data shape stays identical
 * so the UI never has to change.
 */
object SellerDataSource {

    /** Toggle the in-memory status. The UI binds to the snapshot flow. */
    private var currentStatus: StoreStatus = StoreStatus.Online

    fun setStatus(status: StoreStatus) {
        currentStatus = status
    }

    fun currentStatus(): StoreStatus = currentStatus

    fun snapshot(
        displayName: String = "Fred Scotts",
        email: String = "[email protected]",
    ): SellerDashboardSnapshot {
        return SellerDashboardSnapshot(
            displayName = displayName,
            storeName = "ScottsTechX Store",
            storeId = "STX-4587",
            email = email,
            status = currentStatus,
            salesTodayUgx = 850_000L,
            salesTodayDeltaPct = 12.4f,
            ordersToday = 24,
            ordersTodayDelta = 8,
            customersTotal = 128,
            customersDelta = 6,
            rating = 4.8f,
            ratingLabel = "Excellent",
            ordersOverview = SellerOrdersOverview(
                pending = 8,
                processing = 5,
                ready = 3,
                completed = 42,
            ),
            recentOrders = sampleOrders(),
            sales = sampleWeek(),
            aiInsight = SellerAiInsight(
                headline = "Your sales are up 18% this week",
                body = "Samsung Galaxy A55 is your best-performing product this period. Stock up before Friday — best day for conversion.",
                bestProduct = "Samsung Galaxy A55",
                trendLabel = "+18% this week",
            ),
            lowStock = listOf(
                LowStockAlert(productId = "p-104", productName = "iPhone 13 Case", remaining = 3, threshold = 5),
                LowStockAlert(productId = "p-110", productName = "USB-C Braided Cable", remaining = 2, threshold = 10),
            ),
        )
    }

    private fun sampleOrders(): List<SellerOrder> = listOf(
        SellerOrder(
            id = "STX-10482",
            productName = "Samsung Galaxy A55",
            itemsCount = 2,
            totalUgx = 1_450_000L,
            placedAtLabel = "10:30 AM",
            status = OrderStatus.Pending,
            buyerName = "Sarah K.",
        ),
        SellerOrder(
            id = "STX-10481",
            productName = "Nike Air Max 270",
            itemsCount = 1,
            totalUgx = 320_000L,
            placedAtLabel = "9:18 AM",
            status = OrderStatus.Processing,
            buyerName = "Daniel M.",
        ),
        SellerOrder(
            id = "STX-10480",
            productName = "HP Pavilion 15 Laptop",
            itemsCount = 1,
            totalUgx = 2_250_000L,
            placedAtLabel = "Yesterday",
            status = OrderStatus.Ready,
            buyerName = "Achieng L.",
        ),
        SellerOrder(
            id = "STX-10479",
            productName = "Logitech MX Master 3S",
            itemsCount = 1,
            totalUgx = 480_000L,
            placedAtLabel = "Yesterday",
            status = OrderStatus.Completed,
            buyerName = "Robert O.",
        ),
        SellerOrder(
            id = "STX-10478",
            productName = "Bose QuietComfort 45",
            itemsCount = 1,
            totalUgx = 1_100_000L,
            placedAtLabel = "2 days ago",
            status = OrderStatus.Completed,
            buyerName = "Mary N.",
        ),
    )

    private fun sampleWeek(): List<SalesPoint> = listOf(
        SalesPoint("Mon", 620_000L),
        SalesPoint("Tue", 740_000L),
        SalesPoint("Wed", 580_000L),
        SalesPoint("Thu", 810_000L),
        SalesPoint("Fri", 920_000L),
        SalesPoint("Sat", 1_080_000L),
        SalesPoint("Sun", 670_000L),
    )

    /** Used by the AppNavigation's recent-orders detail panel. */
    fun findOrder(id: String): SellerOrder? = sampleOrders().firstOrNull { it.id == id }

    /** Used by the Products screen for the seller's listings. */
    fun sellerProducts(): List<com.scottsx.app.data.domain.Product> =
        MarketplaceDataSource.allProducts.take(8)
}
