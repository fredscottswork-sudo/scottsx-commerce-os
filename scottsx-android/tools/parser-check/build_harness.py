#!/usr/bin/env python3
"""
Builds a runnable Kotlin harness from the SHIPPING model + client source.

Slicing the real files (rather than keeping a copy) guarantees the harness
can never drift away from the code the app actually uses.

    build_harness.py <MarketplaceModels.kt> <V2Client.kt> <outDir>
"""
import sys

models_path, client_path, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
models = open(models_path).read()
client = open(client_path).read()


def slice_between(text, start, end):
    i = text.index(start)
    j = text.index(end, i)
    return text[i:j]


# jsonToProduct refers to the domain classes with a fully-qualified
# com.scottsx.app.data.domain prefix; the sliced classes below land in the
# harness's default package, so strip the prefix to bind them together.
json_to_product = slice_between(
    client, 'private fun jsonToProduct(', '    suspend fun fetchUserProfile',
).replace('com.scottsx.app.data.domain.', '')

parts = [
    # Domain types the decoder needs: ProductCategory (+ fromApiName),
    # Brand, Seller, Product.
    slice_between(models, 'enum class ProductCategory(', 'data class HeroBanner('),
    # The Stage-3 nested types Product references by default:
    # ProductImage, ProductVariant, ProductSpec.
    slice_between(models, 'data class ProductImage(', 'data class Review('),
    # The shipping catalogue decoder, straight out of V2Client.
    json_to_product.rstrip() + '\n',
]

harness = '''import org.json.JSONArray
import org.json.JSONObject

''' + '\n'.join(parts) + '''

fun main(args: Array<String>) {
    val dir = if (args.isNotEmpty()) args[0] else "/tmp"
    var problems = 0

    fun flag(msg: String) { println("  !! " + msg); problems++ }

    // ── Regression guard: /products is an OBJECT envelope ────────────
    // The old client parsed the body as a bare JSONArray and therefore
    // always came home empty. If anyone reverts that, this flag fires.
    val raw = java.io.File("$dir/products.json").readText()
    val envelope = try {
        JSONObject(raw)
    } catch (t: Throwable) {
        flag("/products is not a JSON object (bare-array client bug is back?)")
        println("PARSER CHECK FAILED"); kotlin.system.exitProcess(1)
        return
    }
    val arr = envelope.optJSONArray("products")
    if (arr == null) { flag("envelope has no \\"products\\" array") }
    val rows = arr ?: JSONArray()

    println("catalogue rows: ${rows.length()}")
    if (rows.length() < 20) flag("expected at least the 20 seeded products, got ${rows.length()}")

    var sawRated = false
    var sawSeller = false
    var sawSale = false
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i)
        if (row == null) { flag("row $i is not an object"); continue }
        val p = jsonToProduct(row)

        if (p.id.isBlank()) flag("row $i: blank id")
        if (p.name.isBlank()) flag("row $i: blank title")
        if (p.priceUgx <= 0L) flag("row $i (${p.name}): price parsed as ${p.priceUgx}")
        if (p.brand.name.isBlank()) flag("row $i: blank brand")

        // Real seller mapping — the old ghost fields (sellerId /
        // sellerBusinessName) never existed and silently produced the
        // hardcoded fallback seller for every row.
        row.optJSONObject("seller")?.let { s ->
            sawSeller = true
            if (p.seller.id != s.optString("id")) flag("row $i: seller id not mapped")
            if (p.seller.name.isBlank()) flag("row $i: seller name blank despite object")
        }

        // The rating must come from `rating`, not the long-gone
        // `productTrustScore` ghost field (which hard-coded 4.4 everywhere).
        if (p.rating > 0f) sawRated = true
        if (row.optDouble("rating", -1.0) > 0.0 && p.rating <= 0f) {
            flag("row $i: rating present in JSON (${row.optDouble("rating")}) but parsed as ${p.rating}")
        }

        // oldPriceMinor arrives as JSON null on most rows — must not crash
        // and must not produce an "old" price below the live one.
        p.oldPriceUgx?.let { old ->
            sawSale = true
            if (old <= p.priceUgx) flag("row $i: oldPriceUgx $old not above priceUgx ${p.priceUgx}")
        }

        if (p.stock < 0) flag("row $i: negative stock")
        if (p.discountPercent !in 0..100) flag("row $i: discount ${p.discountPercent} out of range")
    }

    if (!sawSeller) flag("no row carried the nested seller object — feed contract changed?")
    if (!sawRated) flag("every product parsed with rating 0 — ghost rating field regression")
    println("sellers mapped: $sawSeller, rated rows: $sawRated, rows with old price: $sawSale")

    if (problems > 0) {
        println("PARSER CHECK FAILED ($problems problem(s))")
        kotlin.system.exitProcess(1)
    }
    println("PARSER CHECK PASSED")
}
'''

with open(f'{out_dir}/Parse.kt', 'w') as fh:
    fh.write(harness)
print('  harness built from the shipping V2Client + domain sources')
