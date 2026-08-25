package com.scottsx.app.ui.screens

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.CartStore
import com.scottsx.app.data.domain.BannerBackground
import com.scottsx.app.data.domain.Benefit
import com.scottsx.app.data.domain.BenefitIcon
import com.scottsx.app.data.domain.BuyerProfile
import com.scottsx.app.data.domain.HeroBanner
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.preferences.ThemeMode
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.preferences.themeState
import com.scottsx.app.data.preferences.sidebarPaletteFor
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.BenefitsStrip
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.components.BuyerHeader
import com.scottsx.app.ui.components.BuyerSidebarOverlay
import com.scottsx.app.ui.components.CategoryRow
import com.scottsx.app.ui.components.FeedEmptyCard
import com.scottsx.app.ui.components.FeedErrorCard
import com.scottsx.app.ui.components.HeroCarousel
import com.scottsx.app.ui.components.LogoutConfirmDialog
import com.scottsx.app.ui.components.NearbyAiCard
import com.scottsx.app.ui.components.PulsingDot
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.Reveal
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.components.SectionTitle
import com.scottsx.app.ui.components.ShimmerBox
import com.scottsx.app.ui.components.SidebarDestination
import com.scottsx.app.ui.components.ThemeSelectorSheet
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Load state of the live buyer feeds (no demo fallback exists). */
private enum class BuyerFeedState { Loading, Ready, Error }

/**
 * Real marketplace facts shown in the benefits strip. These describe
 * platform capabilities that exist end-to-end (returns, refunds and
 * delivery are backed by real backend endpoints) — they are copy,
 * not data, so no numbers are invented.
 */
private val marketplaceBenefits = listOf(
    Benefit("Nationwide delivery", "Kampala same-day · regions 2–4 days", BenefitIcon.Delivery),
    Benefit("Secure checkout", "Mobile money, cards & bank transfer", BenefitIcon.Security),
    Benefit("Buyer protection", "Refund requests handled in-app", BenefitIcon.Protection),
    Benefit("Easy returns", "Request straight from your orders", BenefitIcon.Returns),
)

