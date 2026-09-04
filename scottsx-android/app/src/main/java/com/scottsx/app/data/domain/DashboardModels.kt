package com.scottsx.app.data.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON helpers the dashboard parsers rely on. `optString` in org.json returns
 * the string "null" (not Kotlin null) when the key is JSON null, so callers
 * that must distinguish "no value" from the literal string use these.
 */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key)

/** `optStringOrNull` but with a fallback when the key is absent or null. */
internal fun JSONObject.optStringSafe(key: String, fallback: String = ""): String =
    optStringOrNull(key) ?: fallback

/**
 * One day of the 14-day sales series (seller + admin dashboards). Named
 * separately from the legacy [SalesPoint] chart model so both can exist in
 * the same module; this one mirrors `web/src/api/types.ts` 1:1.
 */
data class DashboardSalesPoint(
    val date: String = "",
    val orders: Int = 0,
    val revenue: Long = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): DashboardSalesPoint = DashboardSalesPoint(
            date = o.optString("date"),
            orders = o.optInt("orders", 0),
            revenue = o.optLong("revenue", 0),
        )

        fun fromJsonArray(arr: JSONArray?): List<DashboardSalesPoint> =
            if (arr == null) emptyList()
            else (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }
}

data class TopProduct(
    val title: String = "",
    val sold: Int = 0,
) {
    companion object {
        fun fromJsonArray(arr: JSONArray?): List<TopProduct> =
            if (arr == null) emptyList()
            else (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                TopProduct(title = o.optStringSafe("title"), sold = o.optInt("sold", 0))
            }
    }
}

data class SellerRecentOrder(
    val id: String = "",
    val buyerId: String = "",
    val productTitle: String = "",
    val amount: Long = 0,
    val quantity: Int = 1,
    val status: String = "pending",
    val createdAt: String = "",
    val buyerName: String = "Buyer",
) {
    companion object {
        fun fromJsonArray(arr: JSONArray?): List<SellerRecentOrder> =
            if (arr == null) emptyList()
            else (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SellerRecentOrder(
                    id = o.optString("id"),
                    buyerId = o.optString("buyerId"),
                    productTitle = o.optStringSafe("productTitle"),
                    amount = o.optLong("amount", 0),
                    quantity = o.optInt("quantity", 1),
                    status = o.optStringSafe("status", "pending"),
                    createdAt = o.optString("createdAt"),
                    buyerName = o.optStringSafe("buyerName", "Buyer"),
                )
            }
    }
}

/**
 * Full payload of GET /seller/dashboard/stats — the same object the web
 * seller dashboard renders (stats + topProducts + recentOrders + salesSeries).
 */
data class SellerDashboard(
    val revenueUgx: Long = 0,
    val revenue30Ugx: Long = 0,
    val orders: Int = 0,
    val orders30: Int = 0,
    val avgOrderValueUgx: Long = 0,
    val totalProducts: Int = 0,
    val lowStock: Int = 0,
    val outOfStock: Int = 0,
    val topProduct: String? = null,
    val unreadMessages: Int = 0,
    val followers: Int = 0,
    val totalViews: Int = 0,
    val productsByStatus: Map<String, Int> = emptyMap(),
    val pendingApproval: Int = 0,
    val topProducts: List<TopProduct> = emptyList(),
    val recentOrders: List<SellerRecentOrder> = emptyList(),
    val salesSeries: List<DashboardSalesPoint> = emptyList(),
) {
    companion object {
        fun fromJson(root: JSONObject): SellerDashboard {
            val s = root.optJSONObject("stats") ?: JSONObject()
            val byStatus = mutableMapOf<String, Int>()
            s.optJSONObject("productsByStatus")?.let { obj ->
                obj.keys().forEach { k -> byStatus[k] = obj.optInt(k, 0) }
            }
            return SellerDashboard(
                revenueUgx = s.optLong("revenueUgx", 0),
                revenue30Ugx = s.optLong("revenue30Ugx", 0),
                orders = s.optInt("orders", 0),
                orders30 = s.optInt("orders30", 0),
                avgOrderValueUgx = s.optLong("avgOrderValueUgx", 0),
                totalProducts = s.optInt("totalProducts", 0),
                lowStock = s.optInt("lowStock", 0),
                outOfStock = s.optInt("outOfStock", 0),
                topProduct = s.optStringOrNull("topProduct"),
                unreadMessages = s.optInt("unreadMessages", 0),
                followers = s.optInt("followers", 0),
                totalViews = s.optInt("totalViews", 0),
                productsByStatus = byStatus,
                pendingApproval = s.optInt("pendingApproval", 0),
                topProducts = TopProduct.fromJsonArray(root.optJSONArray("topProducts")),
                recentOrders = SellerRecentOrder.fromJsonArray(root.optJSONArray("recentOrders")),
                salesSeries = DashboardSalesPoint.fromJsonArray(root.optJSONArray("salesSeries")),
            )
        }
    }
}

/** GET /seller/location — live pin + open/closed state. */
data class SellerLocationState(
    val lat: Double? = null,
    val lng: Double? = null,
    val sharing: Boolean = false,
    val updatedAt: String? = null,
    val isOpen: Boolean = true,
) {
    companion object {
        fun fromJson(o: JSONObject?): SellerLocationState {
            if (o == null) return SellerLocationState()
            return SellerLocationState(
                lat = if (o.isNull("lat")) null else o.optDouble("lat"),
                lng = if (o.isNull("lng")) null else o.optDouble("lng"),
                sharing = o.optBoolean("sharing", false),
                updatedAt = o.optStringOrNull("updatedAt"),
                isOpen = if (o.isNull("isOpen")) true else o.optBoolean("isOpen", true),
            )
        }
    }
}
