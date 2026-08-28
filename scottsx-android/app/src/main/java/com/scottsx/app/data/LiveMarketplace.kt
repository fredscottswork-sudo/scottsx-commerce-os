package com.scottsx.app.data

import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.V2Client
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session-scoped catalogue cache fed ONLY by the live backend.
 *
 * This replaces the legacy [MarketplaceDataSource] hardcoded product
 * list for every read-by-id lookup (cart lines, wishlist rows, product
 * detail deep links, related rails). Authors that need catalogue data
 * should call [refresh] then read [products]; consumers that only need
 * a single product use [byId] / [byIdOrFetch].
 *
 * Nothing here is fabricated: if the backend is unreachable the cache
 * simply keeps the last known data and [state] reports the failure so
 * surfaces can render an error instead of fake stock.
 */
object LiveMarketplace {

    enum class State { Idle, Loading, Ready, Empty, Error }

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Every product ever observed this session, keyed by id. */
    private val index = ConcurrentHashMap<String, Product>()

    private val refreshMutex = Mutex()

    /** Cache products observed elsewhere (search, detail, cart). */
    fun cache(products: List<Product>) {
        products.forEach { index[it.id] = it }
    }

    fun byId(id: String): Product? = index[id]

    /**
     * Refresh the catalogue from `GET /api/v1/products`. Safe to call
     * from many screens — concurrent calls coalesce on [refreshMutex].
     */
    suspend fun refresh(force: Boolean = false) {
        if (!force && _state.value == State.Ready) return
        refreshMutex.withLock {
            // Someone else may just have completed the work we queued for.
            if (!force && _state.value == State.Ready) return
            _state.value = State.Loading
            val list = V2Client.fetchProductsListOrNull()
            if (list == null) {
                // Keep the old data (if any) but report the failure.
                _state.value = if (_products.value.isEmpty()) State.Error else State.Ready
                return
            }
            cache(list)
            _products.value = list
            _state.value = if (list.isEmpty()) State.Empty else State.Ready
        }
    }

    /** Load once, no-op afterwards. */
    suspend fun ensureLoaded() {
        if (_state.value == State.Idle || (_state.value == State.Error && _products.value.isEmpty())) {
            refresh()
        }
    }

    /**
     * Resolve a product by id, hitting `GET /api/v1/products/:id` on a
     * cache miss (deep links, notifications, chat product cards).
     */
    suspend fun byIdOrFetch(id: String): Product? {
        index[id]?.let { return it }
        val product = V2Client.fetchProductById(id) ?: return null
        index[id] = product
        return product
    }
}

/**
 * Seller-side live caches (dashboard stats, orders, listings) shared by
 * the AI tool layer, which runs synchronously. Call [SellerLive.refresh]
 * from a coroutine; reads are in-memory and non-suspending.
 * [SellerLive.warm] fires a best-effort background refresh.
 */
object SellerLive {

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    @Volatile var dashboard: com.scottsx.app.data.domain.SellerDashboardData? = null
        private set
    @Volatile var orders: List<com.scottsx.app.data.domain.SellerApiOrder>? = null
        private set
    @Volatile var products: com.scottsx.app.data.domain.SellerProductList? = null
        private set

    var lastRefreshMs: Long = 0L
        private set

    suspend fun refresh() {
        val d = runCatching { V2Client.fetchSellerDashboard() }.getOrNull()
        if (d != null) dashboard = d
        val o = runCatching { V2Client.fetchSellerOrders() }.getOrNull()
        if (o != null) orders = o
        val p = runCatching { V2Client.fetchSellerProducts() }.getOrNull()
        if (p != null) products = p
        if (d != null || o != null || p != null) lastRefreshMs = System.currentTimeMillis()
    }

    /** Fire-and-forget refresh; reads may lag one call behind on first use. */
    fun warm() {
        val stale = System.currentTimeMillis() - lastRefreshMs > 60_000L
        if (stale) scope.launch { refresh() }
    }
}

/**
 * Live conversation cache — sync reads for the AI tool layer,
 * suspending refresh for screens. Conversation summaries are the same
 * rows shown in the inbox, so nothing the AI quotes can go stale
 * against what's on screen.
 */
object ChatCache {

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    @Volatile var conversations: List<V2Client.Conversation>? = null
        private set

    /** Last-known message list per conversation (seeded from MessageStream). */
    private val messages = java.util.concurrent.ConcurrentHashMap<String, List<V2Client.ChatMessage>>()

    fun conversation(convId: String): V2Client.Conversation? =
        conversations?.firstOrNull { it.conversationId == convId }

    fun rememberMessages(convId: String, list: List<V2Client.ChatMessage>) {
        if (list.isNotEmpty()) messages[convId] = list
    }

    fun messagesFor(convId: String): List<V2Client.ChatMessage> =
        messages[convId].orEmpty()

    suspend fun refresh() {
        val list = runCatching { V2Client.fetchConversations() }.getOrNull()
        if (list != null) conversations = list
    }

    fun warm() {
        if (conversations == null) scope.launch { refresh() }
    }
}