/**
 * Buyer Home Dashboard — rebuilt on live backend data.
 *
 * Every feed on this screen comes from the same single backend the
 * website uses (`/api/v1/products`):
 *   • Hero carousel      → real flash-deal listings (or one brand banner)
 *   • Flash Deals        → real `flashOnly` listings, live discount badge
 *   • Fresh for you      → real `sort=newest`, in-stock listings
 *   • Trending now       → real `sort=popular` listings
 *
 * While loading: shimmer skeletons. On failure: a real error card with
 * Retry. With no listings: an honest empty state. There is no static
 * catalogue fallback anywhere in this screen.
 *
 * The bottom navigation bar, the sidebar overlay, the theme sheet and
 * the logout dialog are unchanged from the original design.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BuyerHomeScreen(
    profile: BuyerProfile,
    onNavigateToCart: () -> Unit,
    onNavigateToCategory: (ProductCategory) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNearby: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToAllProducts: () -> Unit,
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToReceipts: () -> Unit = {},
    onNavigateToAiPersonalization: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSellerCenter: () -> Unit = {},
    onNavigateToBecomeSeller: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenProduct: (com.scottsx.app.data.domain.Product) -> Unit = {},
    onOpenStore: (String) -> Unit = {},
    onTabSelect: (BottomTab) -> Unit,
    onSignOutRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cartItems by CartStore.items.collectAsState()
    val cartCount = cartItems.sumOf { it.quantity }

    // ---- Live marketplace data (the single backend the website uses) ----
    var feedState by remember { mutableStateOf(BuyerFeedState.Loading) }
    var forYou by remember { mutableStateOf<List<Product>>(emptyList()) }
    var flashDeals by remember { mutableStateOf<List<Product>>(emptyList()) }
    var trending by remember { mutableStateOf<List<Product>>(emptyList()) }
    var notifCount by remember { mutableStateOf(0) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        feedState = BuyerFeedState.Loading
        try {
            val forYouDeferred = async {
                V2Client.fetchProductsFeed(sort = "newest", inStock = true, pageSize = 24, strict = true)
            }
            val flashDeferred = async {
                V2Client.fetchProductsFeed(flashOnly = true, inStock = true, pageSize = 12, strict = true)
            }
            val trendingDeferred = async {
                V2Client.fetchProductsFeed(sort = "popular", inStock = true, pageSize = 12, strict = true)
            }
            val unreadDeferred = async { V2Client.fetchUnreadNotificationCount() }
            forYou = forYouDeferred.await()
            flashDeals = flashDeferred.await()
            trending = trendingDeferred.await()
            notifCount = unreadDeferred.await()
            feedState = BuyerFeedState.Ready
        } catch (_: Exception) {
            feedState = BuyerFeedState.Error
        }
    }

    val heroProducts = flashDeals
    val heroBanners = remember(heroProducts) {
        if (heroProducts.isEmpty()) {
            listOf(
                HeroBanner(
                    id = "brand",
                    title = "Uganda's market, in one app",
                    subtitle = "Buy from real local sellers with secure checkout",
                    supportingText = "SCOTTS TECH X",
                    cta = "Browse products",
                    background = BannerBackground.BluePurple,
                ),
            )
        } else {
            heroProducts.take(5).mapIndexed { i, p ->
                HeroBanner(
                    id = p.id,
                    title = p.name,
                    subtitle = buildString {
                        append(formatUgx(p.priceUgx))
                        p.oldPriceUgx?.let { old ->
                            append("  ·  was ")
                            append(formatUgx(old))
                        }
                        if (p.stock in 1..5) {
                            append("  ·  only ")
                            append(p.stock.toString())
                            append(" left")
                        }
                    },
                    supportingText = when {
                        p.discountPercent > 0 -> "SAVE ${p.discountPercent}%"
                        p.stock in 1..5 -> "LOW STOCK"
                        else -> "FLASH DEAL"
                    },
                    cta = "View deal",
                    background = when (i % 4) {
                        0 -> BannerBackground.BluePurple
                        1 -> BannerBackground.DarkNavy
                        2 -> BannerBackground.GreenTeal
                        else -> BannerBackground.Sunset
                    },
                )
            }
        }
    }

    var selectedCategory by remember { mutableStateOf(ProductCategory.All) }
    var bottomTab by remember { mutableStateOf(BottomTab.Home) }

    // --- Stage 3.1 sidebar overlay state ---
    var sidebarOpen by remember { mutableStateOf(false) }
    var themeSheetOpen by remember { mutableStateOf(false) }
    var logoutDialogOpen by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val themePref = remember(ctx) { ThemePreference.get(ctx) }
    val themeMode by themePref.themeState()
    val onSidebarNav: (SidebarDestination) -> Unit = { dest ->
        when (dest) {
            SidebarDestination.Home -> onTabSelect(BottomTab.Home)
            SidebarDestination.Nearby -> onNavigateToNearby()
            SidebarDestination.Ai -> onNavigateToAi()
            SidebarDestination.Wishlist -> onTabSelect(BottomTab.Wishlist)
            SidebarDestination.Cart -> onNavigateToCart()
            SidebarDestination.Orders -> onNavigateToAllProducts()
            SidebarDestination.Transactions -> onNavigateToTransactions()
            SidebarDestination.Receipts -> onNavigateToReceipts()
            SidebarDestination.AiPersonalization -> onNavigateToAiPersonalization()
            SidebarDestination.Messages -> onNavigateToMessages()
            SidebarDestination.Notifications -> onNavigateToNotifications()
            SidebarDestination.SellerCenter -> onNavigateToSellerCenter()
            SidebarDestination.BecomeSeller -> onNavigateToBecomeSeller()
            SidebarDestination.Settings -> onNavigateToSettings()
            SidebarDestination.Theme -> themeSheetOpen = true
            SidebarDestination.Logout -> logoutDialogOpen = true
            SidebarDestination.Profile -> onTabSelect(BottomTab.Profile)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp),  // leave room for floating nav
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            // 1. Header — gradient backdrop split into two rows:
            //    Row 1: Hamburger (left) + brand spacer
            //    Row 2: BuyerHeader (avatar + welcome text + notification/cart)
            //    The hamburger and the avatar/welcome text are stacked
            //    vertically so they never overlap.
            item {
                Column(
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
                        .padding(top = 32.dp, bottom = 18.dp),
                ) {
                    // Top utility bar — hamburger only, leaves the rest
                    // of the surface for the header content below.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable { sidebarOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open menu",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Avatar + welcome text row (the original BuyerHeader).
                    // The badge is the REAL unread count from the backend.
                    BuyerHeader(
                        displayName = profile.displayName,
                        email = profile.email,
                        notificationCount = notifCount,
                        cartCount = cartCount,
                        onNotificationsClick = onNavigateToNotifications,
                        onCartClick = onNavigateToCart,
                    )
                }
            }

            // 2. Search bar + filter
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BuyerSearchBar(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSearch,
                    )
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
                                ),
                            )
                            .clickable { onNavigateToSearch() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filters",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // 3–8. Live feed (loading / error / ready)
            when (feedState) {
                BuyerFeedState.Loading -> item {
                    BuyerFeedSkeleton()
                }

                BuyerFeedState.Error -> item {
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FeedErrorCard(
                            onRetry = { refreshTick++ },
                            surface = ScottsTechXColors.SurfacePanelDark,
                            foreground = ScottsTechXColors.OnDark,
                            secondary = ScottsTechXColors.OnDarkSecondary,
                            accent = ScottsTechXColors.BluePrimary,
                        )
                    }
                }

                BuyerFeedState.Ready -> {
                    if (forYou.isEmpty() && flashDeals.isEmpty() && trending.isEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                FeedEmptyCard(
                                    title = "No products yet",
                                    message = "Sellers are still stocking the marketplace. Check back soon, or start exploring the categories.",
                                    actionLabel = "Browse categories",
                                    onAction = onNavigateToAllProducts,
                                    surface = ScottsTechXColors.SurfacePanelDark,
                                    foreground = ScottsTechXColors.OnDark,
                                    secondary = ScottsTechXColors.OnDarkSecondary,
                                    accent = ScottsTechXColors.BluePrimary,
                                )
                            }
                        }
                    } else {
                        // 3. Hero carousel — real flash-deal listings.
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Reveal(index = 0) {
                                    HeroCarousel(
                                        banners = heroBanners,
                                        onCtaClick = { banner ->
                                            val target = heroProducts.firstOrNull { it.id == banner.id }
                                            if (target != null) onOpenProduct(target) else onNavigateToAllProducts()
                                        },
                                    )
                                }
                            }
                        }

                        // 4. Category row
                        item {
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Reveal(index = 1) {
                                    CategoryRow(
                                        selected = selectedCategory,
                                        onSelect = { cat ->
                                            selectedCategory = cat
                                            onNavigateToCategory(cat)
                                        },
                                    )
                                }
                            }
                        }

                        // 5. Marketplace benefits
                        item {
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Reveal(index = 2) {
                                    BenefitsStrip(benefits = marketplaceBenefits)
                                }
                            }
                        }

                        // 6. Nearby + AI Assistant
                        item {
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Reveal(index = 3) {
                                    NearbyAiCard(
                                        onNearbyClick = onNavigateToNearby,
                                        onAiClick = onNavigateToAi,
                                    )
                                }
                            }
                        }

                        // 7. Flash Deals — real listings, real discount badge
                        if (flashDeals.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Reveal(index = 4) {
                                        SectionTitle(
                                            title = "Flash Deals",
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.LocalFireDepartment,
                                                    contentDescription = null,
                                                    tint = ScottsTechXColors.BluePrimary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                            viewAll = "View All >",
                                            onViewAll = onNavigateToAllProducts,
                                        )
                                    }
                                    LiveDealBadge(
                                        maxDiscount = flashDeals.maxOfOrNull { it.discountPercent } ?: 0,
                                    )
                                }
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(flashDeals, key = { it.id }) { product ->
                                        ProductCard(
                                            product = product,
                                            onClick = { onOpenProduct(product) },
                                            onAddToCart = { CartStore.add(product.id) },
                                        )
                                    }
                                }
                            }
                        }

                        // 8. Fresh for you — real newest, in-stock listings
                        if (forYou.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(22.dp))
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Reveal(index = 4) {
                                        SectionTitle(
                                            title = "Fresh for you",
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.TrendingUp,
                                                    contentDescription = null,
                                                    tint = ScottsTechXColors.BluePrimary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                            viewAll = "View All >",
                                            onViewAll = onNavigateToAllProducts,
                                        )
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(forYou, key = { it.id }) { product ->
                                        ProductCard(
                                            product = product,
                                            onClick = { onOpenProduct(product) },
                                            onAddToCart = { CartStore.add(product.id) },
                                        )
                                    }
                                }
                            }
                        }

                        // 9. Trending now — real popular sort
                        if (trending.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(22.dp))
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Reveal(index = 4) {
                                        SectionTitle(
                                            title = "Trending now",
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.TrendingUp,
                                                    contentDescription = null,
                                                    tint = ScottsTechXColors.BluePrimary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                            viewAll = "View All >",
                                            onViewAll = onNavigateToAllProducts,
                                        )
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(trending, key = { it.id }) { product ->
                                        ProductCard(
                                            product = product,
                                            onClick = { onOpenProduct(product) },
                                            onAddToCart = { CartStore.add(product.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // bottom padding helper
            item {
                Spacer(Modifier.height(8.dp))
            }
        }

        // 10. Sidebar overlay (Stage 3.1). Always rendered but only
        //     visible when [sidebarOpen] is true.
        BuyerSidebarOverlay(
            open = sidebarOpen,
            onDismiss = { sidebarOpen = false },
            profile = profile,
            cartCount = cartCount,
            wishlistCount = 0,
            // Real counts only — unknown values show 0 (honest), never
            // the demo seed data from MarketplaceDataSource.
            messagesCount = 0,
            notificationsCount = notifCount,
            ordersCount = 0,
            onNavigate = { dest ->
                onSidebarNav(dest)
            },
        )

        // 9. Floating bottom nav — anchored to the bottom of the screen.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            ScottsTechXBottomBar(
                selected = bottomTab,
                onSelect = { tab ->
                    bottomTab = tab
                    onTabSelect(tab)
                },
            )
        }

        // 11. Theme selector sheet — pinned at the bottom; tap any row
        //     to apply + dismiss.
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

        // 12. Logout confirmation dialog (Stage 3.1 brief).
        if (logoutDialogOpen) {
            LogoutConfirmDialog(
                onCancel = { logoutDialogOpen = false },
                onConfirm = {
                    logoutDialogOpen = false
                    sidebarOpen = false
                    // Defer the actual sign-out to AppNavigation. We only
                    // close the drawer here; the parent composable owns
                    // auth state and decides where to navigate.
                    onSignOutRequested()
                },
            )
        }
    }
}

/** Pulsing "LIVE" tag + real max-discount chip for the Flash Deals row. */
@Composable
private fun LiveDealBadge(maxDiscount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulsingDot(color = Color(0xFF34D399))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "LIVE",
            color = Color(0xFF34D399),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        if (maxDiscount > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE11D48))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "UP TO −$maxDiscount%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Shimmer skeleton for the loading feed — mirrors the real section
 * layout (hero, categories, benefits, nearby, product rows) so the
 * transition into live data is seamless.
 */
