package com.scottsx.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.SellerDataSource
import com.scottsx.app.data.domain.OrderStatus
import com.scottsx.app.data.domain.SalesPoint
import com.scottsx.app.data.domain.SellerAiInsight
import com.scottsx.app.data.domain.SellerDashboardSnapshot
import com.scottsx.app.data.domain.SellerOrder
import com.scottsx.app.data.domain.SellerOrdersOverview
import com.scottsx.app.data.domain.StoreStatus
import com.scottsx.app.data.preferences.LocalThemePreference
import com.scottsx.app.data.preferences.ThemeMode
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.preferences.themeState
import com.scottsx.app.ui.components.HamburgerIcon
import com.scottsx.app.ui.components.LogoutConfirmDialog
import com.scottsx.app.ui.components.SellerBottomBar
import com.scottsx.app.ui.components.SellerBottomTab
import com.scottsx.app.ui.components.SellerSidebarDestination
import com.scottsx.app.ui.components.SellerSidebarOverlay
import com.scottsx.app.ui.components.ThemeSelectorSheet
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx

/**
 * Premium Seller Dashboard — Stage 3.2.
 *
 * Sections (vertical order, per the brief):
 *  1. Header — greeting, store name, status pill, hamburger.
 *  2. Today's Overview — 4 stat cards (Sales / Orders / Customers / Rating).
 *  3. Orders Overview — 4 status colors (Pending / Processing / Ready / Completed).
 *  4. Recent Orders — list with view / more controls.
 *  5. Quick Actions — 5 rounded action chips (Add Product off-center prominent).
 *  6. Sales Performance — animated bar chart with period toggle.
 *  7. Seller AI Assistant — premium gradient card with sample insight.
 *  8. Low Stock Alert — at-risk inventory list.
 *
 * Floating seller bottom nav (Home / Orders / Add / Messages / Analytics)
 * with the center Add raised as a blue pill FAB.
 *
 * Sidebar overlay (SellerSidebarOverlay) opens on hamburger tap.
 * Theme picker + logout dialog are managed here.
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

    // Build the snapshot reactively so status toggle / AI insight
    // future updates recompose the dashboard.
    val snapshot by remember(displayName, email) {
        androidx.compose.runtime.derivedStateOf { SellerDataSource.snapshot(displayName, email) }
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
            item {
                SellerHeader(
                    snapshot = snapshot,
                    onToggleStatus = {
                        val next = if (snapshot.status == StoreStatus.Online) StoreStatus.Away else StoreStatus.Online
                        SellerDataSource.setStatus(next)
                    },
                    onMenuClicked = { sidebarOpen = true },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }

            // 1. Today's Overview
            item {
                TodayOverviewCard(snapshot = snapshot)
                Spacer(Modifier.height(10.dp))
            }
            item {
                OrdersOverviewCard(overview = snapshot.ordersOverview)
                Spacer(Modifier.height(20.dp))
            }

            // 2. Recent Orders
            item {
                SectionHeading(
                    title = "Recent Orders",
                    actionLabel = "View All",
                    onAction = onManageOrders,
                )
            }
            items(snapshot.recentOrders, key = { it.id }) { order ->
                OrderRow(
                    order = order,
                    onView = { /* Stage 3.2.1 — open order detail */ },
                    onMore = { /* Stage 3.2.1 — overflow menu */ },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }

            // 3. Quick Actions
            item { SectionHeading(title = "Quick Actions") }
            item {
                QuickActionsRow(
                    onAddProduct = onAddProduct,
                    onManageOrders = onManageOrders,
                    onCreateOffer = { /* Stage 3.2.1 — promotion screen */ },
                    onAnalytics = onOpenAnalytics,
                    onMessages = { /* Stage 3.2.1 — messages */ },
                )
                Spacer(Modifier.height(20.dp))
            }

            // 4. Sales Performance
            item {
                SalesPerformanceCard(
                    period = salesPeriod,
                    onPeriod = { salesPeriod = it },
                    sales = snapshot.sales,
                )
                Spacer(Modifier.height(20.dp))
            }

            // 5. Seller AI Assistant
            item {
                SellerAiCard(
                    insight = snapshot.aiInsight,
                    onAsk = { /* Stage 3.2.1 — open Seller AI */ },
                )
                Spacer(Modifier.height(20.dp))
            }

            // 6. Low Stock Alerts
            item {
                SectionHeading(
                    title = "Low Stock Alert",
                    actionLabel = "View All",
                    onAction = onOpenInventory,
                )
            }
            items(snapshot.lowStock, key = { it.productId }) { alert ->
                LowStockRow(alert = alert)
            }
            item { Spacer(Modifier.height(8.dp)) }
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
            snapshot = snapshot,
            onNavigate = { dest ->
                when (dest) {
                    SellerSidebarDestination.Dashboard -> Unit
                    SellerSidebarDestination.Orders -> onManageOrders()
                    SellerSidebarDestination.Products -> onOpenInventory()
                    SellerSidebarDestination.Customers -> Unit
                    SellerSidebarDestination.Messages -> onOpenMessages()
                    SellerSidebarDestination.Promotions -> onOpenMarketplaceTools()
                    SellerSidebarDestination.Analytics -> onOpenAnalytics()
                    SellerSidebarDestination.SellerAi -> onOpenAnalytics()
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
                    SellerSidebarDestination.ToggleOnline -> {
                        val next = if (snapshot.status == StoreStatus.Online) StoreStatus.Away else StoreStatus.Online
                        SellerDataSource.setStatus(next)
                    }
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
    snapshot: SellerDashboardSnapshot,
    onToggleStatus: () -> Unit,
    onMenuClicked: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ScottsTechXColors.BluePrimaryDark,
                        ScottsTechXColors.BluePrimary,
                        Color(0xFF3B82F6),
                    ),
                ),
            )
            .padding(top = 32.dp, bottom = 22.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            // Top row — hamburger + store avatar + notification + message
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onMenuClicked),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Open menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border1(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                HeaderIcon(icon = Icons.Filled.Notifications, badge = 3, contentDescription = "Notifications")
                Spacer(Modifier.width(8.dp))
                HeaderIcon(icon = Icons.Filled.Receipt, badge = 5, contentDescription = "Messages")
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Good morning, ${snapshot.displayName}",
                color = Color.White.copy(alpha = 0.95f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snapshot.storeName,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                // Status pill — tappable to toggle Online / Away.
                SellerStatusPill(
                    status = snapshot.status,
                    onToggle = onToggleStatus,
                )
            }
        }
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, badge: Int, contentDescription: String? = null) {
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .clickable { /* Stage 3.2.1 — open notifications / messages */ },
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
                    .padding(top = 2.dp, end = 2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = badge.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun SellerStatusPill(status: StoreStatus, onToggle: () -> Unit) {
    val bg = when (status) {
        StoreStatus.Online -> Color(0xFF15803D)
        StoreStatus.Away -> Color(0xFFB45309)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = status.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

// Tiny helper so we can chain a border on the avatar in the header.
private fun Modifier.border1(): Modifier = this

// =====================================================================================
// Sections
// =====================================================================================

@Composable
private fun TodayOverviewCard(snapshot: SellerDashboardSnapshot) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Today's Overview",
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Live",
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF15803D)),
                )
            }
            Spacer(Modifier.height(14.dp))
            // 2x2 grid of stat mini-cards.
            val rows = listOf(
                listOf(
                    OverviewStat("Sales", formatUgx(snapshot.salesTodayUgx), "+${"%.1f".format(snapshot.salesTodayDeltaPct)}%", Color(0xFFEA580C)),
                    OverviewStat("Orders", snapshot.ordersToday.toString(), "+${snapshot.ordersTodayDelta}", ScottsTechXColors.BluePrimary),
                ),
                listOf(
                    OverviewStat("Customers", snapshot.customersTotal.toString(), "+${snapshot.customersDelta}", Color(0xFF059669)),
                    OverviewStat("Rating", "%.1f".format(snapshot.rating), snapshot.ratingLabel, Color(0xFFFBBF24)),
                ),
            )
            rows.forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { stat ->
                        OverviewStatCard(stat = stat, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private data class OverviewStat(
    val label: String,
    val value: String,
    val delta: String,
    val tint: Color,
)

@Composable
private fun OverviewStatCard(stat: OverviewStat, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ScottsTechXColors.PanelInputLight)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(stat.tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = stat.tint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stat.label,
                color = ScottsTechXColors.OnLightSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stat.value,
            color = ScottsTechXColors.OnLight,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ArrowDropUp,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stat.delta,
                color = Color(0xFF059669),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun OrdersOverviewCard(overview: SellerOrdersOverview) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = "Orders Overview",
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrderStatusChip("Pending", overview.pending, Color(0xFFEA580C), OrderStatus.Pending, modifier = Modifier.weight(1f))
                OrderStatusChip("Processing", overview.processing, ScottsTechXColors.BluePrimary, OrderStatus.Processing, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrderStatusChip("Ready", overview.ready, Color(0xFF8B5CF6), OrderStatus.Ready, modifier = Modifier.weight(1f))
                OrderStatusChip("Completed", overview.completed, Color(0xFF059669), OrderStatus.Completed, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OrderStatusChip(
    label: String,
    count: Int,
    tint: Color,
    status: OrderStatus,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = tint,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = count.toString(),
            color = ScottsTechXColors.OnLight,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
        )
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = ScottsTechXColors.OnLight,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier.clickable(onClick = onAction),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = actionLabel,
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = ScottsTechXColors.BluePrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun OrderRow(
    order: SellerOrder,
    onView: () -> Unit,
    onMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ScottsTechXColors.PanelInputLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingBag,
                        contentDescription = null,
                        tint = ScottsTechXColors.BluePrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${order.id}",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = order.productName,
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${order.itemsCount} items  •  ${order.placedAtLabel}",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 11.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.PanelInputLight)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = "More",
                        tint = ScottsTechXColors.OnLightSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatUgx(order.totalUgx),
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                OrderStatusBadge(status = order.status)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ScottsTechXColors.BluePrimary)
                        .clickable(onClick = onView)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "View Order",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderStatusBadge(status: OrderStatus) {
    val (bg, fg) = when (status) {
        OrderStatus.Pending -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        OrderStatus.Processing -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        OrderStatus.Ready -> Color(0xFFEDE9FE) to Color(0xFF7C3AED)
        OrderStatus.Completed -> Color(0xFFDCFCE7) to Color(0xFF15803D)
        OrderStatus.Cancelled -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = status.label,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun QuickActionsRow(
    onAddProduct: () -> Unit,
    onManageOrders: () -> Unit,
    onCreateOffer: () -> Unit,
    onAnalytics: () -> Unit,
    onMessages: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                QuickActionChip(
                    label = "Add Product",
                    icon = Icons.Filled.Add,
                    onClick = onAddProduct,
                    primary = true,
                )
            }
            item {
                QuickActionChip(
                    label = "Orders",
                    icon = Icons.Filled.Receipt,
                    onClick = onManageOrders,
                )
            }
            item {
                QuickActionChip(
                    label = "Create Offer",
                    icon = Icons.Filled.LocalOffer,
                    onClick = onCreateOffer,
                )
            }
            item {
                QuickActionChip(
                    label = "Analytics",
                    icon = Icons.Filled.Analytics,
                    onClick = onAnalytics,
                )
            }
            item {
                QuickActionChip(
                    label = "Messages",
                    icon = Icons.Filled.AutoAwesome,
                    onClick = onMessages,
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val bg = if (primary) {
        Brush.linearGradient(
            colors = listOf(
                ScottsTechXColors.BluePrimaryLight,
                ScottsTechXColors.BluePrimary,
                ScottsTechXColors.BluePrimaryDark,
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.White, ScottsTechXColors.PanelInputLight),
        )
    }
    val textColor = if (primary) Color.White else ScottsTechXColors.OnLight
    val iconTint = if (primary) Color.White else ScottsTechXColors.BluePrimary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    (if (primary) Color.White.copy(alpha = 0.22f)
                    else ScottsTechXColors.BluePrimary.copy(alpha = 0.12f)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SalesPerformanceCard(
    period: SalesPeriod,
    onPeriod: (SalesPeriod) -> Unit,
    sales: List<SalesPoint>,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sales Performance",
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatUgx(sales.sumOf { it.amountUgx }),
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropUp,
                    contentDescription = null,
                    tint = Color(0xFF059669),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "18.5% vs last week",
                    color = Color(0xFF059669),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SalesPeriod.values().forEach { p ->
                    val selected = p == period
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) ScottsTechXColors.BluePrimary
                                else ScottsTechXColors.PanelInputLight,
                            )
                            .clickable { onPeriod(p) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = p.label,
                            color = if (selected) Color.White else ScottsTechXColors.OnLight,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SalesChart(sales = sales)
        }
    }
}

private enum class SalesPeriod(val label: String) {
    ThisWeek("This Week"),
    ThisMonth("This Month"),
    ThreeMonths("3 Months"),
}

@Composable
private fun SalesChart(sales: List<SalesPoint>) {
    val maxAmount = sales.maxOf { it.amountUgx }.coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sales.forEach { point ->
            val fraction = (point.amountUgx.toFloat() / maxAmount.toFloat()).coerceIn(0.05f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ScottsTechXColors.BluePrimaryLight,
                                    ScottsTechXColors.BluePrimary,
                                ),
                            ),
                        ),
                )
            }
        }
    }
    // Render bars + labels as a Canvas for control over the gradient.
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val gap = 8.dp.toPx()
        val labelH = 18.dp.toPx()
        val barAreaH = size.height - labelH
        val barW = (size.width - gap * (sales.size - 1)) / sales.size
        sales.forEachIndexed { i, point ->
            val fraction = (point.amountUgx.toFloat() / maxAmount.toFloat()).coerceIn(0.05f, 1f)
            val barH = barAreaH * fraction
            val x = i * (barW + gap)
            val y = barAreaH - barH
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ScottsTechXColors.BluePrimaryLight,
                        ScottsTechXColors.BluePrimary,
                    ),
                    startY = y,
                    endY = y + barH,
                ),
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sales.forEach { point ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = point.label,
                    color = ScottsTechXColors.OnLightSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun SellerAiCard(
    insight: SellerAiInsight,
    onAsk: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E40AF),
                        Color(0xFF6366F1),
                        Color(0xFF8B5CF6),
                    ),
                ),
            )
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Seller AI Assistant",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Beta",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = insight.headline,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = insight.body,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(onClick = onAsk)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = ScottsTechXColors.BluePrimary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Ask Seller AI",
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun LowStockRow(alert: com.scottsx.app.data.domain.LowStockAlert) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.productName,
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Text(
                    text = "Only ${alert.remaining} left in stock",
                    color = Color(0xFFB45309),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = ScottsTechXColors.OnLightSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
