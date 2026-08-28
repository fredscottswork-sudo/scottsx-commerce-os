package com.scottsx.app.data

import com.scottsx.app.data.domain.Brand
import com.scottsx.app.data.domain.CartItem
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.domain.Seller
import com.scottsx.app.data.remote.V2Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Server-backed cart store.
 *
 * The API at `/api/v1/me/cart` is the source of truth: every local
 * mutation is applied optimistically so buttons stay snappy, then
 * confirmed (or corrected) by the server's response, which carries the
 * full cart payload (items with titles/prices/images, subtotal, count).
 * Guests (no auth token) get a purely local cart — the server call
 * simply returns null and the optimistic state stands.
 *
 * Product details for cart lines come from the server cart payload
 * itself, so nothing here ever touches the legacy fake catalogue.
 */
object CartStore {

    /** Rich per-line data returned by the server cart endpoints. */
    data class CartLineDetail(
        val title: String,
        val priceUgx: Long,
        val imageUrl: String?,
        val stock: Int,
        val status: String,
        val sellerId: String,
        val sellerName: String,
        val lineTotalUgx: Long,
    )

    data class CheckoutOutcome(
        val ok: Boolean,
        val message: String,
        val orderCount: Int,
        val totalUgx: Long,
        val orderIds: List<String>,
    )

    private val _items = MutableStateFlow<List<CartItem>>(emptyList())
    val items: StateFlow<List<CartItem>> = _items.asStateFlow()

    private val _details = MutableStateFlow<Map<String, CartLineDetail>>(emptyMap())
    val details: StateFlow<Map<String, CartLineDetail>> = _details.asStateFlow()

    private val _subtotalUgx = MutableStateFlow(0L)
    val subtotalUgx: StateFlow<Long> = _subtotalUgx.asStateFlow()

    /** True once the first server hydration completed successfully. */
    private val _synced = MutableStateFlow(false)
    val synced: StateFlow<Boolean> = _synced.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private fun adoptServerCart(cart: V2Client.ServerCart) {
        _items.value = cart.items.map { CartItem(productId = it.productId, quantity = it.quantity) }
        _details.value = cart.items.associate { line ->
            line.productId to CartLineDetail(
                title = line.title,
                priceUgx = line.priceMinor,
                imageUrl = line.imageUrl,
                stock = line.stockQuantity,
                status = line.status,
                sellerId = line.sellerId,
                sellerName = line.sellerName,
                lineTotalUgx = line.lineTotalMinor,
            )
        }
        _subtotalUgx.value = cart.subtotalMinor
        _synced.value = true
    }

    fun detailsFor(productId: String): CartLineDetail? = _details.value[productId]

    /**
     * Add a product to the cart. Merges quantities like the old
     * in-memory store, then asks the server to confirm — the reply
     * carries the authoritative cart, so any drift (stock limits,
     * another device) self-heals.
     *
     * Returns the new total count so the UI can show a "+1" toast.
     */
    fun add(productId: String, quantity: Int = 1, variantId: String? = null): Int {
        _items.update { current ->
            val existing = current.firstOrNull { it.productId == productId }
            if (existing != null) {
                current.map {
                    if (it.productId == productId) it.copy(quantity = it.quantity + quantity) else it
                }
            } else {
                current + CartItem(productId, quantity)
            }
        }
        scope.launch { V2Client.addCartItem(productId, quantity)?.let { adoptServerCart(it) } }
        return _items.value.sumOf { it.quantity }
    }

    fun remove(productId: String, variantId: String? = null) {
        _items.update { current -> current.filterNot { it.productId == productId } }
        _details.update { it - productId }
        scope.launch { V2Client.removeCartItem(productId)?.let { adoptServerCart(it) } }
    }

    fun setQuantity(productId: String, quantity: Int, variantId: String? = null) {
        if (quantity <= 0) {
            remove(productId, variantId)
            return
        }
        _items.update { current ->
            current.map { if (it.productId == productId) it.copy(quantity = quantity) else it }
        }
        scope.launch { V2Client.setCartItemQuantity(productId, quantity)?.let { adoptServerCart(it) } }
    }

