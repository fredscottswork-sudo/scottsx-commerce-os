package com.scottsx.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.SellerDashboardData
import com.scottsx.app.data.domain.SellerStats
import com.scottsx.app.data.domain.StoreStatus
import com.scottsx.app.data.preferences.sidebarPaletteFor
import com.scottsx.app.data.preferences.themeState
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.AnimatedNumber
import com.scottsx.app.ui.components.FeedErrorCard
import com.scottsx.app.ui.components.HamburgerIcon
import com.scottsx.app.data.preferences.LocalThemePreference
import com.scottsx.app.ui.components.LogoutConfirmDialog
import com.scottsx.app.ui.components.PulsingDot
import com.scottsx.app.ui.components.Reveal
import com.scottsx.app.ui.components.SellerBottomBar
import com.scottsx.app.ui.components.SellerBottomTab
import com.scottsx.app.ui.components.SellerSidebarData
import com.scottsx.app.ui.components.SellerSidebarDestination
import com.scottsx.app.ui.components.SellerSidebarOverlay
import com.scottsx.app.ui.components.ShimmerBox
import com.scottsx.app.ui.components.ThemeSelectorSheet
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.formatUgxCompact
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.components.navBarSpacer

/**
 * Seller Home — rebuilt on the web's seller dashboard contract.
 *
 * Every figure comes from `GET /api/v1/seller/dashboard/stats`
 * (Postgres aggregates over the seller's real orders and products —
 * the same payload web/src/pages/seller/SellerDashboard.tsx renders),
 * the open/closed pill from `/seller/location` + `PATCH
 * /seller/open-state`, and the bell from the unread-count endpoint.
 *
 * The Stage-3 sample-data snapshot (850,000 UGX days, 42 completed
 * orders, "sales up 18%" insights) is gone: loading shows shimmer,
 * failure shows a retry card, and a brand-new store shows honest
 * zeros with pointers to the next real action.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SellerHomeScreen(
    displayName: String,
    email: String,
    onAddProduct: () -> Unit = {},
    onManageOrders: () -> Unit = {},
    onOpenInventory: () -> Unit = {},
    onOpenBulkImport: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenMarketplaceTools: () -> Unit = {},
    onOpenStoreSettings: () -> Unit = {},
    onOpenProfileSettings: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenProduct: (Product) -> Unit = {},
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

    // ---- Live state --------------------------------------------------------
    var dash by remember { mutableStateOf<SellerDashboardData?>(null) }
    var open by remember { mutableStateOf<Boolean?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var lastFix by remember { mutableStateOf<String?>(null) }
    var locBusy by remember { mutableStateOf(false) }
    var locHint by remember { mutableStateOf<String?>(null) }
    var storeName by remember { mutableStateOf<String?>(null) }
    var sellerId by remember { mutableStateOf<String?>(null) }
    var unread by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(SellerFeedState.Loading) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        // Each of the four fetches is best-effort and exception-isolated:
        // if ANY of them threw (parse glitch, flaky network, a 4xx the
        // client maps to an exception), coroutineScope would CANCEL the
        // other children and the whole block would fail — leaving the
        // dashboard stuck on the Loading skeleton forever. supervisorScope
        // + runCatching keeps every failure local so the Home tab always
        // reaches Ready (or Error only when the main feed itself failed).
        state = kotlinx.coroutines.supervisorScope {
            var feed: SellerDashboardData? = null
            val d = launch { feed = runCatching { V2Client.fetchSellerDashboard() }.getOrNull() }
            val l = launch {
                runCatching {
                    open = V2Client.fetchStoreOpenState()
                    V2Client.fetchSellerLocationState()?.let { loc ->
                        sharing = loc.sharing
                        lastFix = loc.updatedAt
                    }
                }
            }
            val n = launch { unread = runCatching { V2Client.fetchUnreadNotificationCount() }.getOrDefault(0) }
            val p = launch {
                runCatching {
                    V2Client.fetchSellerProfile()?.let { prof ->
                        storeName = listOf("businessName", "storeName", "name")
                            .firstNotNullOfOrNull { key ->
                                prof.optString(key).takeIf { it.isNotBlank() }
                            }
                        sellerId = prof.optString("id").takeIf { it.isNotBlank() }
                    }
                }
            }
            d.join(); l.join(); n.join(); p.join()
            dash = feed
            if (feed == null) SellerFeedState.Error else SellerFeedState.Ready
        }
    }

    // ---- Chrome state ------------------------------------------------------
    var bottomTab by remember { mutableStateOf(SellerBottomTab.Home) }
    var sidebarOpen by remember { mutableStateOf(false) }
    var themeSheetOpen by remember { mutableStateOf(false) }
    var logoutDialogOpen by remember { mutableStateOf(false) }

    val status: StoreStatus =
        if (open == false) StoreStatus.Away else StoreStatus.Online

    fun toggleOpen() {
        val next = !(open ?: false)
        open = next  // optimistic — revert on server refusal
        scope.launch {
            val accepted = V2Client.setStoreOpen(next)
            if (accepted == null) open = !next
        }
    }

    // ---- Live location (web dashboard "Store controls" parity) ------------
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sharePermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) { locHint = "Location permission is required to share your store." }
        else {
            locBusy = true
            runCatching {
                val client = com.google.android.gms.location.LocationServices
                    .getFusedLocationProviderClient(ctx)
                val cts = com.google.android.gms.tasks.CancellationTokenSource()
                @Suppress("MissingPermission")
                client.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token,
                ).addOnSuccessListener { fix ->
                    scope.launch {
                        if (fix != null) {
                            val ok = V2Client.updateSellerLocation(fix.latitude, fix.longitude)
                            sharing = ok
                            lastFix = java.text.SimpleDateFormat(
                                "d MMM HH:mm", java.util.Locale.getDefault(),
                            ).format(java.util.Date())
                            locHint = if (ok) "Buyers can now see your store live."
                            else "Could not publish the fix — try again."
                        } else {
                            locHint = "GPS returned nothing — step outside or try again."
                        }
                        locBusy = false
                    }
                }.addOnFailureListener {
                    scope.launch {
                        locHint = "GPS failed — try again."
                        locBusy = false
                    }
                }
            }.onFailure {
                locHint = "Location services unavailable on this device."
                locBusy = false
            }
        }
    }

    fun toggleSharing(next: Boolean) {
        if (locBusy) return
        if (!next) {
            locBusy = true
            locHint = null
            scope.launch {
                if (V2Client.stopSellerLocationSharing()) {
                    sharing = false
                    locHint = "Live sharing off — store stays at its last known pin."
                } else {
                    locHint = "Could not stop sharing — try again."
                }
                locBusy = false
            }
            return
        }
        locHint = null
        // The system permission dialog handles both granted+denied flows.
        sharePermLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark)
            .statusBarSpacer()  // edge-to-edge: content clears the status bar,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // Brand strip — company identity first (same as buyer home).
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(ScottsTechXColors.BrandGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The COMPANY LOGO picture — brand identification,
                        // always the first thing on screen.
                        Image(
                            painter = painterResource(R.drawable.brand_mark),
                            contentDescription = "ScottsTechX logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "ScottsTechX",
                        color = ScottsTechXColors.OnDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.2.sp,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "· Seller Center",
                        color = ScottsTechXColors.OnDarkMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 16.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ScottsTechXColors.SurfacePanelDark)
                            .clickable { sidebarOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HamburgerIcon,
                            contentDescription = "Menu",
                            tint = ScottsTechXColors.OnDark,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = storeName ?: "Seller dashboard",
                            color = ScottsTechXColors.OnDark,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = displayName,
                            color = ScottsTechXColors.OnDarkSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (open != null) {
                        OpenStatePill(status = status, onToggle = { toggleOpen() })
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ScottsTechXColors.SurfacePanelDark)
                            .clickable { onOpenSellerMessages() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubble,
                            contentDescription = "Messages",
                            tint = ScottsTechXColors.OnDark,
                            modifier = Modifier.size(20.dp),
                        )
                        if (unread > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(ScottsTechXColors.ErrorRed),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (unread > 9) "9+" else unread.toString(),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            val stats = dash?.stats
            when (state) {
                SellerFeedState.Loading -> {
                    item {
                        SellerSkeleton(
                            base = ScottsTechXColors.SurfaceElevatedDark,
                            highlight = Color(0xFF1B2743),
                        )
                    }
                }
                SellerFeedState.Error -> {
                    item {
                        Spacer(Modifier.height(20.dp))
                        FeedErrorCard(
                            onRetry = { refreshTick++ },
                            modifier = Modifier.padding(16.dp),
                            surface = ScottsTechXColors.SurfacePanelDark,
                            foreground = ScottsTechXColors.OnDark,
                            secondary = ScottsTechXColors.OnDarkSecondary,
                            accent = ScottsTechXColors.BluePrimary,
                        )
                    }
                }
                SellerFeedState.Ready -> if (stats != null) {
                    // 0. Attention chips — the web's alert row
                    //    (pending approval / out of stock / low stock /
                    //    unread messages), deep-linked to the real screens.
                    if (stats.pendingApproval > 0 || stats.outOfStock > 0 ||
                        stats.lowStock > 0 || stats.unreadMessages > 0
                    ) {
                        item {
                            Reveal(index = 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (stats.pendingApproval > 0) {
                                        AttentionChip(
                                            "${stats.pendingApproval} awaiting approval",
                                            ChipTone.Amber, onClick = onOpenInventory,
                                        )
                                    }
                                    if (stats.outOfStock > 0) {
                                        AttentionChip(
                                            "${stats.outOfStock} out of stock",
                                            ChipTone.Red, onClick = onOpenInventory,
                                        )
                                    }
                                    if (stats.lowStock > 0) {
                                        AttentionChip(
                                            "${stats.lowStock} low on stock",
                                            ChipTone.Amber, onClick = onOpenInventory,
                                        )
                                    }
                                    if (stats.unreadMessages > 0) {
                                        AttentionChip(
                                            "${stats.unreadMessages} unread message${if (stats.unreadMessages > 1) "s" else ""}",
                                            ChipTone.Blue, onClick = onOpenSellerMessages,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 1. Stat cards — real numbers, web-parity set
                    item {
                        Reveal(index = 0) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatCard(
                                        label = "Revenue (all time)",
                                        value = stats.revenueUgx,
                                        // Abbreviated ("UGX 45.0M") — the raw
                                        // figure is up to 13 chars wide.
                                        format = { formatUgxCompact(it) },
                                        icon = Icons.Filled.AttachMoney,
                                        hint = "${formatUgxCompact(stats.revenue30Ugx)} last 30 days",
                                        accent = ScottsTechXColors.SuccessGreen,
                                        modifier = Modifier.weight(1f),
                                    )
                                    StatCard(
                                        label = "Orders",
                                        hint = "${stats.orders30} in last 30 days",
                                        value = stats.orders.toLong(),
                                        format = { compactCount(it) },
                                        icon = Icons.Filled.ShoppingBag,
                                        accent = ScottsTechXColors.BluePrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatCard(
                                        label = "Avg order value",
                                        value = stats.avgOrderValueUgx,
                                        format = { formatUgxCompact(it) },
                                        hint = "${stats.totalProducts} products listed",
                                        icon = Icons.Filled.Inventory2,
                                        accent = ScottsTechXColors.PurpleAccent,
                                        modifier = Modifier.weight(1f),
                                    )
                                    StatCard(
                                        label = "Followers",
                                        value = stats.followers.toLong(),
                                        format = { compactCount(it) },
                                        hint = "${compactCount(stats.totalViews.toLong())} product views",
                                        icon = Icons.Filled.Group,
                                        accent = ScottsTechXColors.CyanAccent,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    // 2. Quick actions — real navigations only
                    item {
                        Reveal(index = 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                QuickChip("Add product", Icons.Filled.Add, onAddProduct, Modifier.weight(1f))
                                QuickChip("Orders", Icons.Filled.ShoppingBag, onManageOrders, Modifier.weight(1f))
                                QuickChip("Promos", Icons.Filled.LocalOffer, onOpenMarketplaceTools, Modifier.weight(1f))
                                QuickChip("Analytics", Icons.Filled.Analytics, onOpenAnalytics, Modifier.weight(1f))
                            }
                        }
                    }

                    // 3. Sales sparkline — the real 14-day series
                    if (dash?.salesSeries?.isNotEmpty() == true) {
                        item {
                            Reveal(index = 2) {
                                SalesSparklineCard(
                                    points = dash!!.salesSeries.map { it.revenue },
                                    revenue30 = stats.revenue30Ugx,
                                    orders30 = stats.orders30,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                        }
                    }

                    // 3b. Store controls — web parity: accepting-orders
                    //     switch, live-location sharing, listing badges.
                    item {
                        Reveal(index = 3) {
                            StoreControlsCard(
                                acceptingOrders = open != false,
                                onToggleOrders = { toggleOpen() },
                                sharing = sharing,
                                locBusy = locBusy,
                                locHint = locHint,
                                lastFix = lastFix,
                                onToggleSharing = { next -> toggleSharing(next) },
                                statusCounts = stats,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    // 4. Listing health — real status counts
                    item {
                        Reveal(index = 4) {
                            ListingHealthCard(
                                approved = stats.approved,
                                pending = stats.pending + stats.pendingApproval,
                                draft = stats.draft,
                                rejected = stats.rejected,
                                suspended = stats.suspended,
                                lowStock = stats.lowStock,
                                outOfStock = stats.outOfStock,
                                onInventory = onOpenInventory,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    // 5. Top products — real sales leaders
                    if (dash?.topProducts?.isNotEmpty() == true) {
                        item {
                            Reveal(index = 4) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                    SellerSectionTitle(title = "Top products", action = null, onAction = {})
                                }
                            }
                        }
                        items(dash!!.topProducts, key = { it.title }) { top ->
                            TopProductRow(
                                title = top.title,
                                sold = top.sold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // 6. Recent orders — real rows
                    if (dash?.recentOrders?.isNotEmpty() == true) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Reveal(index = 5) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    SellerSectionTitle(
                                        title = "Recent orders",
                                        action = "View all",
                                        onAction = onManageOrders,
                                    )
                                }
                            }
                        }
                        items(dash!!.recentOrders, key = { it.id }) { order ->
                            RecentOrderRow(
                                order = order,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // 7. Low-stock alert band — only when real
                    if (stats.lowStock > 0 || stats.outOfStock > 0) {
                        item {
                            Reveal(index = 6) {
                                LowStockBand(
                                    low = stats.lowStock,
                                    out = stats.outOfStock,
                                    onInventory = onOpenInventory,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        // Floating bottom nav
        Box(
            modifier = Modifier
                .navBarSpacer()  // lift the bottom bar clear of the gesture pill
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            SellerBottomBar(
                selected = bottomTab,
                onSelect = { tab ->
                    bottomTab = tab
                    when (tab) {
                        SellerBottomTab.Home -> Unit
                        SellerBottomTab.Orders -> onManageOrders()
                        SellerBottomTab.Add -> onAddProduct()
                        SellerBottomTab.Messages -> onOpenSellerMessages()
                        SellerBottomTab.Analytics -> onOpenAnalytics()
                    }
                },
                onAddClicked = onAddProduct,
                onAiClicked = onOpenSellerAi,
            )
        }

        // Sidebar — fed with real numbers only
        val statsForSidebar = dash?.stats
        SellerSidebarOverlay(
            open = sidebarOpen,
            onDismiss = { sidebarOpen = false },
            data = SellerSidebarData(
                displayName = displayName,
                storeName = storeName ?: "My store",
                storeId = sellerId ?: "",
                status = status,
                pendingOrders = dash?.recentOrders?.count { it.status == "pending" } ?: 0,
                followers = statsForSidebar?.followers ?: 0,
                productsTotal = statsForSidebar?.totalProducts ?: 0,
                unreadMessages = statsForSidebar?.unreadMessages ?: unread,
            ),
            onNavigate = { dest ->
                when (dest) {
                    SellerSidebarDestination.Dashboard -> Unit
                    SellerSidebarDestination.Orders -> onManageOrders()
                    SellerSidebarDestination.Products -> onOpenInventory()
                    SellerSidebarDestination.BulkImport -> onOpenBulkImport()
                    SellerSidebarDestination.AddProduct -> onAddProduct()
                    SellerSidebarDestination.Customers -> Unit
                    SellerSidebarDestination.Messages -> onOpenMessages()
                    SellerSidebarDestination.Notifications -> onOpenNotifications()
                    SellerSidebarDestination.Promotions -> onOpenMarketplaceTools()
                    SellerSidebarDestination.Analytics -> onOpenAnalytics()
                    SellerSidebarDestination.SellerAi -> onOpenSellerAi()
                    SellerSidebarDestination.MarketingTools -> onOpenMarketplaceTools()
                    SellerSidebarDestination.StoreProfile -> onOpenProfileSettings()
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
                    SellerSidebarDestination.ToggleOnline -> toggleOpen()
                }
            },
        )

        if (themeSheetOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { themeSheetOpen = false },
                sheetState = sheetState,
                containerColor = sidebarPaletteFor(themeMode).background,
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

private enum class SellerFeedState { Loading, Ready, Error }

// =====================================================================================
// Building blocks
// =====================================================================================

@Composable
private fun OpenStatePill(status: StoreStatus, onToggle: () -> Unit) {
    val live = status == StoreStatus.Online
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (live) ScottsTechXColors.SuccessGreen.copy(alpha = 0.14f)
                else ScottsTechXColors.WarningAmber.copy(alpha = 0.14f),
            )
            .clickable(onClickLabel = "Toggle store open state") { onToggle() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (live) ScottsTechXColors.SuccessGreen else ScottsTechXColors.WarningAmber),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = status.label,
            color = if (live) ScottsTechXColors.SuccessGreen else ScottsTechXColors.WarningAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Long,
    format: (Long) -> String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ScottsTechXColors.SurfacePanelDark)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
            AnimatedNumber(
            target = value,
            format = format,
            color = ScottsTechXColors.OnDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            // Hard-clamped to one line: an unexpected figure degrades to
            // "UGX 9…" instead of wrapping and bursting the tile.
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label, color = ScottsTechXColors.OnDarkSecondary, fontSize = 11.5.sp,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        )
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint, color = ScottsTechXColors.OnDarkMuted, fontSize = 10.sp,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun compactCount(n: Long): String = when {
    n >= 1_000_000L -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000L -> "${"%.1f".format(n / 1_000.0)}K"
    else -> n.toString()
}

@Composable
private fun QuickChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.SurfacePanelDark)
            .clickable(onClickLabel = label) { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(5.dp))
        Text(text = label, color = ScottsTechXColors.OnDarkSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SellerSectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, color = ScottsTechXColors.OnDark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        if (action != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = action, color = ScottsTechXColors.BluePrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = ScottsTechXColors.BluePrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 14-day revenue sparkline built from the real salesSeries. */
