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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.navigation.Routes
import com.scottsx.app.ui.components.OfflineBanner
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.SectionHeader
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.bottomInset
import com.scottsx.app.ui.components.navBarSpacer
import com.scottsx.app.ui.components.topInset
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Buyer home — hero carousel, categories, flash deals, recommended grid,
 * and the buyer bottom nav: Home / Nearby / [AI FAB] / Wishlist / Profile.
 */
@Composable
fun BuyerHomeScreen(
    onProductClick: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // True when the feed request FAILED. That is shown with a retry button —
    // a network failure must never masquerade as an empty marketplace, and
    // no local/demo catalog is substituted.
    var loadError by remember { mutableStateOf(false) }
    // Bumped by the Retry button to re-run the loader.
    var refresh by remember { mutableIntStateOf(0) }
    var cartCount by remember { mutableIntStateOf(0) }
    var unread by remember { mutableIntStateOf(0) }
    var feed by remember { mutableStateOf("for-you") }
    var category by remember { mutableStateOf(ProductCategory.All) }
    // Real wishlist state. The heart used to be a local `var wished` inside the
    // card, so it reset on every recomposition and never reached the backend —
    // tapping it looked like it worked and saved nothing.
    var savedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val currentUser by SessionCache.user.collectAsState()

    // Feed loader — same endpoints/params as the web buyer dashboard tabs.
    LaunchedEffect(feed, category, refresh) {
        loading = true
        loadError = false
        val cat = if (category == ProductCategory.All) null else category.displayName
        val live = when (feed) {
            "flash" -> V2Client.fetchProductsFeedOrNull(sort = "newest", flashOnly = true, category = cat)
            "trending" -> V2Client.fetchProductsFeedOrNull(sort = "popular", category = cat)
            "following" -> V2Client.fetchFavoritesFeedOrNull()
            else -> V2Client.fetchProductsFeedOrNull(sort = "newest", inStock = true, category = cat)
        }
        loadError = live == null
        products = live ?: products // keep the last good list visible while retrying
        loading = false
    }

    // Refresh the badge whenever this screen is shown again — the buyer may
    // have just added something, or emptied the cart by checking out.
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
            contentPadding = PaddingValues(
                // Edge-to-edge: the first row would otherwise sit under the clock.
                top = topInset(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            // Offline strip: draws nothing while connected, so it is free
            // space; when the connection drops it announces it here instead
            // of letting the feed pretend everything is fine.
            item { OfflineBanner() }
            item {
                // Top bar: menu / location chip / avatar
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
                        // Size first, then clip, then the ripple, then inset
                        // the glyph. The old order (.clip.clickable.padding
                        // .size) sized the GLYPH to 28dp and let the padding
                        // inflate the circle to 40dp, so the ripple disc was
                        // bigger than it looked and clipped at the wrong bounds.
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onNavigate(Routes.PROFILE) }
                            .padding(6.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = ScottsTechXColors.BluePrimary.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(16.dp))
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onNavigate(Routes.NOTIFICATIONS) }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
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
                    // Cart, with a live count so the buyer can see they have
                    // something waiting without opening the screen.
                    Box(
                        // Give the ripple a fixed round target instead of
                        // letting it take the icon's size plus padding.
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onNavigate(Routes.CART) }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
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
                        homeGreeting(),
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
                Spacer(Modifier.height(10.dp))
            }

            // Hero carousel — real flash deals from the live catalog, on the
            // brand blue gradients (web --gradient-brand). Hidden entirely
            // when there are no live flash deals: no fake promo filler.
            if (!loading && !loadError && flashDeals.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(flashDeals.take(3)) { deal ->
                            val gradients = listOf(
                                ScottsTechXColors.BrandGradientColors,
                                ScottsTechXColors.BlueHeroColors,
                                listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
                            )
                            val g = gradients[(deal.id.hashCode() and 0x7fffffff) % gradients.size]
                            Box(
                                modifier = Modifier
                                    .width(300.dp)
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.horizontalGradient(g))
                                    .clickable { onProductClick(deal.id) }
                                    .padding(16.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Text("⚡ Flash deal", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        deal.title,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        formatUgx(deal.priceMinor),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
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
                                compact = true,
                                wished = savedIds.contains(product.id),
                                onWishToggle = {
                                    scope.launch {
                                        val on = V2Client.toggleBookmark(product.id)
                                        savedIds = if (on) savedIds + product.id else savedIds - product.id
                                    }
                                },
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
            } else if (loadError) {
                item {
                    // Real error state: the feed request failed. A retry
                    // re-runs the same request; nothing is faked or cached.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("📡", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "We couldn't reach the marketplace",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Check your connection, then try again.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { refresh += 1 }) {
                            Text("Retry")
                        }
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
                                    wished = savedIds.contains(product.id),
                                    onWishToggle = {
                                        scope.launch {
                                            val on = V2Client.toggleBookmark(product.id)
                                            savedIds = if (on) savedIds + product.id else savedIds - product.id
                                        }
                                    },
                                )
                            }
                            // chunked(2) leaves the last row holding a single
                            // item when the count is odd; without a filler the
                            // lone card takes the full row width.
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Buyer bottom bar: Home / Nearby / [AI FAB] / Wishlist / Profile.
 * The AI button is the large centre FAB.
 */
@Composable
private fun ScaffoldWithBottomBar(
    selected: Int,
    onTab: (Int) -> Unit,
    onAiClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 76dp of bar + whatever the gesture pill / nav bar occupies, so the
        // last product row can always be scrolled fully into view.
        content(PaddingValues(bottom = 76.dp + bottomInset()))

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
                            Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)),
                            CircleShape,
                        )
                        .clickable(onClick = onAiClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Assistant", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The surface keeps painting to the screen edge; only the
                        // tappable row is lifted above the navigation bar.
                        .navBarSpacer()
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

private fun homeGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
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
