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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.navigation.Routes
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.SectionHeader
import com.scottsx.app.ui.theme.ScottsTechXColors
import java.util.Calendar

/**
 * Buyer home — the Android mirror of the web buyer dashboard.
 *
 * Greeting hero, feed tabs (For you / Flash / Trending / Following — the
 * exact feeds the website serves), working category filters and the
 * redesigned product grid. Bottom nav unchanged:
 * Home / Nearby / [AI FAB] / Wishlist / Profile.
 */
@Composable
fun BuyerHomeScreen(
    onProductClick: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var cartCount by remember { mutableIntStateOf(0) }
    var unread by remember { mutableIntStateOf(0) }
    var feed by remember { mutableStateOf("for-you") }
    var category by remember { mutableStateOf(ProductCategory.All) }
    var savedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val currentUser by SessionCache.user.collectAsState()

    // Feed loader — same endpoints/params as the web buyer dashboard tabs.
    LaunchedEffect(feed, category) {
        loading = true
        val cat = if (category == ProductCategory.All) null else category.displayName
        val live = when (feed) {
            "flash" -> V2Client.fetchProductsFeed(sort = "newest", flashOnly = true, category = cat)
            "trending" -> V2Client.fetchProductsFeed(sort = "popular", category = cat)
            "following" -> V2Client.fetchFavoritesFeed()
            else -> V2Client.fetchProductsFeed(sort = "newest", inStock = true, category = cat)
        }
        products = if (live.isNotEmpty() || feed == "following") live else MarketplaceDataSource.products
        loading = false
    }

    // Cart badge + unread notifications + wishlist state.
    LaunchedEffect(currentUser?.id) {
        if (currentUser != null) {
            V2Client.fetchCart().onSuccess { cartCount = it.itemCount }
            unread = V2Client.fetchUnreadNotificationCount()
            savedIds = V2Client.fetchBookmarks().map { it.id }.toSet()
        } else {
            cartCount = 0
            unread = 0
            savedIds = emptySet()
        }
    }

    val flashDeals = products.filter { it.isFlashDeal }

    ScaffoldWithBottomBar(
        selected = 0,
        onTab = { index ->
            when (index) {
                0 -> Unit
                1 -> onNavigate(Routes.NEARBY)
                3 -> onNavigate(Routes.SAVED_PRODUCTS)
                4 -> onNavigate(Routes.PROFILE)
            }
        },
        onAiClick = { onNavigate(Routes.AI) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                // Top bar: menu / location chip / bell / cart / avatar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onNavigate(Routes.PROFILE) }
                            .padding(6.dp)
                            .size(28.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = ScottsTechXColors.BluePrimary.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigate(Routes.NEARBY) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = ScottsTechXColors.BluePrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                currentUser?.city?.ifBlank { "Kampala" } ?: "Kampala",
                                color = ScottsTechXColors.BluePrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))

                    // Notifications bell with unread badge.
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onNavigate(Routes.NOTIFICATIONS) }
                            .padding(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = if (unread > 0) "Notifications, $unread unread" else "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(25.dp),
                        )
                        if (unread > 0) {
                            Surface(
                                color = ScottsTechXColors.ErrorRed,
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Text(
                                    if (unread > 9) "9+" else unread.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))

                    // Cart with live count.
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onNavigate(Routes.CART) }
                            .padding(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.ShoppingCart,
                            contentDescription = if (cartCount > 0) "Cart, $cartCount items" else "Cart",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp),
                        )
                        if (cartCount > 0) {
                            Surface(
                                color = ScottsTechXColors.ErrorRed,
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Text(
                                    if (cartCount > 9) "9+" else cartCount.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onNavigate(Routes.PROFILE) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                (currentUser?.displayName ?: "S").firstOrNull()?.uppercase() ?: "S",
                                fontWeight = FontWeight.Bold,
                                color = ScottsTechXColors.BluePrimary,
                            )
                        }
                    }
                }
            }

            item {
                // Greeting — mirrors the web hero ("Good morning, Hi X 👋").
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        greeting(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ScottsTechXColors.BluePrimary,
                    )
                    Text(
                        "Hi ${(currentUser?.displayName ?: "there").split(" ").first()} 👋",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            item {
                // Hero carousel — brand blue gradients (web --gradient-brand).
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(MarketplaceDataSource.heroBanners) { banner ->
                        val gradients = listOf(
                            ScottsTechXColors.BrandGradientColors,
                            ScottsTechXColors.BlueHeroColors,
                            listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
                        )
                        val g = gradients[(banner.hashCode() and 0x7fffffff) % gradients.size]
                        Box(
                            modifier = Modifier
                                .width(300.dp)
                                .height(120.dp)
                                .background(Brush.horizontalGradient(g), RoundedCornerShape(18.dp))
                                .padding(16.dp),
                        ) {
                            Column {
                                Text(banner.emoji, fontSize = 26.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(banner.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(banner.subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                // Feed tabs — the same four feeds as the web buyer dashboard.
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedTab("For you", feed == "for-you") { feed = "for-you" }
                    FeedTab("⚡ Flash", feed == "flash") { feed = "flash" }
                    FeedTab("Trending", feed == "trending") { feed = "trending" }
                    if (SessionCache.isLoggedIn()) {
                        FeedTab("Following", feed == "following") { feed = "following" }
                    }
                }
            }

            item {
                // Category row — taps filter the live feed.
                SectionHeader("Categories")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ProductCategory.values().toList()) { cat ->
                        val active = category == cat
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) ScottsTechXColors.BluePrimary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { category = if (active) ProductCategory.All else cat }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(cat.emoji, fontSize = 22.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                cat.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) ScottsTechXColors.BluePrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (feed != "flash" && flashDeals.isNotEmpty()) {
                item {
                    SectionHeader("Flash deals", action = "View all") { feed = "flash" }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(flashDeals) { product ->
                            ProductCard(
                                product = product,
                                onClick = { onProductClick(product.id) },
                                modifier = Modifier.width(160.dp),
                                initiallySaved = product.id in savedIds,
                                compact = true,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    when (feed) {
                        "flash" -> "Flash deals"
                        "trending" -> "Trending now"
                        "following" -> "New from sellers you follow"
                        else -> "Recommended for you"
                    },
                )
            }

            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                }
            } else if (products.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (feed == "following") "💙" else "🔍", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (feed == "following") "Follow sellers to fill this feed"
                            else "Nothing here yet",
                            style = MaterialTheme.typography.titleMedium,
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
                                    withCartButton = true,
                                    onAddedToCart = { cartCount += 1 },
                                    initiallySaved = product.id in savedIds,
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun FeedTab(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(99.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
        )
    }
}

/**
 * Buyer bottom bar: Home / Nearby / [AI FAB] / Wishlist / Profile.
 * The AI button is the large centre FAB (brand-blue gradient).
 */
@Composable
private fun ScaffoldWithBottomBar(
    selected: Int,
    onTab: (Int) -> Unit,
    onAiClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content(PaddingValues(bottom = 76.dp))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            Brush.horizontalGradient(ScottsTechXColors.BrandGradientColors),
                            CircleShape,
                        )
                        .clickable(onClick = onAiClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "AI Assistant",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BuyerNavItem(Icons.Filled.Home, "Home", selected == 0, Modifier.weight(1f)) { onTab(0) }
                    BuyerNavItem(Icons.Filled.NearMe, "Nearby", selected == 1, Modifier.weight(1f)) { onTab(1) }
                    Spacer(Modifier.width(56.dp))
                    BuyerNavItem(Icons.Filled.Favorite, "Wishlist", selected == 3, Modifier.weight(1f)) { onTab(3) }
                    BuyerNavItem(Icons.Filled.Person, "Profile", selected == 4, Modifier.weight(1f)) { onTab(4) }
                }
            }
        }
    }
}

@Composable
private fun BuyerNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
