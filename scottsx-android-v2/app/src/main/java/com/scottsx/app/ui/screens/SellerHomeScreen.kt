package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.scottsx.app.data.domain.LowStockAlert
import com.scottsx.app.data.domain.OrderStatus
import com.scottsx.app.data.domain.SellerApiOrder
import com.scottsx.app.data.domain.SellerDashboardData
import com.scottsx.app.data.domain.SellerDashboardSnapshot
import com.scottsx.app.data.domain.SellerOrder
import com.scottsx.app.data.domain.SellerOrdersOverview
import com.scottsx.app.data.domain.SellerProductList
import com.scottsx.app.data.domain.SellerSalesPoint
import com.scottsx.app.data.domain.SellerAiInsight
import com.scottsx.app.data.domain.SessionCache
import com.scottsx.app.data.domain.StoreStatus
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.preferences.LocalThemePreference
import com.scottsx.app.data.preferences.themeState
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.data.remote.V2NetworkException
import com.scottsx.app.ui.components.FeedErrorCard
import com.scottsx.app.ui.components.LogoutConfirmDialog
import com.scottsx.app.ui.components.PulsingDot
import com.scottsx.app.ui.components.Reveal
import com.scottsx.app.ui.components.SellerBottomBar
import com.scottsx.app.ui.components.SellerBottomTab
import com.scottsx.app.ui.components.SellerSidebarDestination
import com.scottsx.app.ui.components.SellerSidebarOverlay
import com.scottsx.app.ui.components.ShimmerBox
import com.scottsx.app.ui.components.ThemeSelectorSheet
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Load state of the live seller dashboard (no demo snapshot exists). */
private enum class SellerFeedState { Loading, Ready, Error }


private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun relativeTime(iso: String): String = try {
    val dt = OffsetDateTime.parse(iso)
    val mins = Duration.between(dt, OffsetDateTime.now()).toMinutes()
    when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins} min ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        mins < 7 * 24 * 60 -> "${mins / (24 * 60)}d ago"
        else -> dt.toLocalDate().toString()
    }
} catch (_: Exception) {
    iso.take(10)
}

private fun dayLabel(dateStr: String): String = try {
    LocalDate.parse(dateStr)
        .dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3)
} catch (_: Exception) {
    dateStr.takeLast(2)
}