@Composable
private fun SalesSparklineCard(
    points: List<Long>,
    revenue30: Long,
    orders30: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ScottsTechXColors.SurfacePanelDark)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = ScottsTechXColors.CyanAccent)
            Spacer(Modifier.width(6.dp))
            Text(text = "Sales — last 14 days", color = ScottsTechXColors.OnDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${formatUgx(revenue30)} revenue  ·  $orders30 orders in 30 days",
            color = ScottsTechXColors.OnDarkSecondary,
            fontSize = 11.5.sp,
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
        ) {
            val max = (points.maxOrNull() ?: 0L).coerceAtLeast(1L)
            val stepX = size.width / points.size.coerceAtLeast(1)
            val stepY = size.height * 0.8f
            val path = Path()
            var x = stepX / 2f
            points.forEachIndexed { i, v ->
                val y = size.height - (v.toFloat() / max.toFloat()) * stepY - size.height * 0.1f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                x += stepX
            }
            drawPath(path, color = Color(0xFF22D3EE), style = Stroke(width = 3.dp.toPx()))
            // end dot on the latest day
            drawCircle(color = Color(0xFF22D3EE), radius = 4.dp.toPx(), center = Offset(x - stepX, size.height - (points.last().toFloat() / max.toFloat()) * stepY - size.height * 0.1f))
        }
    }
}

