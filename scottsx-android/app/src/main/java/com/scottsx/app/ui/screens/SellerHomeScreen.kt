package com.scottsx.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.LocationProvider
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.SellerDashboard
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.navigation.Routes
import com.scottsx.app.ui.components.AreaChart
import com.scottsx.app.ui.components.CountUpText
import com.scottsx.app.ui.components.HBarList
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.SectionHeader
import com.scottsx.app.ui.components.SellerBottomBar
import com.scottsx.app.ui.components.SellerBottomTab
import com.scottsx.app.ui.components.StatCard
import com.scottsx.app.ui.components.StatusChip
import com.scottsx.app.ui.components.bottomInset
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.formatUgxCompact
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Seller dashboard — the Android mirror of the web seller dashboard.
 *
 * Same backend payload (GET /seller/dashboard/stats), same content order:
 * hero + store controls, KPI grid, 14-day revenue chart, top products,
 * recent orders, moderation status, inventory.
 */
@Composable
fun SellerHomeScreen(
    onProductClick: (String) -> Unit,
    onAddProduct: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by SessionCache.user.collectAsState()

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var dash by remember { mutableStateOf<SellerDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }

    // Store controls (open/closed + live location) — same as the web toggles.
    var isOpen by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
    var locBusy by remember { mutableStateOf(false) }
    var locMessage by remember { mutableStateOf<String?>(null) }

    fun publishFix() {
        val fix = LocationProvider.lastKnown(context)
        if (fix == null) {
            locMessage = "No GPS fix yet — turn on location and try again."
            locBusy = false
            return
        }
        scope.launch {
            val ok = V2Client.publishSellerLocation(fix.lat, fix.lng)
            sharing = ok
            locMessage = if (ok) "Live location on — buyers see your store move." else "Could not publish location."
            locBusy = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) publishFix() else {
            locMessage = "Location permission denied."
            locBusy = false
        }
    }

    LaunchedEffect(Unit) {
        products = V2Client.fetchSellerProducts()
        dash = V2Client.fetchSellerDashboard()
        V2Client.fetchSellerLocation()?.let {
            isOpen = it.isOpen
            sharing = it.sharing
        }
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Clear the floating bottom bar AND the gesture pill.
            contentPadding = PaddingValues(bottom = 96.dp + bottomInset()),
        ) {
            // ── Hero: brand-blue gradient, store identity + quick stats ─────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(ScottsTechXColors.BlueHeroColors),
                            RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        )
                        .statusBarSpacer()
                        .padding(20.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Welcome back, ${(currentUser?.displayName ?: "seller").split(" ").first()}",
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Your store at a glance — revenue, stock health and what needs attention.",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                )
                            }
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Store settings",
                                tint = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable { onNavigate(Routes.PROFILE) }
                                    .padding(8.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            HeroStat(formatUgxCompact(dash?.revenueUgx ?: 0), "Revenue")
                            HeroStat("${dash?.orders ?: 0}", "Orders")
                            HeroStat("${dash?.totalProducts ?: 0}", "Listings")
                            HeroStat("${dash?.followers ?: 0}", "Followers")
                        }
                    }
                }
            }

            // ── Store controls: open/closed + live location ─────────────────
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        ControlRow(
                            icon = Icons.Filled.Inventory2,
                            title = if (isOpen) "Store open" else "Store closed",
                            subtitle = "Shown on your Nearby card",
                            checked = isOpen,
                            onToggle = { next ->
                                isOpen = next
                                scope.launch {
                                    if (!V2Client.setStoreOpen(next)) isOpen = !next
                                }
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        ControlRow(
                            icon = Icons.Filled.LocationOn,
                            title = "Live location",
                            subtitle = if (sharing) "Buyers see you move in real time" else "Pin stays at last known spot",
                            checked = sharing,
                            enabled = !locBusy,
                            onToggle = { next ->
                                locBusy = true
                                locMessage = null
                                if (!next) {
                                    scope.launch {
                                        if (V2Client.stopSellerLocation()) sharing = false
                                        locBusy = false
                                    }
                                } else if (LocationProvider.hasPermission(context)) {
                                    publishFix()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                        )
                        locMessage?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                }
            }

            dash?.let { d ->
                // ── KPI grid — same numbers as the web stat cards ──────────
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(
                                icon = Icons.Filled.Payments,
                                label = "Revenue (30d)",
                                value = d.revenue30Ugx,
                                formatter = { formatUgxCompact(it) },
                                accent = ScottsTechXColors.BluePrimary,
                                modifier = Modifier.weight(1f),
                            )
                            StatCard(
                                icon = Icons.Filled.LocalShipping,
                                label = "Orders (30d)",
                                value = d.orders30.toLong(),
                                accent = ScottsTechXColors.CyanAccent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(
                                icon = Icons.Filled.TrendingUp,
                                label = "Avg order value",
                                value = d.avgOrderValueUgx,
                                formatter = { formatUgxCompact(it) },
                                accent = ScottsTechXColors.PurpleAccent,
                                modifier = Modifier.weight(1f),
                            )
                            StatCard(
                                icon = Icons.Filled.Visibility,
                                label = "Product views",
                                value = d.totalViews.toLong(),
                                accent = ScottsTechXColors.SuccessGreen,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniStat(
                                icon = Icons.Filled.ChatBubble,
                                label = "Unread",
                                value = d.unreadMessages,
                                accent = ScottsTechXColors.BluePrimary,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(Routes.SELLER_MESSAGES) },
                            )
                            MiniStat(
                                icon = Icons.Filled.Groups,
                                label = "Followers",
                                value = d.followers,
                                accent = ScottsTechXColors.PurpleAccent,
                                modifier = Modifier.weight(1f),
                            )
                            MiniStat(
                                icon = Icons.Filled.WarningAmber,
                                label = "Low stock",
                                value = d.lowStock,
                                accent = ScottsTechXColors.WarningAmber,
                                modifier = Modifier.weight(1f),
                            )
                            MiniStat(
                                icon = Icons.Filled.Inventory2,
                                label = "In review",
                                value = d.pendingApproval,
                                accent = ScottsTechXColors.CyanAccent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ── 14-day revenue chart (same series the web renders) ──────
                if (d.salesSeries.isNotEmpty()) {
                    item {
                        DashPanel(title = "Sales — last 14 days") {
                            Row(verticalAlignment = Alignment.Bottom) {
                                CountUpText(
                                    target = d.salesSeries.sumOf { it.revenue },
                                    formatter = { formatUgx(it) },
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${d.salesSeries.sumOf { it.orders }} orders",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            AreaChart(
                                points = d.salesSeries.map { it.revenue },
                                labels = d.salesSeries.map { it.date.takeLast(5) },
                            )
                        }
                    }
                }

                // ── Top products ────────────────────────────────────────────
                val sold = d.topProducts.filter { it.sold > 0 }
                if (sold.isNotEmpty()) {
                    item {
                        DashPanel(title = "Top products") {
                            HBarList(items = sold.map { it.title to it.sold })
                        }
                    }
                }

                // ── Recent orders ───────────────────────────────────────────
                if (d.recentOrders.isNotEmpty()) {
                    item {
                        DashPanel(title = "Recent orders") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                d.recentOrders.take(6).forEach { o ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(
                                                    ScottsTechXColors.BluePrimary.copy(alpha = 0.13f),
                                                    CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                o.buyerName.firstOrNull()?.uppercase() ?: "B",
                                                color = ScottsTechXColors.BluePrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                o.productTitle,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                            )
                                            Text(
                                                "${o.buyerName} · ×${o.quantity}",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                formatUgx(o.amount * o.quantity),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            StatusChip(o.status)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Moderation status (sellers must see review outcomes) ────────
            val inReview = products.filter { it.status == "pending" }
            val rejected = products.filter { it.status == "rejected" }
            val suspended = products.filter { it.status == "suspended" }
            if (inReview.isNotEmpty() || rejected.isNotEmpty() || suspended.isNotEmpty()) {
                item {
                    SectionHeader("Listing status")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (inReview.isNotEmpty()) {
                            ModerationBanner(
                                emoji = "⏳",
                                tint = ScottsTechXColors.WarningAmber,
                                title = "${inReview.size} listing(s) awaiting admin review",
                                body = "You'll be notified as soon as they decide.",
                            )
                        }
                        rejected.forEach { product ->
                            ModerationBanner(
                                emoji = "⚠️",
                                tint = ScottsTechXColors.ErrorRed,
                                title = "Rejected: ${product.title.take(38)}",
                                body = product.rejectionReason
                                    ?: "Edit the listing and resubmit it for review.",
                            )
                        }
                        suspended.forEach { product ->
                            ModerationBanner(
                                emoji = "⛔",
                                tint = ScottsTechXColors.ErrorRed,
                                title = "Suspended: ${product.title.take(36)}",
                                body = product.rejectionReason
                                    ?: "This listing was taken down. Contact support for details.",
                            )
                        }
                    }
                }
            }

            // ── Inventory ───────────────────────────────────────────────────
            item { SectionHeader("Your inventory", action = "Add product", onAction = onAddProduct) }

            if (!loading && products.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("📦", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No products yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap the + button to list your first product.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                products.chunked(2).forEach { rowItems ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { product ->
                                ProductCard(
                                    product = product,
                                    onClick = { onProductClick(product.id) },
                                    modifier = Modifier.weight(1f),
                                    // A seller cannot wishlist their own stock;
                                    // what they need at a glance is whether the
                                    // listing is live, waiting on an admin, or
                                    // was rejected.
                                    showWishlist = false,
                                    statusLabel = product.status,
                                    compact = true,
                                )
                            }
                            // chunked(2) leaves the last row with a single item
                            // when the count is odd; a weighted spacer holds
                            // the missing cell so the card keeps half width.
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Low-stock quick list.
            val lowStock = products.filter { it.stockQuantity in 1..5 }
            if (lowStock.isNotEmpty()) {
                item {
                    SectionHeader("Inventory alerts")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(lowStock) { product ->
                            Surface(
                                color = ScottsTechXColors.WarningAmber.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable { onProductClick(product.id) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.WarningAmber,
                                        contentDescription = null,
                                        tint = ScottsTechXColors.WarningAmber,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "${product.title.take(18)} · ${product.stockQuantity} left",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SellerBottomBar(
            selected = SellerBottomTab.Home,
            onTabSelected = { tab ->
                when (tab) {
                    SellerBottomTab.AI -> onNavigate(Routes.SELLER_AI)
                    SellerBottomTab.Messages -> onNavigate(Routes.SELLER_MESSAGES)
                    SellerBottomTab.Analytics -> onNavigate(Routes.SELLER_ANALYTICS)
                    else -> Unit
                }
            },
            onAddClick = onAddProduct,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── Small private pieces ─────────────────────────────────────────────────────

@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.Start) {
        // Single line, hard-clamped: the width math in tools/layout-check
        // proves the expected figures fit, and the ellipsis is the backstop
        // so an unexpectedly long figure can never wrap or burst the tile.
        Text(
            value,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

/** Card wrapper for a dashboard block (chart, list…). */
@Composable
private fun DashPanel(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ControlRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = ScottsTechXColors.BluePrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = ScottsTechXColors.BluePrimary),
        )
    }
}

/** Compact tap-able KPI chip (unread, followers, low stock, in review). */
@Composable
private fun MiniStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(13.dp),
        shadowElevation = 1.dp,
        modifier = if (onClick != null) modifier.clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick) else modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(4.dp))
            Text("$value", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One-line moderation notice on the seller dashboard. */
@Composable
private fun ModerationBanner(
    emoji: String,
    tint: Color,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(emoji, fontSize = 16.sp)
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = tint)
            Text(
                body,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
