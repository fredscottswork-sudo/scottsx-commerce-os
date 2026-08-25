package com.scottsx.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.domain.BuyerProfile
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.SessionCache
import com.scottsx.app.data.preferences.ThemeMode
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.preferences.isSeller
import com.scottsx.app.data.preferences.sidebarPaletteFor
import com.scottsx.app.ui.theme.ScottsTechXColors
import coil.compose.AsyncImage

/** Destination of a sidebar nav item — opaque to the drawer. */
enum class SidebarDestination {
    Home, Nearby, Ai, Wishlist, Cart, Orders, Messages, Notifications,
    SellerCenter, BecomeSeller, Settings, Theme, Logout, Profile,
    Transactions, Receipts, AiPersonalization,
}

/**
 * Premium left-side navigation drawer for the Buyer Dashboard.
 *
 *  - Slides in from the left over a dimmed + subtly blurred backdrop.
 *  - Width: ~80% of the screen on phones (max 340dp).
 *  - Hamburger ↔ X icon swap; tap-outside + Android Back close.
 *  - Gesture: drawer-swipe-left closes; edge-swipe-right opens (handled by
 *    [BuyerSidebarOverlay] since the host needs the edge-swipe zone).
 *
 * Sections — exact order from the brief:
 *  PROFILE  → EXPLORE → SELLER → PREFERENCES → ACCOUNT.
 *
 * Badges are dynamic and pulled from [MarketplaceDataSource] /
 * [com.scottsx.app.data.CartStore].
 *
 * Theme switching is delegated to the caller via [onShowThemePicker].
 * The drawer itself never hosts a [ModalBottomSheet] — keeping the
 * composable stateless and easy to lift into other screens.
 */
@Composable
fun BuyerSidebarOverlay(
    open: Boolean,
    onDismiss: () -> Unit,
    profile: BuyerProfile,
    cartCount: Int,
    wishlistCount: Int,
    messagesCount: Int = MarketplaceDataSource.unreadMessagesCount(),
    notificationsCount: Int = MarketplaceDataSource.unreadNotificationsCount(),
    ordersCount: Int = MarketplaceDataSource.pendingOrdersCount(),
    onNavigate: (SidebarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val palette = sidebarPaletteFor(ThemeMode.SYSTEM) // sidebar theme uses drawer itself

    AnimatedVisibility(
        visible = open,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // Dimmed + blurred backdrop. Blur is API-31+; on older
            // devices we fall back to a flat dark overlay.
            val dimAlpha by animateFloatAsState(
                targetValue = if (open) 1f else 0f,
                animationSpec = tween(220),
                label = "dim-alpha",
            )
            val backdropColor = Color(0xFF050711).copy(alpha = 0.55f * dimAlpha)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backdropColor)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            )

            // The actual drawer container.
            val drawerWidthFraction = 0.80f
            val drawerMaxWidth = 340.dp
            // Compute a stable width: 80% of the screen, capped at 340dp.
            val density = androidx.compose.ui.platform.LocalDensity.current
            val screenWidthPx = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val targetWidth = (screenWidthPx * drawerWidthFraction).coerceAtMost(drawerMaxWidth)

            // Slide-in offset: hidden = -targetWidth (off-screen left), visible = 0
            val offsetAnim by animateFloatAsState(
                targetValue = if (open) 0f else -1f,
                animationSpec = tween(
                    durationMillis = 280,
                    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
                ),
                label = "drawer-offset",
            )
            val translationX = targetWidth.value * offsetAnim

            BuyerSidebarCard(
                modifier = Modifier
                    .offset(x = translationX.dp)
                    .width(targetWidth)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 12.dp, start = 0.dp, end = 8.dp)
                    .pointerInput(Unit) {
                        // Edge-swipe-left inside the drawer to close.
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -10f) onDismiss()
                        }
                    }
                    .shadow(
                        elevation = 22.dp,
                        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        clip = false,
                    )
                    .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                    .background(brush = Brush.linearGradient(
                        colors = listOf(Color.White, Color(0xFFFAFBFE)),
                    )),
                profile = profile,
                cartCount = cartCount,
                wishlistCount = wishlistCount,
                messagesCount = messagesCount,
                notificationsCount = notificationsCount,
                ordersCount = ordersCount,
                onNavigate = { dest ->
                    onNavigate(dest)
                    if (dest != SidebarDestination.Theme) onDismiss()
                },
                onDismiss = onDismiss,
            )

            // Android Back closes the drawer first.
            BackHandler(enabled = open) { onDismiss() }
        }
    }
}

