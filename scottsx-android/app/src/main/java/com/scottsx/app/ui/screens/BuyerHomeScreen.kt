package com.scottsx.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.R
import com.scottsx.app.data.CartStore
import com.scottsx.app.data.Session
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
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.components.navBarSpacer

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
    onNavigateToMyOrders: () -> Unit = {},
    onNavigateToSavedSellers: () -> Unit = {},
    onTrackOrder: (String) -> Unit = {},
    onNavigateToCategory: (ProductCategory) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNearby: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToAllProducts: () -> Unit,
    onNavigateToWishlist: () -> Unit = {},
    onNavigateToDeals: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToReceipts: () -> Unit = {},
    onNavigateToAiPersonalization: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToPayments: () -> Unit = {},
    onNavigateToAddresses: () -> Unit = {},
    onNavigateToRefunds: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
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

    // Caller-owned wishlist state for every product tile on this screen.
    val wishlistIds by com.scottsx.app.data.WishlistStore.ids.collectAsState()

    // ---- Live feed state ---------------------------------------------------
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var followingFeed by remember { mutableStateOf<List<Product>>(emptyList()) }
    var followedSellers by remember { mutableStateOf<List<com.scottsx.app.data.remote.V2Client.FavoriteSeller>>(emptyList()) }
    var feedState by remember { mutableStateOf(FeedState.Loading) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var unread by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        feedState = FeedState.Loading
        feedState = kotlinx.coroutines.coroutineScope {
            var feed: List<Product>? = null
            val feedJob = launch { feed = V2Client.fetchProductsListOrNull() }
            val badgeJob = launch { unread = V2Client.fetchUnreadNotificationCount() }
            // Signed-in extras: "from sellers you follow" tab + the
            // sellers-you-follow strip (same endpoints the web dashboard uses).
            var followFeed: List<Product>? = null
            var sellers: List<com.scottsx.app.data.remote.V2Client.FavoriteSeller>? = null
            val signedIn = com.scottsx.app.data.Session.tokenOrNull() != null
            val followJob = launch {
                if (signedIn) {
                    followFeed = V2Client.fetchFavoritesFeed()
                    sellers = V2Client.fetchFavoriteSellers()
                }
            }
            feedJob.join(); badgeJob.join(); followJob.join()
            followingFeed = followFeed ?: emptyList()
            followedSellers = sellers ?: emptyList()
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
            SidebarDestination.Deals -> onNavigateToDeals()
            SidebarDestination.Ai -> onNavigateToAi()
            SidebarDestination.Wishlist -> onNavigateToWishlist()
            SidebarDestination.Cart -> onNavigateToCart()
            SidebarDestination.Orders -> onNavigateToAllProducts()
            SidebarDestination.Payments -> onNavigateToPayments()
            SidebarDestination.Addresses -> onNavigateToAddresses()
            SidebarDestination.Refunds -> onNavigateToRefunds()
            SidebarDestination.Support -> onNavigateToSupport()
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
            SidebarDestination.Profile -> onNavigateToProfile()
        }
    }

    val skeletonBase = ScottsTechXColors.SurfaceElevatedDark
    val skeletonHighlight = Color(0xFF1B2743)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark)
            .statusBarSpacer()  // edge-to-edge: content clears the status bar,
    ) {
        // Ambient brand-vibe background: THREE slow-drifting glow orbs
        // (blue/cyan/violet — the company palette) floating behind the
        // feed. Pure ambience; every interactive layer sits above it.
        run {
            val orbDrift by rememberInfiniteTransition(label = "home-orbs").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(9000, easing = androidx.compose.animation.core.EaseInOutSine),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "home-orb-drift",
            )
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 110.dp, y = (-90 + orbDrift * 30).dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.10f)),
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-90 + orbDrift * 22).dp, y = 160.dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.CyanAccent.copy(alpha = 0.06f)),
            )
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (-120).dp, y = (120 - orbDrift * 26).dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.PurpleAccent.copy(alpha = 0.05f)),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // 1. Brand strip — company identity, always the first thing
            //    on screen: mark + wordmark + tagline.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The OFFICIAL company logo picture — pre-shaped
                    // rounded tile, no wrapper needed. First thing on screen.
                    Image(
                        painter = painterResource(R.drawable.brand_mark),
                        contentDescription = "ScottsTechX logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "ScottsTechX",
                        color = ScottsTechXColors.OnDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.2.sp,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "· Shop smart, sell fast",
                        color = ScottsTechXColors.OnDarkMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            // 2. Header — greeting + real badges
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
                        // 3. Hero — ROTATING flash-deal showcase: every
                        //    live deal takes the stage in turn, sliding in
                        //    with the app signature motion (auto-advance
                        //    every 5s + any manual tap to switch).
                        item {
                            Reveal(index = 1) {
                                HeroSpotlight(
                                    deals = flashDeals,
                                    onOpenDeal = onOpenProduct,
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
                                    items(flashDeals.size, key = { flashDeals[it].id }) { i ->
                                        val product = flashDeals[i]
                                        // Cards cascade in as the row scrolls;
                                        // the stagger index keeps motion
                                        // cheap for far-off items.
                                        Reveal(index = i.coerceAtMost(6)) {
                                            ProductCard(
                                                product = product,
                                                onClick = { onOpenProduct(product) },
                                                onAddToCart = { CartStore.add(product.id) },
                                                wished = product.id in wishlistIds,
                                                // Each tap persists through the
                                                // backend saved-products endpoints.
                                                onToggleWishlist = { com.scottsx.app.data.WishlistStore.toggleBookmark(product.id) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5b. From sellers you follow — web feed-tab parity
                        if (followingFeed.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(20.dp))
                                Reveal(index = 3) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.weight(1f)) {
                                            SectionTitle(
                                                title = "From sellers you follow",
                                                viewAll = null,
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
                                    items(followingFeed.take(8), key = { it.id }) { product ->
                                        ProductCard(
                                            product = product,
                                            onClick = { onOpenProduct(product) },
                                            onAddToCart = { CartStore.add(product.id) },
                                            wished = product.id in wishlistIds,
                                            onToggleWishlist = { com.scottsx.app.data.WishlistStore.toggleBookmark(product.id) },
                                        )
                                    }
                                }
                            }
                        }

                        // 5c. Sellers you follow — real avatars + ratings
                        if (followedSellers.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(20.dp))
                                Reveal(index = 3) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        SectionTitle(
                                            title = "Sellers you follow",
                                            viewAll = "Manage",
                                            onViewAll = { /* deep-link */ },
                                        )
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
                                    items(followedSellers, key = { it.id }) { seller ->
                                        Column(
                                            modifier = Modifier
                                                .width(90.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(ScottsTechXColors.SurfacePanelDark)
                                                .clickable { onOpenStore(seller.id) }
                                                .padding(vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            if (!seller.logoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = seller.logoUrl,
                                                    contentDescription = seller.storeName,
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(CircleShape)
                                                        .background(ScottsTechXColors.SurfaceElevatedDark),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .background(ScottsTechXColors.BluePrimary, CircleShape),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        (seller.storeName.firstOrNull() ?: 'S').uppercase(),
                                                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 18.sp,
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                seller.storeName,
                                                color = ScottsTechXColors.OnDark,
                                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "★ ${"%.1f".format(seller.rating)} · ${seller.productCount}",
                                                color = ScottsTechXColors.OnDarkSecondary, fontSize = 9.5.sp,
                                                maxLines = 1,
                                            )
                                        }
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
                                            wished = product.id in wishlistIds,
                                            onToggleWishlist = { com.scottsx.app.data.WishlistStore.toggleBookmark(product.id) },
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

        // Floating bottom nav
        Box(
            modifier = Modifier
                .navBarSpacer()  // lift the bottom bar clear of the gesture pill
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

        // Sidebar overlay — drawn AFTER the bottom nav so the drawer (and
        // its sticky Log-out button) sits on top of the floating bar;
        // before, the bar covered the logout row and swallowed its taps.
        BuyerSidebarOverlay(
            open = sidebarOpen,
            onDismiss = { sidebarOpen = false },
            profile = profile,
            cartCount = cartCount,
            wishlistCount = 0,
            onNavigate = onSidebarNav,
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
 * Hero — ROTATING flash-deal showcase. Every live deal takes the stage
 * in turn: the current slide slips out to the left while the next one
 * rises in from the right (the app's signature slide+fade), each with
 * its REAL photo, discount chip and per-slide pulse strip so the user
 * can see exactly which deal is on. Auto-advances every 5 s; tapping a
 * dot jumps straight to that deal. With zero live deals the hero
 * degrades to the static brand strip — never a fake promotion.
 */
@Composable
private fun HeroSpotlight(
    deals: List<Product>,
    onOpenDeal: (Product) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (deals.isEmpty()) {
        // Static brand fallback (no fake promotion, ever).
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(ScottsTechXColors.BrandGradient)
                .clickable { onBrowse() },
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LIVE MARKETPLACE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Real listings from\nUgandan sellers",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Kampala  ·  Entebbe  ·  Jinja",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            }
        }
        return
    }

    val stage = deals.take(8)
    var index by remember { mutableIntStateOf(0) }

    // Auto-rotation — recycles through every live deal.
    LaunchedEffect(stage.size) {
        while (stage.size > 1) {
            kotlinx.coroutines.delay(5000)
            index = (index + 1) % stage.size
        }
    }

    val active = stage[index.coerceIn(0, stage.lastIndex)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(ScottsTechXColors.BrandGradient)
            .clickable { onOpenDeal(active) },
    ) {
        // The stage: slide+fade between deals.
        androidx.compose.animation.AnimatedContent(
            targetState = active,
            transitionSpec = {
                (androidx.compose.animation.slideInHorizontally(
                    animationSpec = tween(520, easing = androidx.compose.animation.core.EaseOutCubic),
                ) { it } + androidx.compose.animation.fadeIn(tween(520))) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(
                        animationSpec = tween(420, easing = androidx.compose.animation.core.EaseInCubic),
                    ) { -it / 2 } + androidx.compose.animation.fadeOut(tween(420)))
            },
            label = "hero-deal-rotation",
            modifier = Modifier.matchParentSize(),
        ) { deal ->
            Box(modifier = Modifier.fillMaxSize()) {
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
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingDot(color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${stage.size} FLASH DEALS LIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = deal.name,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 24.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = deal.seller?.name ?: "",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (deal.discountPercent > 0) {
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

        // Progress dots — tap to jump to that deal.
        if (stage.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stage.forEachIndexed { i, d ->
                    val selected = i == index
                    val w by animateFloatAsState(
                        targetValue = if (selected) 18f else 6f,
                        animationSpec = tween(300),
                        label = "hero-dot-w",
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(w.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) Color.White
                                else Color.White.copy(alpha = 0.45f),
                            )
                            .clickable { index = i },
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