@Composable
private fun ListingHealthCard(
    approved: Int,
    pending: Int,
    draft: Int,
    rejected: Int,
    suspended: Int,
    lowStock: Int,
    outOfStock: Int,
    onInventory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ScottsTechXColors.SurfacePanelDark)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Listing health", color = ScottsTechXColors.OnDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                text = "Inventory",
                color = ScottsTechXColors.BluePrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClickLabel = "Open inventory") { onInventory() },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthChip("Approved", approved, ScottsTechXColors.SuccessGreen, Modifier.weight(1f))
            HealthChip("Pending", pending, ScottsTechXColors.WarningAmber, Modifier.weight(1f))
            HealthChip("Draft", draft, ScottsTechXColors.OnDarkMuted, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthChip("Rejected", rejected, ScottsTechXColors.ErrorRed, Modifier.weight(1f))
            HealthChip("Suspended", suspended, ScottsTechXColors.PinkAccent, Modifier.weight(1f))
            HealthChip("Out of stock", outOfStock, ScottsTechXColors.WarningAmber, Modifier.weight(1f))
        }
    }
}

private enum class ChipTone { Amber, Red, Blue }

/** Web-parity alert chip — color coding matches the web's alert-chip rows. */
@Composable
private fun AttentionChip(label: String, tone: ChipTone, onClick: () -> Unit) {
    val (bg, fg) = when (tone) {
        ChipTone.Amber -> Pair(Color(0x26F59E0B), Color(0xFFFBBF24))
        ChipTone.Red -> Pair(Color(0x26EF4444), Color(0xFFF87171))
        ChipTone.Blue -> Pair(Color(0x261E6FFF), Color(0xFF7BACFF))
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Spacer(Modifier.width(7.dp))
        Text(label, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Web dashboard "Store controls" card — accepting-orders switch, live
 * location sharing, and listing-status badges, all against the same
 * backend the web toggles (`/seller/open-state`, `/seller/location`).
 */
@Composable
private fun StoreControlsCard(
    acceptingOrders: Boolean,
    onToggleOrders: () -> Unit,
    sharing: Boolean,
    locBusy: Boolean,
    locHint: String?,
    lastFix: String?,
    onToggleSharing: (Boolean) -> Unit,
    statusCounts: SellerStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ScottsTechXColors.SurfacePanelDark)
            .padding(14.dp),
    ) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = ScottsTechXColors.BluePrimary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Store controls",
                color = ScottsTechXColors.OnDark,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
            )
        }
        Spacer(Modifier.height(12.dp))

        // row 1: accepting orders
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Accepting orders", color = ScottsTechXColors.OnDark, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Closed stores stay listed but marked closed.",
                    color = ScottsTechXColors.OnDarkSecondary, fontSize = 10.5.sp,
                )
            }
            androidx.compose.material3.Switch(
                checked = acceptingOrders,
                onCheckedChange = { onToggleOrders() },
            )
        }
        Spacer(Modifier.height(10.dp))

        // row 2: live location
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (sharing) Icons.Filled.MyLocation else Icons.Filled.LocationOff,
                        contentDescription = null,
                        tint = if (sharing) ScottsTechXColors.SuccessGreen else ScottsTechXColors.OnDarkSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Live location", color = ScottsTechXColors.OnDark, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (sharing) "Buyers see your store move in real time."
                    else "Off — buyers see only your last pinned position.",
                    color = ScottsTechXColors.OnDarkSecondary, fontSize = 10.5.sp,
                )
                if (lastFix != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("Last fix: $lastFix", color = ScottsTechXColors.OnDarkMuted, fontSize = 10.sp)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sharing) Color(0x33EF4444) else ScottsTechXColors.BluePrimary)
                    .clickable(enabled = !locBusy) { onToggleSharing(!sharing) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    when {
                        locBusy -> "Working…"
                        sharing -> "Turn off"
                        else -> "Turn on"
                    },
                    color = if (sharing) Color(0xFFF87171) else Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (locHint != null) {
            Spacer(Modifier.height(4.dp))
            Text(locHint, color = ScottsTechXColors.OnDarkSecondary, fontSize = 10.5.sp)
        }

        // row 3: listing status badges (web productsByStatus strip)
        val counts = listOf(
            "approved" to statusCounts.approved,
            "pending" to (statusCounts.pending + statusCounts.pendingApproval),
            "draft" to statusCounts.draft,
            "rejected" to statusCounts.rejected,
        ).filter { it.second > 0 }
        if (counts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Listing status", color = ScottsTechXColors.OnDarkSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                counts.forEach { (status, n) ->
                    val tone = when (status) {
                        "approved" -> Pair(Color(0x2622C55E), Color(0xFF4ADE80))
                        "pending" -> Pair(Color(0x26F59E0B), Color(0xFFFBBF24))
                        "rejected" -> Pair(Color(0x26EF4444), Color(0xFFF87171))
                        else -> Pair(Color(0x1F94A3C4), ScottsTechXColors.OnDarkSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tone.first)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("$n $status", color = tone.second, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthChip(label: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = count.toString(), color = accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = label, color = ScottsTechXColors.OnDarkSecondary, fontSize = 9.5.sp)
    }
}

@Composable
private fun TopProductRow(title: String, sold: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.SurfacePanelDark)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.TrendingUp, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = ScottsTechXColors.OnDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = "$sold sold", color = ScottsTechXColors.SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentOrderRow(
    order: com.scottsx.app.data.domain.SellerRecentOrder,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.SurfacePanelDark)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ScottsTechXColors.PurpleAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = order.buyerInitial, color = ScottsTechXColors.PurpleAccent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = order.productTitle,
                color = ScottsTechXColors.OnDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${order.buyerName}  ·  x${order.quantity}  ·  ${order.createdAt.take(10)}",
                color = ScottsTechXColors.OnDarkMuted,
                fontSize = 10.5.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(text = formatUgx(order.amount), color = ScottsTechXColors.OnDark, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(3.dp))
            OrderStatusChip(status = order.status, label = order.displayStatus)
        }
    }
}

