#!/usr/bin/env python3
"""
Builds a runnable Kotlin harness from the SHIPPING model source.

Slicing the real file (rather than keeping a copy) guarantees the harness can
never drift away from the code the app actually uses.

    build_harness.py <MarketplaceModels.kt> <MessagesScreen.kt> <outDir>
"""
import sys

models_path, screens_path, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
models = open(models_path).read()
screens = open(screens_path).read()


def slice_between(text, start, end):
    i = text.index(start)
    j = text.index(end, i)
    return text[i:j]


parts = [
    # Chat models: OtherParty, Conversation, InboxCounts, Inbox, ChatMessage,
    # Transcript, QuickReplyItem.
    slice_between(models, 'data class OtherParty(', 'data class MessageThread('),
    # JSON helpers, including optStringSafe.
    slice_between(models, 'internal fun JSONObject.optStringOrNull',
                  'internal fun JSONObject.optLongOrNull'),
    'internal fun JSONObject.optLongOrNull(key: String): Long? =\n'
    '    if (isNull(key)) null else optLong(key)\n',
    # The timestamp formatter under test.
    slice_between(screens, 'internal fun chatTimeLabel', '/** Shared inbox UI'),
    # Nearby models: NearbySeller (+positionLabel), Place, NearbyResult.
    slice_between(models, 'data class NearbySeller(',
                  '/** AI search / image-search / voice-search response. */'),
    # Cart models: CartItem (+isUnavailable), Cart, PlacedOrder, CartCheckoutResult.
    slice_between(models, 'data class CartItem(',
                  '// ── small JSON helpers'),
]

