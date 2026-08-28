package com.scottsx.app.ui.screens

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.data.CartStore
import com.scottsx.app.data.domain.BuyerProfile
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.preferences.sidebarPaletteFor
import com.scottsx.app.data.preferences.themeState
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.components.BuyerSidebarOverlay
import com.scottsx.app.ui.components.CategoryRow
import com.scottsx.app.ui.components.FeedEmptyCard
import com.scottsx.app.ui.components.FeedErrorCard
import com.scottsx.app.ui.components.HamburgerIcon
import com.scottsx.app.ui.components.LogoutConfirmDialog
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.PulsingDot
import com.scottsx.app.ui.components.Reveal
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.components.SectionTitle
import com.scottsx.app.ui.components.ShimmerBox
import com.scottsx.app.ui.components.SidebarDestination
import com.scottsx.app.ui.components.ThemeSelectorSheet
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Buyer Home — rebuilt on the web contract.
 *
 * EVERY number and product on this screen is live backend data
 * (`/api/v1/products`, `/api/v1/me/notifications/unread-count`) — the
 * same endpoints the website renders. The in-memory sample catalogue
 * is gone: loading shows shimmer skeletons, failure shows a retry
 * card, an empty catalogue shows an honest empty state.
 *
 * Web-mirrored hierarchy:
 *   1. Greeting header (real unread badge, cart count)
 *   2. Search + filter
 *   3. Hero — live "top deal" spotlight (real max discount in feed)
 *   4. Categories
 *   5. Flash Deals (real `isFlashDeal` listings)
 *   6. For You (real listings, grid rows)
 *
 * Static feature/benefit/payment cards were removed per the
 * production-brief — only real commerce stays.
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
    onOpenProduct: (Product) -> Unit = {},
    onOpenStore: (String) -> Unit = {},
    onTabSelect: (BottomTab) -> Unit,
    onSignOutRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cartItems by CartStore.items.collectAsState()
    val cartCount = cartItems.sumOf { it.quantity }

    // ---- Live feed state ---------------------------------------------------
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var feedState by remember { mutableStateOf(FeedState.Loading) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var unread by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        feedState = FeedState.Loading
        feedState = kotlinx.coroutines.coroutineScope {
            var feed: List<Product>? = null
            val feedJob = launch { feed = V2Client.fetchProductsListOrNull() }
            val badgeJob = launch { unread = V2Client.fetchUnreadNotificationCount() }
            feedJob.join(); badgeJob.join()
            val result = feed
            if (result == null) {
                FeedState.Error
            } else {
                products = result
                FeedState.Ready
            }
        }
    }

    val flashDeals by remember(products) {
        derivedStateOf { products.filter { it.isFlashDeal }.take(8) }
    }
    val forYou by remember(products) {
        derivedStateOf {
            products.filter { !it.isFlashDeal }
                .sortedByDescending { it.rating }
                .take(10)
        }
    }
    val topDeal by remember(products) {
        derivedStateOf { flashDeals.maxByOrNull { it.discountPercent } }
    }

    // ---- Chrome state ------------------------------------------------------
    var selectedCategory by remember { mutableStateOf(ProductCategory.All) }
    var bottomTab by remember { mutableStateOf(BottomTab.Home) }
    var sidebarOpen by remember { mutableStateOf(false) }
    var themeSheetOpen by remember { mutableStateOf(false) }
    var logoutDialogOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
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

    val skeletonBase = ScottsTechXColors.SurfaceElevatedDark
    val skeletonHighlight = Color(0xFF1B2743)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // 1. Header — greeting + real badges
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 16.dp, top = 34.dp),
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
                            text = greeting(),
                            color = ScottsTechXColors.OnDarkSecondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = profile.displayName,
                            color = ScottsTechXColors.OnDark,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HeaderBadge(
                        count = unread,
                        description = "Notifications",
                        icon = { tint ->
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notifications",
                                tint = tint,
                                modifier = Modifier.size(21.dp),
                            )
                        },
                        onClick = onNavigateToNotifications,
                    )
                    Spacer(Modifier.width(10.dp))
                    HeaderBadge(
                        count = cartCount,
                        description = "Cart",
                        icon = { tint ->
                            Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = "Cart",
                                tint = tint,
                                modifier = Modifier.size(21.dp),
                            )
                        },
                        onClick = onNavigateToCart,
                    )
                }
            }

            // 2. Search + filter
            item {
                Reveal(index = 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ScottsTechXColors.SurfaceElevatedDark)
                                .clickable { onNavigateToSearch() },
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
                                    tint = ScottsTechXColors.OnDarkMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Search phones, shoes, groceries...",
                                    color = ScottsTechXColors.OnDarkMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ScottsTechXColors.BrandGradient)
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
            }

            when (feedState) {
                FeedState.Loading -> {
                    item { BuyerSkeleton(skeletonBase, skeletonHighlight) }
                }
                FeedState.Error -> {
                    item {
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
                FeedState.Ready -> {
                    if (products.isEmpty()) {
                        item {
                            FeedEmptyCard(
                                title = "No products yet",
                                message = "Sellers in your market haven't listed anything yet — check back soon, or start selling yourself.",
                                actionLabel = "Become a seller",
                                onAction = onNavigateToBecomeSeller,
                                modifier = Modifier.padding(16.dp),
                                surface = ScottsTechXColors.SurfacePanelDark,
                                foreground = ScottsTechXColors.OnDark,
                                secondary = ScottsTechXColors.OnDarkSecondary,
                                accent = ScottsTechXColors.BluePrimary,
                            )
                        }
                    } else {
                        // 3. Hero — the biggest real flash deal right now
                        item {
                            Reveal(index = 1) {
                                HeroSpotlight(
                                    deal = topDeal,
                                    totalDeals = flashDeals.size,
                                    onOpenDeal = { topDeal?.let(onOpenProduct) },
                                    onBrowse = onNavigateToAllProducts,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }

                        // 4. Categories (real catalogue domains)
                        item {
                            Spacer(Modifier.height(16.dp))
                            Reveal(index = 2) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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

                        // 5. Flash Deals — real listings only
                        if (flashDeals.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(20.dp))
                                Reveal(index = 3) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        PulsingDot(color = ScottsTechXColors.CyanAccent)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "LIVE",
                                            color = ScottsTechXColors.CyanAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.2.sp,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Box(Modifier.weight(1f)) {
                                            SectionTitle(
                                                title = "Flash Deals",
                                                viewAll = "View All",
                                                onViewAll = onNavigateToAllProducts,
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(10.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
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

                        // 6. For You — real catalogue, rating-sorted
                        item {
                            Spacer(Modifier.height(22.dp))
                            Reveal(index = 4) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    SectionTitle(
                                        title = "For You",
                                        viewAll = "View All",
                                        onViewAll = onNavigateToAllProducts,
                                    )
                                }
                            }
                        }
                        items(
                            forYou.chunked(size = 2),
                            key = { row -> row.first().id },
                        ) { row ->
                            Reveal(index = 5) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    row.forEach { product ->
                                        ProductCard(
                                            product = product,
                                            onClick = { onOpenProduct(product) },
                                            onAddToCart = { CartStore.add(product.id) },
                                            modifier = Modifier.weight(1f),
                                            width = null,
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        // Sidebar overlay
        BuyerSidebarOverlay(
            open = sidebarOpen,
            onDismiss = { sidebarOpen = false },
            profile = profile,
            cartCount = cartCount,
            wishlistCount = 0,
            onNavigate = onSidebarNav,
        )

        // Floating bottom nav
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
                    onSignOutRequested()
                },
            )
        }
    }
}

private enum class FeedState { Loading, Ready, Error }

private fun greeting(): String = when (java.util.Calendar.getInstance()
    .get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Welcome back"
}

@Composable
private fun HeaderBadge(
    count: Int,
    description: String,
    icon: @Composable (tint: Color) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(ScottsTechXColors.SurfacePanelDark)
            .clickable(onClickLabel = description) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        icon(ScottsTechXColors.OnDark)
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .height(16.dp)
                    .width(if (count > 9) 26.dp else 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ScottsTechXColors.ErrorRed),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Hero — spotlights the best real flash deal in the feed. The artwork
 * is the actual product photo; the chip carries its real discount.
 * With no flash deal in the catalogue the hero degrades to a brand
 * strip offering the full catalogue — never a fake promotion.
 */
@Composable
private fun HeroSpotlight(
    deal: Product?,
    totalDeals: Int,
    onOpenDeal: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (deal != null) 190.dp else 120.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(ScottsTechXColors.BrandGradient)
            .clickable { if (deal != null) onOpenDeal() else onBrowse() },
    ) {
        if (deal != null) {
            AsyncImage(
                model = deal.imageUrl,
                contentDescription = deal.name,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(180.dp)
                    .height(190.dp),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x6605070D)),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (deal != null) "$totalDeals FLASH DEALS LIVE" else "LIVE MARKETPLACE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = deal?.name ?: "Real listings from\nUgandan sellers",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = deal?.seller?.name ?: "Kampala  ·  Entebbe  ·  Jinja",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
            if (deal != null && deal.discountPercent > 0) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "-${deal.discountPercent}% today",
                        color = ScottsTechXColors.BluePrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

/** Shimmer skeleton mirroring the real section layout. */
@Composable
private fun BuyerSkeleton(base: Color, highlight: Color) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            base = base,
            highlight = highlight,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                ShimmerBox(
                    modifier = Modifier
                        .size(56.dp),
                    shape = CircleShape,
                    base = base,
                    highlight = highlight,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        ShimmerBox(
            modifier = Modifier
                .width(150.dp)
                .height(18.dp),
            base = base,
            highlight = highlight,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(190.dp),
                    base = base,
                    highlight = highlight,
                )
            }
        }
    }
}