@Composable
private fun OrderStatusChip(status: String, label: String) {
    val accent = when (status) {
        "delivered" -> ScottsTechXColors.SuccessGreen
        "paid", "shipped" -> ScottsTechXColors.BluePrimary
        "cancelled", "refunded" -> ScottsTechXColors.ErrorRed
        else -> ScottsTechXColors.WarningAmber
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = label, color = accent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LowStockBand(low: Int, out: Int, onInventory: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ScottsTechXColors.WarningAmber.copy(alpha = 0.10f))
            .clickable(onClickLabel = "Review low stock") { onInventory() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = ScottsTechXColors.WarningAmber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    if (low > 0) append("$low listing${if (low == 1) "" else "s"} running low")
                    if (low > 0 && out > 0) append("  ·  ")
                    if (out > 0) append("$out out of stock")
                },
                color = ScottsTechXColors.OnDark,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                // Long localized strings clamp instead of pushing the
                // count/chevron out of the band.
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Restock before they go quiet.", color = ScottsTechXColors.OnDarkSecondary, fontSize = 11.sp,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = ScottsTechXColors.WarningAmber, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SellerSkeleton(base: Color, highlight: Color) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { ShimmerBox(modifier = Modifier.weight(1f).height(96.dp), base = base, highlight = highlight) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { ShimmerBox(modifier = Modifier.weight(1f).height(96.dp), base = base, highlight = highlight) }
        }
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(130.dp), base = base, highlight = highlight)
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(96.dp), base = base, highlight = highlight)
        repeat(3) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(54.dp), base = base, highlight = highlight) }
    }
}