/**
 * The drawer card itself, exposed so the host can place it inside an
 * [AnimatedVisibility] if it wants different enter/exit choreography
 * (e.g. the Buyer Dashboard uses this directly without the overlay).
 */
@Composable
fun BuyerSidebarCard(
    modifier: Modifier = Modifier,
    profile: BuyerProfile,
    cartCount: Int,
    wishlistCount: Int,
    messagesCount: Int,
    notificationsCount: Int,
    ordersCount: Int,
    onNavigate: (SidebarDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header: hamburger (X when open) + brand + close
        SidebarHeader(
            displayName = profile.displayName.ifBlank { "Guest" },
            email = profile.email,
            avatarUrl = profile.avatarUrl,
            onClose = onDismiss,
        )

        // Scrollable body so small screens don't cut off options.
        // Logout is now drawn in a sticky bottom bar OUTSIDE the LazyColumn
        // so it's always visible at the bottom of the drawer.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
        ) {
            item {
                ProfileSection(
                    profile = profile,
                    onViewProfile = { onNavigate(SidebarDestination.Profile) },
                )
                Spacer(Modifier.height(12.dp))
            }
            item { SectionLabel("Explore") }
            itemsIndexed(
                items = listOf(
                    SidebarItem(SidebarDestination.Home, "Home", Icons.Filled.Home, null),
                    SidebarItem(SidebarDestination.Nearby, "Nearby", Icons.Filled.LocationOn, null, featured = FeaturedKind.Nearby),
                    SidebarItem(SidebarDestination.Ai, "AI Assistant", Icons.Filled.AutoAwesome, null, featured = FeaturedKind.Ai),
                    SidebarItem(SidebarDestination.Wishlist, "Wishlist", Icons.Filled.Favorite, wishlistCount),
                    SidebarItem(SidebarDestination.Cart, "Cart", Icons.Filled.ShoppingCart, cartCount),
                    SidebarItem(SidebarDestination.Orders, "My Orders", Icons.Filled.LocalShipping, ordersCount),
                    SidebarItem(SidebarDestination.Transactions, "My Transactions", Icons.Filled.SwapHoriz, null),
                    SidebarItem(SidebarDestination.Receipts, "My Receipts", Icons.Filled.ReceiptLong, null),
                    SidebarItem(SidebarDestination.AiPersonalization, "AI Personalization", Icons.Filled.SmartToy, null),
                    SidebarItem(SidebarDestination.Messages, "Messages", Icons.Filled.ChatBubble, messagesCount),
                    SidebarItem(SidebarDestination.Notifications, "Notifications", Icons.Filled.Notifications, notificationsCount),
                ),
            ) { index, item ->
                SidebarRow(item = item, onClick = { onNavigate(item.destination) }, index = index)
            }
            item {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Seller")
            }
            val sellerItem = if (SessionCache.isSeller()) {
                SidebarItem(SidebarDestination.SellerCenter, "Seller Center", Icons.Filled.Storefront, null)
            } else {
                SidebarItem(SidebarDestination.BecomeSeller, "Become a Seller", Icons.Filled.Storefront, null)
            }
            item { SidebarRow(sellerItem, onClick = { onNavigate(sellerItem.destination) }, index = 0) }
            item {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Preferences")
            }
            val prefItems = listOf(
                SidebarItem(SidebarDestination.Settings, "Settings", Icons.Filled.Settings, null),
                SidebarItem(SidebarDestination.Theme, "Theme", Icons.Filled.Brightness4, null),
            )
            itemsIndexed(prefItems) { index, item ->
                SidebarRow(item, onClick = { onNavigate(item.destination) }, index = index)
            }
        }

        // Sticky bottom bar — logout is always visible at the drawer bottom.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogOutRow(onClick = { onNavigate(SidebarDestination.Logout) })
        }
    }
}