@Composable
private fun BuyerFeedSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            shape = RoundedCornerShape(22.dp),
            base = ScottsTechXColors.SurfacePanelDark,
            highlight = ScottsTechXColors.SurfaceElevatedDark,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                ShimmerBox(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    base = ScottsTechXColors.SurfacePanelDark,
                    highlight = ScottsTechXColors.SurfaceElevatedDark,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp),
            shape = RoundedCornerShape(18.dp),
            base = ScottsTechXColors.SurfacePanelDark,
            highlight = ScottsTechXColors.SurfaceElevatedDark,
        )
        Spacer(Modifier.height(16.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(135.dp),
            shape = RoundedCornerShape(18.dp),
            base = ScottsTechXColors.SurfacePanelDark,
            highlight = ScottsTechXColors.SurfaceElevatedDark,
        )
        Spacer(Modifier.height(20.dp))
        ShimmerBox(
            modifier = Modifier
                .width(160.dp)
                .height(20.dp),
            shape = RoundedCornerShape(10.dp),
            base = ScottsTechXColors.SurfacePanelDark,
            highlight = ScottsTechXColors.SurfaceElevatedDark,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { BuyerProductCardSkeleton() }
        }
    }
}

@Composable
private fun BuyerProductCardSkeleton() {
    Column {
        ShimmerBox(
            modifier = Modifier
                .size(180.dp),
            shape = RoundedCornerShape(18.dp),
            base = ScottsTechXColors.SurfacePanelDark,
            highlight = ScottsTechXColors.SurfaceElevatedDark,
        )
        Spacer(Modifier.height(8.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            shape = RoundedCornerShape(7.dp),
            base = ScottsTechXColors.SurfacePanelDark,
            highlight = ScottsTechXColors.SurfaceElevatedDark,
        )
        Spacer(Modifier.height(6.dp))
        ShimmerBox(
            modifier = Modifier
                .width(120.dp)
                .height(14.dp),
            shape = RoundedCornerShape(7.dp),
            base = ScottsTechXColors.SurfacePanelDark,
            highlight = ScottsTechXColors.SurfaceElevatedDark,
        )
        Spacer(Modifier.height(6.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            shape = RoundedCornerShape(6.dp),
            base = ScottsTechXColors.SurfacePanelDark,
            highlight = ScottsTechXColors.SurfaceElevatedDark,
        )
    }
}

@Composable
private fun BuyerSearchBar(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = ScottsTechXColors.OnLightSecondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Search for products, brands and categories...",
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
