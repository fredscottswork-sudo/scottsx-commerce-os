package com.scottsx.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.CartStore
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.domain.BuyerProfile
import com.scottsx.app.data.preferences.ThemeMode
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.preferences.themeState
import com.scottsx.app.data.preferences.sidebarPaletteFor
import com.scottsx.app.ui.components.BuyerSidebarOverlay
import com.scottsx.app.ui.components.HamburgerIcon as Hamburger
import com.scottsx.app.ui.components.SidebarDestination
import com.scottsx.app.ui.components.ThemeSelectorSheet
import com.scottsx.app.ui.components.LogoutConfirmDialog
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.components.BenefitsStrip
import com.scottsx.app.ui.components.BuyerHeader
import com.scottsx.app.ui.components.CountdownTimer
import com.scottsx.app.ui.components.HeroCarousel
import com.scottsx.app.ui.components.CategoryRow
import com.scottsx.app.ui.components.NearbyAiCard
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.components.SectionTitle
import com.scottsx.app.ui.theme.ScottsTechXColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.Menu

/**
 * Stage-2 Buyer Home Dashboard + Stage 3.1 sidebar overlay.
 *
 * Vertical hierarchy (per brief):
 *   1. Header (with hamburger button overlaid for sidebar)
 *   2. Search + filter
 *   3. Hero carousel
 *   4. Category row
 *   5. Marketplace benefits
 *   6. Nearby + AI Assistant
 *   7. Flash Deals (countdown + horizontal scroll)
 *   8. Recommended for you
 *   9. Floating transparent blue animated bottom nav
 *  10. BuyerSidebar overlay (3.1)
 *  11. Theme selector ModalBottomSheet (3.1)
 *  12. Logout confirmation dialog (3.1)
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
    // Fetch live products from the backend (so new seller uploads
    // appear in the buyer feed). Falls back to the static
    // MarketplaceDataSource if the network call fails or returns empty.
    val scope = rememberCoroutineScope()
    var apiProducts by remember { mutableStateOf<List<com.scottsx.app.data.domain.Product>>(emptyList()) }
    var apiLoaded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch {
            try {
                val arr = com.scottsx.app.data.remote.V2Client.fetchProductsList()
                apiProducts = arr
                apiLoaded = true
            } catch (e: Exception) { apiLoaded = true }
        }
    }
    val flashDeals = if (apiProducts.isNotEmpty()) apiProducts.take(6) else MarketplaceDataSource.flashDeals
    val recommended = if (apiProducts.isNotEmpty()) apiProducts.drop(6).take(10) else MarketplaceDataSource.recommended
    val cartCount = cartItems.sumOf { it.quantity }

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
                    BuyerHeader(
                        displayName = profile.displayName,
                        email = profile.email,
                        notificationCount = profile.notificationCount,
                        cartCount = cartCount,
                        onNotificationsClick = { /* Stage 2 — notifications */ },
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

            // 3. Hero carousel
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HeroCarousel(
                        banners = MarketplaceDataSource.heroBanners,
                        onCtaClick = { onNavigateToAllProducts() },
                    )
                }
            }

            // 4. Category row
            item {
                Spacer(Modifier.height(12.dp))
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

            // 5. Marketplace benefits
            item {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    BenefitsStrip(benefits = MarketplaceDataSource.benefits)
                }
            }

            // 6. Nearby + AI Assistant
            item {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    NearbyAiCard(
                        onNearbyClick = onNavigateToNearby,
                        onAiClick = onNavigateToAi,
                    )
                }
            }

            // 7. Flash Deals
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionTitle(
                        title = "Flash Deals",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FilterList,  // placeholder, swapped below
                                contentDescription = null,
                                tint = ScottsTechXColors.BluePrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        viewAll = "View All >",
                        onViewAll = onNavigateToAllProducts,
                    )
                    CountdownTimer(initialSeconds = 2 * 3600 + 45 * 60 + 30)
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

            // 8. Recommended for you
            item {
                Spacer(Modifier.height(22.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle(
                        title = "Recommended for you",
                        viewAll = "View All >",
                        onViewAll = onNavigateToAllProducts,
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
                    items(recommended, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            onClick = { /* Stage 2 — product details */ },
                            onAddToCart = { CartStore.add(product.id) },
                        )
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