@Composable
private fun SidebarHeader(
    displayName: String,
    email: String,
    avatarUrl: String? = null,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ScottsTechXColors.BluePrimaryDark,
                        ScottsTechXColors.BluePrimary,
                    ),
                ),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hamburger / X swap
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close menu",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "ScottsTechX",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                coil.compose.AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                )
            } else {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "U",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(
    profile: BuyerProfile,
    onViewProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewProfile)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar — uses procedural gradient + initials when no photo
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ScottsTechXColors.BluePrimary,
                            ScottsTechXColors.BluePrimaryLight,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (profile.displayName.firstOrNull()?.uppercase() ?: "U"),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName.ifBlank { "Buyer" },
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = profile.email.ifBlank { "buyer@scottsx.app" },
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "View Profile",
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
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = ScottsTechXColors.OnLightSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 8.dp),
    )
}

/** Single nav-row item data holder. */
private data class SidebarItem(
    val destination: SidebarDestination,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badge: Int?,
    val featured: FeaturedKind? = null,
)

private enum class FeaturedKind { Nearby, Ai }

@Composable
private fun SidebarRow(
    item: SidebarItem,
    onClick: () -> Unit,
    index: Int = 0,
) {
    // Subtle stagger: items animate in 22ms after the previous one.
    var appeared by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 22L).coerceAtMost(180L))
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(220),
        label = "row-alpha",
    )
    val slide by animateFloatAsState(
        targetValue = if (appeared) 0f else 12f,
        animationSpec = tween(260),
        label = "row-slide",
    )
    val featuredAccent = when (item.featured) {
        FeaturedKind.Nearby -> NearbyFeatureAccent
        FeaturedKind.Ai -> AiFeatureAccent
        else -> null
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .graphicsLayer { this.alpha = alpha; translationY = slide },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (featuredAccent != null) featuredAccent.copy(alpha = 0.10f)
                    else Color.Transparent,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Featured items get a tinted halo behind the icon.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (featuredAccent != null) featuredAccent.copy(alpha = 0.25f)
                        else ScottsTechXColors.PanelInputLight,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = featuredAccent ?: ScottsTechXColors.OnLight,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.label,
                color = ScottsTechXColors.OnLight,
                fontWeight = if (item.featured != null) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            // Dynamic badge — hidden when 0 / null
            if ((item.badge ?: 0) > 0) {
                BadgePill(count = item.badge!!, featured = item.featured)
            } else if (item.featured != null) {
                FeaturedDot(featured = item.featured)
            }
        }
    }
}

@Composable
private fun BadgePill(count: Int, featured: FeaturedKind?) {
    val tint = when (featured) {
        FeaturedKind.Nearby -> NearbyFeatureAccent
        FeaturedKind.Ai -> AiFeatureAccent
        else -> ScottsTechXColors.BluePrimary
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun FeaturedDot(featured: FeaturedKind) {
    val tint = when (featured) {
        FeaturedKind.Nearby -> NearbyFeatureAccent
        FeaturedKind.Ai -> AiFeatureAccent
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(tint),
    )
}

@Composable
private fun LogOutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFEE2E2))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFFECACA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Logout,
                contentDescription = null,
                tint = Color(0xFFB91C1C),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Log Out",
            color = Color(0xFFB91C1C),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFB91C1C),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Halo colors for the "priority features" section (Nearby + AI). */
private val NearbyFeatureAccent = Color(0xFF059669)        // emerald
private val AiFeatureAccent = Color(0xFF6366F1)             // indigo/blue-purple

/** Re-export the hamburger icon so callers don't need an extra import. */
val HamburgerIcon = Icons.Filled.Menu