harness = '''import org.json.JSONArray
import org.json.JSONObject

''' + '\n'.join(parts) + '''

fun main(args: Array<String>) {
    val dir = if (args.isNotEmpty()) args[0] else "/tmp"
    var problems = 0

    fun flag(msg: String) { println("  !! " + msg); problems++ }

    val inbox = JSONObject(java.io.File("$dir/inbox.json").readText())
    val counts = InboxCounts.fromJson(inbox.getJSONObject("counts"))
    println("counts: all=${counts.all} unread=${counts.unread} offers=${counts.offers} " +
            "pinned=${counts.pinned} archived=${counts.archived}")

    val arr = inbox.getJSONArray("conversations")
    println("\\nconversations (${arr.length()}):")
    var sawBlankTimestamp = false
    for (i in 0 until arr.length()) {
        val c = Conversation.fromJson(arr.getJSONObject(i))
        val label = chatTimeLabel(c.lastTime)
        if (c.lastTime.isBlank()) sawBlankTimestamp = true
        if (c.lastTime == "null") flag("lastTime leaked the string \\"null\\"")
        if (c.lastMessage == "null") flag("lastMessage leaked the string \\"null\\"")
        if (label == "null") flag("chatTimeLabel produced \\"null\\"")
        println("  %-24s time=%-26s label=%-10s msg=%s".format(
            c.otherParty.name.take(24), "\\"" + c.lastTime + "\\"",
            "\\"" + label + "\\"", "\\"" + c.lastMessage.take(20) + "\\""))
    }
    if (!sawBlankTimestamp) {
        println("  (note: no message-less thread in this capture — the null-timestamp " +
                "path was not exercised)")
    }

    val tx = JSONObject(java.io.File("$dir/transcript.json").readText())
    val msgs = tx.getJSONArray("messages")
    println("\\nmessages (${msgs.length()}):")
    for (i in 0 until msgs.length()) {
        val m = ChatMessage.fromJson(msgs.getJSONObject(i))
        if (m.text == "null") flag("message text leaked the string \\"null\\"")
        println("  kind=%-7s offer=%-10s status=%-9s qty=%d read=%b text=%s".format(
            m.kind, m.offerMinor?.toString() ?: "-", m.offerStatus ?: "-",
            m.offerQuantity, m.readByOther, "\\"" + m.text.take(24) + "\\""))
    }

    val offer = (0 until msgs.length())
        .map { ChatMessage.fromJson(msgs.getJSONObject(it)) }
        .firstOrNull { it.isOffer }
    if (offer == null) {
        flag("no offer message found in the capture")
    } else {
        val minor = offer.offerMinor ?: 0L
        println("\\noffer parses to UGX " + (minor / 100) + " (minor=" + minor + ")")
        if (minor != 45000000L) flag("offerMinor should be 45000000, got " + minor)
    }

    val head = JSONObject(java.io.File("$dir/head.json").readText())
    val hc = Conversation.fromJson(head.getJSONObject("conversation"))
    println("head: ${hc.otherParty.name} verified=${hc.otherParty.verified} " +
            "mySide=${hc.mySide} price=${hc.productPriceMinor}")
    if (hc.productPriceMinor == null || hc.productPriceMinor == 0L) {
        flag("productPriceMinor failed to parse (bigint returned as a string?)")
    }
    if (hc.otherParty.name == "null") flag("otherParty.name leaked \\"null\\"")

    // ── Nearby: the app now queries with no radius, so these are the exact
    // payloads NearbyScreen renders.
    fun readNearby(name: String): NearbyResult {
        val o = JSONObject(java.io.File("$dir/$name").readText())
        val arr = o.optJSONArray("sellers") ?: org.json.JSONArray()
        return NearbyResult(
            sellers = (0 until arr.length()).map { NearbySeller.fromJson(arr.getJSONObject(it)) },
            count = o.optInt("count", 0),
            total = o.optInt("total", o.optInt("count", 0)),
            liveCount = o.optInt("liveCount", 0),
            place = o.optJSONObject("place")?.let { Place.fromJson(it) },
        )
    }

    val near = readNearby("nearby.json")
    println("\\nnearby from Kampala: count=${near.count} total=${near.total} live=${near.liveCount}")
    if (near.sellers.isEmpty()) flag("no sellers parsed from the nearby capture")
    if (near.total < near.sellers.size) flag("total (${near.total}) is below the page size")

    val here = near.place
    if (here == null) {
        flag("place was not parsed — the app would show no location name")
    } else {
        println("place: label=\\"${here.label}\\" short=\\"${here.shortLabel}\\" " +
                "city=${here.city} region=${here.region} country=${here.country} " +
                "cc=${here.countryCode} acc=${here.accuracyKm}km")
        if (here.label.isBlank()) flag("place.label is blank")
        if (here.label == "null" || here.shortLabel == "null") flag("place leaked the string \\"null\\"")
        if (here.country != "Uganda") flag("expected Uganda for Kampala, got ${here.country}")
    }

    println("\\nnearest stores:")
    var lastKm = -1.0
    for (s in near.sellers.take(6)) {
        if (s.name == "null" || s.placeLabel == "null") flag("seller strings leaked \\"null\\"")
        if (s.distanceKm < lastKm) flag("sellers are not sorted by distance (${s.distanceKm} after $lastKm)")
        lastKm = s.distanceKm
        println("  %-22s %7.1f km  %-28s %s".format(
            s.name.take(22), s.distanceKm, s.placeLabel.take(28), s.positionLabel))
    }

    // Every seed store has sharing off, so each pin must read as last-known
    // rather than pretending to be live.
    val notLive = near.sellers.filter { !it.live }
    if (notLive.any { it.positionLabel == "Live now" }) {
        flag("a store that is not live is labelled \\"Live now\\"")
    }

    // A distant origin must still return the nearest stores globally, because
    // the query no longer has a radius.
    val far = readNearby("nearby-far.json")
    println("\\nfrom London: count=${far.count} total=${far.total} place=\\"${far.place?.label}\\"")
    if (far.sellers.isEmpty()) {
        flag("a distant buyer saw NO stores — the radius filter is still being applied")
    } else {
        val nearest = far.sellers.first()
        println("  nearest: ${nearest.name} at ${nearest.distanceKm} km")
        if (nearest.distanceKm < 1000) flag("expected a very distant store, got ${nearest.distanceKm} km")
    }
    if (far.place?.country != "United Kingdom") {
        flag("expected United Kingdom for London, got ${far.place?.country}")
    }

    // ── Cart: the flow the app uses to actually buy something.
    val cart = Cart.fromJson(JSONObject(java.io.File("$dir/cart.json").readText()))
    println("\\ncart: ${cart.items.size} line(s), itemCount=${cart.itemCount} " +
            "subtotal=${cart.subtotalMinor} ${cart.currency}")
    if (cart.items.isEmpty()) flag("no cart lines parsed")
    if (cart.itemCount != 3) flag("expected 3 units in the cart, got ${cart.itemCount}")

    var computed = 0L
    for (line in cart.items) {
        println("  %-38s x%d  %9d  stock=%d  %s".format(
            line.title.take(38), line.quantity, line.lineTotalMinor,
            line.stockQuantity, line.sellerName.take(18)))
        if (line.title == "null" || line.sellerName == "null") flag("cart strings leaked \\"null\\"")
        if (line.title.isBlank()) flag("a cart line has no title")
        if (line.priceMinor <= 0L) flag("a cart line has no price (bigint parsed as a string?)")
        if (line.lineTotalMinor != line.priceMinor * line.quantity) {
            flag("lineTotalMinor != price x quantity for ${line.title}")
        }
        if (line.isUnavailable) flag("a freshly added approved line reads as unavailable")
        computed += line.lineTotalMinor
    }
    if (computed != cart.subtotalMinor) {
        flag("subtotal ${cart.subtotalMinor} does not match the lines ($computed)")
    }

    // A suspended line must read as unavailable, or the UI would happily let
    // the buyer try to check out something moderation has pulled.
    val pulled = Cart.fromJson(JSONObject(java.io.File("$dir/cart-suspended.json").readText()))
    val pulledLine = pulled.items.firstOrNull { it.status != "approved" }
    if (pulledLine == null) {
        flag("the capture has no suspended cart line — the unavailable path is untested")
    } else {
        println("\\nsuspended line: ${pulledLine.title.take(34)} status=${pulledLine.status} " +
                "unavailable=${pulledLine.isUnavailable}")
        if (!pulledLine.isUnavailable) flag("a suspended product does NOT read as unavailable")
    }

    val done = CartCheckoutResult.fromJson(
        JSONObject(java.io.File("$dir/cart-checkout.json").readText()))
    println("\\ncheckout: ${done.orderCount} order(s), total=${done.totalMinor} mode=${done.paymentMode}")
    for (o in done.orders) {
        println("  order ${o.id.take(8)} ${o.title.take(32)} x${o.quantity} = ${o.amountMinor} (${o.status})")
        if (o.id.isBlank()) flag("an order came back without an id")
        if (o.amountMinor <= 0L) flag("an order has no amount")
    }
    if (done.orders.isEmpty()) flag("checkout returned no orders")
    // Two sellers in the cart must become two orders.
    if (done.orderCount != 2) flag("expected 2 orders (one per seller), got ${done.orderCount}")
    if (done.paymentMode != "cod") flag("expected cash on delivery, got ${done.paymentMode}")

    val emptied = Cart.fromJson(JSONObject(java.io.File("$dir/cart-empty.json").readText()))
    if (!emptied.isEmpty) flag("the cart was not emptied by checkout")
    if (emptied.itemCount != 0) flag("itemCount should be 0 after checkout, got ${emptied.itemCount}")
    println("cart after checkout: empty=${emptied.isEmpty}")

    if (problems == 0) {
        println("\\nOK - every parser handled the real backend JSON correctly.")
    } else {
        println("\\nFAILED - " + problems + " problem(s)")
        kotlin.system.exitProcess(1)
    }
}
'''

with open(f'{out_dir}/Parse.kt', 'w') as fh:
    fh.write(harness)
print('  harness built from the shipping model source')
