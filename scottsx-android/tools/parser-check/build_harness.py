#!/usr/bin/env python3
"""
Builds a runnable Kotlin harness from the SHIPPING dashboard model source.

Slicing the real file (rather than keeping a copy) guarantees the harness can
never drift away from the code the app actually uses.

    build_harness.py <DashboardModels.kt> <outDir>

The harness covers the parsers that exist in the current data layer —
DashboardSalesPoint, TopProduct, SellerRecentOrder, SellerDashboard and
SellerLocationState from DashboardModels.kt — against the live
`/seller/dashboard/stats` and `/seller/location` payloads captured by
capture.mjs. (The old chat/nearby/cart parser module was removed when the app
moved to V2Client inline responders; there is no longer any fromJson code for
those flows, so they are not covered here.)
"""
import sys

models_path, out_dir = sys.argv[1], sys.argv[2]
models = open(models_path).read()


def slice_between(text, start, end):
    i = text.index(start)
    j = text.index(end, i)
    return text[i:j]


parts = [
    # JSON helpers, including optStringSafe / optStringOrNull.
    slice_between(models, 'internal fun JSONObject.optStringOrNull',
                  'data class DashboardSalesPoint('),
    # Dashboard parsers, in file order.
    slice_between(models, 'data class DashboardSalesPoint(', 'data class TopProduct('),
    slice_between(models, 'data class TopProduct(', 'data class SellerRecentOrder('),
    slice_between(models, 'data class SellerRecentOrder(', 'data class SellerDashboard('),
    slice_between(models, 'data class SellerDashboard(', '/** GET /seller/location'),
    # SellerLocationState is the last declaration in the file.
    models[models.index('data class SellerLocationState('):],
]

harness = '''import org.json.JSONArray
import org.json.JSONObject

''' + '\n'.join(parts) + '''

fun main(args: Array<String>) {
    val dir = if (args.isNotEmpty()) args[0] else "/tmp"
    var problems = 0

    fun flag(msg: String) { println("  !! " + msg); problems++ }

    // ── Seller dashboard: stats + topProducts + recentOrders + salesSeries.
    val dash = SellerDashboard.fromJson(
        JSONObject(java.io.File("$dir/dashboard.json").readText()))

    println("dashboard: revenue=" + dash.revenueUgx + " UGX (30d=" + dash.revenue30Ugx + ") " +
            "orders=" + dash.orders + " (30d=" + dash.orders30 + ") " +
            "products=" + dash.totalProducts + " top=" + dash.topProduct +
            " unread=" + dash.unreadMessages + " followers=" + dash.followers)

    if (dash.revenueUgx < 0L) flag("revenueUgx parsed negative: " + dash.revenueUgx)
    if (dash.revenue30Ugx < 0L) flag("revenue30Ugx parsed negative: " + dash.revenue30Ugx)
    if (dash.orders < 0) flag("orders parsed negative: " + dash.orders)
    if (dash.totalProducts <= 0) flag("the seed seller has no products (" + dash.totalProducts + ")")
    if (dash.pendingApproval < 0) flag("pendingApproval parsed negative")
    if (dash.topProduct == null) flag("topProduct is null for a seller with a catalogue")
    if (dash.topProduct == "null") flag("topProduct leaked the string \\"null\\"")

    if (dash.salesSeries.isEmpty()) {
        flag("no sales series parsed")
    } else {
        println("  salesSeries: " + dash.salesSeries.size + " day(s), revenue today=" +
                dash.salesSeries.last().revenue)
        if (dash.salesSeries.size < 14) flag("expected the 14-day series, got " + dash.salesSeries.size)
        for (p in dash.salesSeries) {
            if (p.revenue < 0L) flag("a series point has negative revenue for " + p.date)
            if (p.orders < 0) flag("a series point has negative orders for " + p.date)
        }
    }

    if (dash.topProducts.isEmpty()) {
        flag("topProducts is empty for a seller with a catalogue")
    } else {
        println("  topProducts: " + dash.topProducts.joinToString { "\\"" + it.title + "\\" x" + it.sold })
    }

    println("  recentOrders: " + dash.recentOrders.size + " (validating first)")
    for (o in dash.recentOrders.take(3)) {
        if (o.id.isBlank()) flag("a recent order has no id")
        if (o.amount < 0L) flag("a recent order has a negative amount")
        if (o.status.isBlank()) flag("a recent order has no status")
    }

    // ── Location: live-sharing payload (lat/lng/updatedAt present).
    val loc = JSONObject(java.io.File("$dir/location.json").readText())
    val live = SellerLocationState.fromJson(loc)
    println("location (live): sharing=" + live.sharing + " lat=" + live.lat +
            " lng=" + live.lng + " updatedAt=" + live.updatedAt + " open=" + live.isOpen)
    if (!live.sharing) flag("captured live location reads as sharing=off")
    if (live.lat == null || live.lng == null) flag("live location lost its coordinates")
    if (live.updatedAt == null) flag("live location has no updatedAt")
    if (live.updatedAt == "null") flag("updatedAt leaked the string \\"null\\"")

    // ── Location: the pre-capture state (seed default — usually null lat/lng,
    // sharing off). This is the exact JSON a brand-new seller sees, and the
    // parser must not turn null coordinates into 0.0 or mark the pin live.
    val beforeObj = JSONObject(java.io.File("$dir/location-before.json").readText())
    val beforeLoc = beforeObj.optJSONObject("location")
    val seed = SellerLocationState.fromJson(beforeLoc)
    println("location (before): sharing=" + seed.sharing + " lat=" + seed.lat +
            " lng=" + seed.lng + " open=" + seed.isOpen)
    if (beforeLoc != null && beforeLoc.isNull("lat")) {
        if (seed.lat != null) flag("null lat parsed as " + seed.lat)
        if (seed.lng != null) flag("null lng parsed as " + seed.lng)
    }
    if (beforeLoc != null && !beforeLoc.isNull("sharing") && !beforeLoc.optBoolean("sharing", false)) {
        if (seed.sharing) flag("a sharing=off row parsed as sharing=on")
    }

    // Empty / null input must produce safe defaults, not a crash.
    val none = SellerLocationState.fromJson(null)
    if (none.sharing) flag("null location reads as sharing=on")
    if (none.lat != null || none.lng != null) flag("null location has fake coordinates")

    if (problems == 0) {
        println("\\nOK - every dashboard parser handled the real backend JSON correctly.")
    } else {
        println("\\nFAILED - " + problems + " problem(s)")
        kotlin.system.exitProcess(1)
    }
}
'''

with open(f'{out_dir}/Parse.kt', 'w') as fh:
    fh.write(harness)
print('  harness built from the shipping dashboard model source')