/**
 * Seller Home Dashboard — rebuilt on live backend data.
 *
 * Everything on this screen comes from the same single backend the
 * website uses:
 *   • Overview        → `GET /api/v1/seller/dashboard/stats` (Postgres
 *                       aggregates: 30-day revenue, orders, avg order
 *                       value, views, followers, stock health)
 *   • Orders overview → real status counts from `GET /api/v1/seller/orders`
 *   • Recent orders   → the backend's 10 most recent real orders
 *   • Sales chart     → the backend's real 14-day sales series
 *   • Low stock       → real listings with stock ≤ 5 + inline +10
 *                       restock (`PATCH /api/v1/seller/products/:id`,
 *                       optimistic with honest revert)
 *   • Open/Closed     → `PATCH /api/v1/seller/open-state` — the same
 *                       flag Nearby buyers see on the store card
 *
 * While loading: shimmer skeletons. On failure: a real error card with
 * Retry. The demo `SellerDataSource` snapshot is gone from this screen.
 *
 * The bottom navigation bar, the sidebar overlay, the theme sheet and
 * the logout dialog are unchanged from the original design.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SellerHomeScreen(
    displayName: String,
    email: String,
    onAddProduct: () -> Unit = {},
    onManageOrders: () -> Unit = {},
    onOpenInventory: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenMarketplaceTools: () -> Unit = {},
    onOpenStoreSettings: () -> Unit = {},
    onOpenProfileSettings: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
    onOpenProduct: (com.scottsx.app.data.domain.Product) -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToReceipts: () -> Unit = {},
    onCreateReceipt: () -> Unit = {},
    onNavigateToAiPersonalization: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSwitchToBuyer: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenSellerAi: () -> Unit = {},
    onOpenSellerMessages: () -> Unit = {},
) {
    val themePref = LocalThemePreference.current
    val themeMode by themePref.themeState()
    val scope = rememberCoroutineScope()

    // ---- Live seller data (the single backend the website uses) ----
    var feedState by remember { mutableStateOf(SellerFeedState.Loading) }
    var dashboard by remember { mutableStateOf<SellerDashboardData?>(null) }
    var allOrders by remember { mutableStateOf<List<SellerApiOrder>>(emptyList()) }
    var products by remember { mutableStateOf<SellerProductList?>(null) }
    var storeOpen by remember { mutableStateOf(false) }
    var statusBusy by remember { mutableStateOf(false) }
    var restockBusy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        feedState = SellerFeedState.Loading
        try {
            val dashboardDeferred = async { V2Client.fetchSellerDashboard() }
            val ordersDeferred = async { V2Client.fetchSellerOrders() }
            val productsDeferred = async { V2Client.fetchSellerProducts() }
            val locationDeferred = async { V2Client.fetchStoreLocation() }
            val data = dashboardDeferred.await() ?: throw V2NetworkException("Dashboard unavailable")
            dashboard = data
            allOrders = ordersDeferred.await()
            products = productsDeferred.await()
            locationDeferred.await()?.let { storeOpen = it.isOpen }
            feedState = SellerFeedState.Ready
        } catch (_: Exception) {
            feedState = SellerFeedState.Error
        }
    }

    /** Real open/closed toggle → PATCH /seller/open-state (optimistic). */
    fun toggleStoreOpen() {
        if (statusBusy) return
        val previous = storeOpen
        storeOpen = !previous
        statusBusy = true
        scope.launch {
            val ok = V2Client.setStoreOpen(!previous)
            statusBusy = false
            if (!ok) storeOpen = previous
        }
    }

    /**
     * Inline restock: +10 stock via a real partial PATCH (stock-only
     * edits keep the listing live). Optimistic update with an honest
     * revert when the backend rejects the change.
     */
    fun restock(product: Product) {
        if (product.id in restockBusy) return
        val current = products
        val nextStock = product.stock + 10
        products = current?.copy(
            products = current.products.map {
                if (it.id == product.id) it.copy(stock = nextStock) else it
            },
        )
        restockBusy = restockBusy + product.id
        scope.launch {
            val updated = V2Client.updateSellerProduct(
                product.id,
                JSONObject().put("stockQuantity", nextStock),
            )
            restockBusy = restockBusy - product.id
            if (updated == null) {
                products = current
            } else {
                // Sync the authoritative stock value from the backend.
                val serverStock = updated.optInt("stockQuantity", nextStock)
                products = current?.copy(
                    products = current.products.map {
                        if (it.id == product.id) it.copy(stock = serverStock) else it
                    },
                )
            }
        }
    }

    val storeName = SessionCache.storeNameOrEmpty()
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val stats = dashboard?.stats

    // Real snapshot for the shared sidebar (badges come from live data).
    val sidebarSnapshot = remember(dashboard, allOrders, products, storeOpen, displayName, email, storeName) {
        val s = dashboard?.stats
        SellerDashboardSnapshot(
            displayName = displayName,
            storeName = storeName,
            storeId = SessionCache.userId ?: "new-seller",
            email = email,
            status = if (storeOpen) StoreStatus.Online else StoreStatus.Away,
            salesTodayUgx = s?.revenue30Ugx ?: 0L,
            salesTodayDeltaPct = 0f,
            ordersToday = s?.orders30 ?: 0,
            ordersTodayDelta = 0,
            customersTotal = s?.followers ?: 0,
            customersDelta = 0,
            rating = 0f,
            ratingLabel = "",
            ordersOverview = SellerOrdersOverview(
                pending = allOrders.count { it.status == "pending" },
                processing = allOrders.count { it.status == "paid" },
                ready = allOrders.count { it.status == "shipped" },
                completed = allOrders.count { it.status == "delivered" },
            ),
            recentOrders = (dashboard?.recentOrders ?: emptyList()).map { o ->
                SellerOrder(
                    id = o.id,
                    productName = o.productTitle,
                    itemsCount = o.quantity,
                    // Backend `amount` is the unit price.
                    totalUgx = o.amount * o.quantity,
                    placedAtLabel = relativeTime(o.createdAt),
                    status = OrderStatus.fromLabel(o.displayStatus),
                    buyerName = o.buyerName,
                )
            },
            sales = emptyList(),
            aiInsight = SellerAiInsight(
                headline = "Seller AI",
                body = "",
                bestProduct = s?.topProduct ?: "—",
                trendLabel = "",
            ),
            lowStock = (products?.lowStockProducts ?: emptyList()).map { p ->
                LowStockAlert(p.id, p.name, p.stock, 5)
            },
        )
    }

    var bottomTab by remember { mutableStateOf(SellerBottomTab.Home) }
    var sidebarOpen by remember { mutableStateOf(false) }
    var themeSheetOpen by remember { mutableStateOf(false) }
    var logoutDialogOpen by remember { mutableStateOf(false) }
    var salesPeriod by remember { mutableStateOf(SalesPeriod.ThisWeek) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.PanelLight),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            // Header — real greeting, real badges, real open/closed state
            item {
                SellerHeader(
                    greeting = greetingFor(hour),
                    displayName = displayName,
                    storeName = storeName,
                    storeOpen = storeOpen,
                    statusBusy = statusBusy,
                    messagesBadge = stats?.unreadMessages ?: 0,
                    ordersBadge = stats?.pendingApproval ?: 0,
                    onToggleStatus = { toggleStoreOpen() },
                    onMenuClicked = { sidebarOpen = true },
                    onMessagesClicked = onOpenSellerMessages,
                    onOrdersClicked = onManageOrders,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }

            when (feedState) {
                SellerFeedState.Loading -> item {
                    SellerFeedSkeleton()
                }

                SellerFeedState.Error -> item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FeedErrorCard(
                            onRetry = { refreshTick++ },
                            surface = Color.White,
                            foreground = ScottsTechXColors.OnLight,
                            secondary = ScottsTechXColors.OnLightSecondary,
                            accent = ScottsTechXColors.BluePrimary,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }

                  SellerFeedState.Ready -> {
                    val s = dashboard?.stats

                    // 0. Onboarding — only when the store truly has nothing yet
                    if (s != null && s.totalProducts == 0 && s.orders == 0) {
                        item {
                            Reveal(index = 0) {
                                OnboardingCard(onAddProduct = onAddProduct)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // 1. Today's Overview — real 30-day aggregates
                    if (s != null) {
                        item {
                            Reveal(index = 0) {
                                SellerOverviewCard(stats = s)
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        item {
                            Reveal(index = 1) {
                                OrdersOverviewRow(
                                    orders = allOrders,
                                    onSeeAll = onManageOrders,
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    // 2. Recent Orders — real, with working "View Order"
                    val recent = dashboard?.recentOrders ?: emptyList()
                    if (recent.isNotEmpty()) {
                        item {
                            SectionHeading(
                                title = "Recent Orders",
                                actionLabel = "View All",
                                onAction = onManageOrders,
                            )
                        }
                        items(recent, key = { it.id }) { order ->
                            OrderRow(
                                title = order.productTitle,
                                buyerName = order.buyerName,
                                quantity = order.quantity,
                                amount = order.amount,
                                statusLabel = order.displayStatus,
                                placedAt = relativeTime(order.createdAt),
                                onView = onManageOrders,
                            )
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }

                    // 3. Quick Actions — every action wired to a real screen
                    item {
                        SectionHeading(title = "Quick Actions")
                        QuickActionsRow(
                            onAddProduct = onAddProduct,
                            onManageOrders = onManageOrders,
                            onPromotions = onOpenMarketplaceTools,
                            onAnalytics = onOpenAnalytics,
                            onMessages = onOpenSellerMessages,
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    // 4. Sales Performance — real 14-day backend series
                    val series = dashboard?.salesSeries ?: emptyList()
                    if (series.isNotEmpty()) {
                        item {
                            SalesPerformanceCard(
                                series = series,
                                period = salesPeriod,
                                onPeriod = { salesPeriod = it },
                            )
                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    // 5. Seller AI — honest card, real CTA to the AI screen
                    if (s != null) {
                        item {
                            SellerAiCard(
                                lowStock = s.lowStock,
                                pendingApproval = s.pendingApproval,
                                topProduct = s.topProduct,
                                onAsk = onOpenSellerAi,
                            )
                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    // 6. Stock Watch — real listings + inline restock
                    //    (hidden for a brand-new store: "well stocked"
                    //    would be a lie with zero listings)
                    val lowStock = products?.lowStockProducts ?: emptyList()
                    val outOfStock = products?.outOfStockProducts ?: emptyList()
                    val hasListings = (s?.totalProducts ?: 0) > 0
                    if (hasListings) {
                        item {
                            SectionHeading(
                                title = "Stock Watch",
                                actionLabel = "View All",
                                onAction = onOpenInventory,
                            )
                        }
                        when {
                            lowStock.isEmpty() && outOfStock.isEmpty() -> item {
                                WellStockedCard()
                            }
                            else -> {
                                items(lowStock + outOfStock, key = { it.id }) { p ->
                                    LowStockRow(
                                        name = p.name,
                                        stock = p.stock,
                                        busy = p.id in restockBusy,
                                        onRestock = { restock(p) },
                                        onOpen = { onOpenProduct(p) },
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

        // ---------- Floating bottom nav (seller) ----------
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            SellerBottomBar(
                selected = bottomTab,
                onSelect = { tab ->
                    bottomTab = tab
                    when (tab) {
                        SellerBottomTab.Home -> Unit
                        SellerBottomTab.Add -> onAddProduct()
                        SellerBottomTab.AI -> onOpenSellerAi()
                        SellerBottomTab.Messages -> onOpenSellerMessages()
                        SellerBottomTab.Analytics -> onOpenAnalytics()
                    }
                },
                onAddClicked = onAddProduct,
                onAiClicked = onOpenSellerAi,
            )
        }

        // ---------- Sidebar overlay ----------
        SellerSidebarOverlay(
            open = sidebarOpen,
            onDismiss = { sidebarOpen = false },
            snapshot = sidebarSnapshot,
            onNavigate = { dest ->
                when (dest) {
                    SellerSidebarDestination.Dashboard -> Unit
                    SellerSidebarDestination.Orders -> onManageOrders()
                    SellerSidebarDestination.Products -> onOpenInventory()
                    SellerSidebarDestination.Customers -> Unit
                    SellerSidebarDestination.Messages -> onOpenMessages()
                    SellerSidebarDestination.Promotions -> onOpenMarketplaceTools()
                    SellerSidebarDestination.Analytics -> onOpenAnalytics()
                    SellerSidebarDestination.SellerAi -> onOpenSellerAi()
                    SellerSidebarDestination.MarketingTools -> onOpenMarketplaceTools()
                    SellerSidebarDestination.StoreProfile -> onOpenStoreSettings()
                    SellerSidebarDestination.StoreSettings -> onOpenStoreSettings()
                    SellerSidebarDestination.Transactions -> onNavigateToTransactions()
                    SellerSidebarDestination.Receipts -> onNavigateToReceipts()
                    SellerSidebarDestination.CreateReceipt -> onCreateReceipt()
                    SellerSidebarDestination.AiPersonalization -> onNavigateToAiPersonalization()
                    SellerSidebarDestination.Settings -> onNavigateToSettings()
                    SellerSidebarDestination.SwitchToBuyer -> onSwitchToBuyer()
                    SellerSidebarDestination.Logout -> logoutDialogOpen = true
                    SellerSidebarDestination.ViewStore -> Unit
                    SellerSidebarDestination.Theme -> themeSheetOpen = true
                    SellerSidebarDestination.ToggleOnline -> toggleStoreOpen()
                }
            },
        )

        // ---------- Theme selector sheet ----------
        if (themeSheetOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { themeSheetOpen = false },
                sheetState = sheetState,
                containerColor = Color.White,
            ) {
                ThemeSelectorSheet(
                    current = themeMode,
                    onPick = { mode ->
                        themePref.set(mode)
                        themeSheetOpen = false
                    },
                )
            }
        }

        // ---------- Logout confirmation ----------
        if (logoutDialogOpen) {
            LogoutConfirmDialog(
                onCancel = { logoutDialogOpen = false },
                onConfirm = {
                    logoutDialogOpen = false
                    sidebarOpen = false
                    onSignOut()
                },
            )
        }
    }
}

// =====================================================================================
// Header
// =====================================================================================

@Composable
private fun SellerHeader(
    greeting: String,
    displayName: String,
    storeName: String,
    storeOpen: Boolean,
    statusBusy: Boolean,
    messagesBadge: Int,
    ordersBadge: Int,
    onToggleStatus: () -> Unit,
    onMenuClicked: () -> Unit,
    onMessagesClicked: () -> Unit,
    onOrdersClicked: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ScottsTechXColors.BluePrimaryDark,
                        ScottsTechXColors.BluePrimary,
                    ),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { onMenuClicked() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Open menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SellerHeaderIcon(
                        icon = Icons.Filled.ChatBubble,
                        contentDescription = "Messages",
                        badge = messagesBadge,
                        onClick = onMessagesClicked,
                    )
                    SellerHeaderIcon(
                        icon = Icons.Filled.ReceiptLong,
                        contentDescription = "Orders",
                        badge = ordersBadge,
                        onClick = onOrdersClicked,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(
                            width = 1.5.dp,
                            color = Color.White.copy(alpha = 0.35f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (storeName.firstOrNull() ?: displayName.firstOrNull() ?: "S")
                            .toString().uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting,",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                    Text(
                        text = displayName.ifEmpty { "Seller" },
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = storeName,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Real open/closed state — synced with the backend flag that
            // Nearby buyers see on the store card.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (storeOpen) Color(0xFF15803D) else Color(0xFFB45309),
                        )
                        .clickable(enabled = !statusBusy, onClick = onToggleStatus)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (storeOpen) {
                            PulsingDot(color = Color.White)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = when {
                                statusBusy -> "Updating…"
                                storeOpen -> "Open · Accepting orders"
                                else -> "Closed to new orders"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerHeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    badge: Int,
    onClick: () -> Unit,
) {
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE11D48))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badge > 9) "9+" else badge.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = ScottsTechXColors.OnLight,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = ScottsTechXColors.BluePrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onAction() },
            )
        }
    }
}