    fun clear() {
        _items.value = emptyList()
        _details.value = emptyMap()
        _subtotalUgx.value = 0L
        scope.launch { V2Client.clearServerCart()?.let { adoptServerCart(it) } }
    }

    val totalCount: Int get() = _items.value.sumOf { it.quantity }

    /** Hydrate from the server (call when the cart screen opens). */
    suspend fun syncFromServer() {
        syncMutex.withLock {
            V2Client.fetchCart()?.let { adoptServerCart(it) }
        }
    }

    /**
     * Place the order(s): the backend creates one order per line,
     * decrements stock atomically, notifies each seller and empties
     * the cart — all in a single transaction.
     */
    suspend fun checkout(phone: String? = null, note: String? = null, addressId: String? = null): CheckoutOutcome {
        val result = V2Client.checkoutCart(phone, note, addressId)
            ?: return CheckoutOutcome(
                ok = false,
                message = "Couldn't place the order — check your connection and try again.",
                orderCount = 0,
                totalUgx = 0L,
                orderIds = emptyList(),
            )
        _items.value = emptyList()
        _details.value = emptyMap()
        _subtotalUgx.value = 0L
        return CheckoutOutcome(
            ok = true,
            message = result.message,
            orderCount = result.orderCount,
            totalUgx = result.totalMinor,
            orderIds = result.orderIds,
        )
    }
}

/**
 * Wishlist backed by the server bookmarks API (`/me/bookmarks`).
 * Toggles are optimistic with server confirmation; [syncFromServer]
 * hydrates ids + full products for the wishlist screen.
 */
object WishlistStore {

    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _synced = MutableStateFlow(false)
    val synced: StateFlow<Boolean> = _synced.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun contains(productId: String): Boolean = _ids.value.contains(productId)

    /** Toggle the saved state AND persist it through the backend
     *  saved-products endpoints (optimistic with rollback on failure).
     *  Same code path as [toggle] — named for what it actually does. */
    fun toggleBookmark(productId: String): Boolean = toggle(productId)

    fun toggle(productId: String): Boolean {
        val added = !_ids.value.contains(productId)
        _ids.update { if (added) it + productId else it - productId }
        _products.update { list -> if (added) list else list.filterNot { it.id == productId } }
        scope.launch {
            val ok = if (added) V2Client.saveProduct(productId) else V2Client.unsaveProduct(productId)
            if (!ok) {
                // The toggle endpoint is the single source of truth —
                // if it failed, restore the previous local state.
                _ids.update { if (added) it - productId else it + productId }
            }
        }
        return added
    }

    suspend fun syncFromServer() {
        val arr = V2Client.fetchSavedProducts() ?: return
        val mapped = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { V2Client.jsonToProduct(it) }
        }
        _products.value = mapped
        _ids.value = mapped.map { it.id }.toSet()
        _synced.value = true
    }

    val totalCount: Int get() = _ids.value.size
}

/**
 * Variant-aware cart resolver. Product details come from the server
 * cart payload first, then the live catalogue cache — never from the
 * retired hardcoded marketplace fixture.
 */
data class ResolvedCartItem(
    val product: Product,
    val quantity: Int,
    val variantId: String? = null,
)

private fun productFromLine(productId: String, detail: CartStore.CartLineDetail): Product =
    Product(
        id = productId,
        name = detail.title,
        shortDescription = "",
        description = "",
        priceUgx = detail.priceUgx,
        category = ProductCategory.All,
        brand = Brand(id = "unknown", name = ""),
        seller = Seller(id = detail.sellerId, name = detail.sellerName),
        imageUrl = detail.imageUrl ?: "",
        stock = detail.stock,
    )

fun List<CartItem>.resolve(): List<Pair<Product, Int>> =
    mapNotNull { item ->
        val product = CartStore.detailsFor(item.productId)?.let { productFromLine(item.productId, it) }
            ?: LiveMarketplace.byId(item.productId)
        product?.let { it to item.quantity }
    }

fun List<CartItem>.resolveWithVariants(): List<ResolvedCartItem> =
    mapNotNull { item ->
        val product = CartStore.detailsFor(item.productId)?.let { productFromLine(item.productId, it) }
            ?: LiveMarketplace.byId(item.productId)
        product?.let { ResolvedCartItem(it, item.quantity, item.variantId) }
    }
