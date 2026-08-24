package com.scottsx.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.SellerDashboardSnapshot
import com.scottsx.app.data.preferences.LocalThemePreference
import com.scottsx.app.data.preferences.ThemeMode
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.preferences.themeState
import com.scottsx.app.ui.theme.ScottsTechXColors
import coil.compose.AsyncImage
import com.scottsx.app.data.domain.SessionCache

/** What a seller sidebar tap wants to do. */
enum class SellerSidebarDestination {
    Dashboard, Orders, Products, Customers, Messages, Promotions, Analytics,
    SellerAi, MarketingTools,
    StoreProfile, StoreSettings, Settings,
    SwitchToBuyer, Logout, ViewStore,
    Theme, ToggleOnline,
    Transactions, Receipts, CreateReceipt, AiPersonalization,
}

private enum class SellerFeaturedKind { SellerAi, Analytics }

/**
 * Premium left-side drawer for the seller dashboard.
 *
 * Mirrors the buyer [BuyerSidebarOverlay] architecture but with the
 * seller menu structure from the brief:
 *   HEADER  →  BUSINESS  →  TOOLS  →  STORE  →  ACCOUNT  →  THEME.
 *
 * Badges are dynamic (orders, products, messages). Seller AI +
 * Analytics render with the brand-blue glow. Theme switcher lives
 * at the bottom and writes to the same [ThemePreference] singleton
 * the buyer side uses, so a theme choice persists across the app.
 */
@Composable
fun SellerSidebarOverlay(
    open: Boolean,
    onDismiss: () -> Unit,
    snapshot: SellerDashboardSnapshot,
    onNavigate: (SellerSidebarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themePref = LocalThemePreference.current
    val themeMode by themePref.themeState()

    AnimatedVisibility(
        visible = open,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // Dim backdrop.
            val dimAlpha by animateFloatAsState(
                targetValue = if (open) 1f else 0f,
                animationSpec = tween(220),
                label = "seller-dim",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050711).copy(alpha = 0.55f * dimAlpha))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            )

            // Drawer width: 80% of screen, capped at 340dp.
            val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val targetWidth = (screenWidthDp * 0.80f).coerceAtMost(340.dp)
            val offsetAnim by animateFloatAsState(
                targetValue = if (open) 0f else -1f,
                animationSpec = tween(
                    durationMillis = 280,
                    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
                ),
                label = "seller-drawer-offset",
            )
            val translationX = targetWidth.value * offsetAnim

            SellerSidebarCard(
                modifier = Modifier
                    .offset(x = translationX.dp)
                    .width(targetWidth)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 12.dp, start = 0.dp, end = 8.dp)
                    .pointerInput(Unit) {
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
                    .background(Brush.linearGradient(colors = listOf(Color.White, Color(0xFFFAFBFE)))),
                snapshot = snapshot,
                themeMode = themeMode,
                themePref = themePref,
                onNavigate = { dest ->
                    onNavigate(dest)
                    if (dest != SellerSidebarDestination.Theme) onDismiss()
                },
                onDismiss = onDismiss,
            )

            BackHandler(enabled = open) { onDismiss() }
        }
    }
}

@Composable
fun SellerSidebarCard(
    modifier: Modifier = Modifier,
    snapshot: SellerDashboardSnapshot,
    themeMode: ThemeMode,
    themePref: ThemePreference,
    onNavigate: (SellerSidebarDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SellerSidebarHeader(
            storeName = snapshot.storeName,
            displayName = snapshot.displayName,
            storeId = snapshot.storeId,
            avatarUrl = SessionCache.avatarUrl,
            status = snapshot.status,
            onClose = onDismiss,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
        ) {
            item {
                SellerProfileHeader(
                    snapshot = snapshot,
                    onViewStore = { onNavigate(SellerSidebarDestination.ViewStore) },
                )
                Spacer(Modifier.height(12.dp))
            }
            item { SectionLabel("Business") }
            itemsIndexed(
                items = listOf(
                    SellerSidebarItem(SellerSidebarDestination.Dashboard, "Dashboard", Icons.Filled.Dashboard, null),
                    SellerSidebarItem(SellerSidebarDestination.Orders, "Orders", Icons.Filled.Inventory2, snapshot.ordersOverview.pending),
                    SellerSidebarItem(SellerSidebarDestination.CreateReceipt, "Create Receipt", Icons.Filled.ReceiptLong, null),
                    SellerSidebarItem(SellerSidebarDestination.Transactions, "Transactions", Icons.Filled.SwapHoriz, null),
                    SellerSidebarItem(SellerSidebarDestination.Receipts, "Receipts", Icons.Filled.Receipt, null),
                    SellerSidebarItem(SellerSidebarDestination.AiPersonalization, "AI Personalization", Icons.Filled.SmartToy, null),
                    SellerSidebarItem(SellerSidebarDestination.Products, "Products", Icons.Filled.Store, 124),
                    SellerSidebarItem(SellerSidebarDestination.Customers, "Customers", Icons.Filled.Group, snapshot.customersDelta),
                    SellerSidebarItem(SellerSidebarDestination.Messages, "Messages", Icons.Filled.Message, 5),
                    SellerSidebarItem(SellerSidebarDestination.Promotions, "Promotions", Icons.Filled.LocalOffer, 2),
                    SellerSidebarItem(
                        SellerSidebarDestination.Analytics,
                        "Analytics",
                        Icons.Filled.Analytics,
                        null,
                        featured = SellerFeaturedKind.Analytics,
                    ),
                ),
            ) { index, item ->
                SellerSidebarRow(item, onClick = { onNavigate(item.destination) }, index = index)
            }

            item { Spacer(Modifier.height(14.dp)); SectionLabel("Tools") }
            itemsIndexed(
                items = listOf(
                    SellerSidebarItem(
                        SellerSidebarDestination.SellerAi,
                        "Seller AI Assistant",
                        Icons.Filled.AutoAwesome,
                        null,
                        featured = SellerFeaturedKind.SellerAi,
                    ),
                    SellerSidebarItem(
                        SellerSidebarDestination.MarketingTools,
                        "Marketing Tools",
                        Icons.Filled.Campaign,
                        null,
                    ),
                ),
            ) { index, item ->
                SellerSidebarRow(item, onClick = { onNavigate(item.destination) }, index = index)
            }

            item { Spacer(Modifier.height(14.dp)); SectionLabel("Store") }
            itemsIndexed(
                items = listOf(
                    SellerSidebarItem(SellerSidebarDestination.StoreProfile, "Store Profile", Icons.Filled.Person, null),
                    SellerSidebarItem(SellerSidebarDestination.StoreSettings, "Store Settings", Icons.Filled.Settings, null),
                    SellerSidebarItem(SellerSidebarDestination.Settings, "App Settings", Icons.Filled.Tune, null),
                ),
            ) { index, item ->
                SellerSidebarRow(item, onClick = { onNavigate(item.destination) }, index = index)
            }

            item {
                Spacer(Modifier.height(14.dp))
                SellerThemeRow(
                    current = themeMode,
                    onPick = { mode -> themePref.set(mode) },
                    onClick = { onNavigate(SellerSidebarDestination.Theme) },
                )
            }

            item { Spacer(Modifier.height(16.dp)); SectionLabel("Account") }
            itemsIndexed(
                items = listOf(
                    SellerSidebarItem(
                        SellerSidebarDestination.SwitchToBuyer,
                        "Switch to Buyer App",
                        Icons.Filled.SwapHoriz,
                        null,
                    ),
                ),
            ) { index, item ->
                SellerSidebarRow(item, onClick = { onNavigate(item.destination) }, index = index)
            }

        }
        // Sticky bottom bar — logout is always visible at the drawer bottom.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SellerLogOutRow(onClick = { onNavigate(SellerSidebarDestination.Logout) })
        }
    }
}

@Composable
private fun SellerSidebarHeader(
    storeName: String,
    displayName: String,
    storeId: String,
    avatarUrl: String? = null,
    status: com.scottsx.app.data.domain.StoreStatus,
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
            text = "Seller Hub",
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
                    text = storeName.firstOrNull()?.uppercase() ?: "S",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun SellerProfileHeader(
    snapshot: SellerDashboardSnapshot,
    onViewStore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
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
                Icon(
                    imageVector = Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = snapshot.storeName,
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified",
                        tint = ScottsTechXColors.BluePrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Store ID: ${snapshot.storeId}",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                StoreStatusPill(status = snapshot.status)
            }
        }
        Spacer(Modifier.height(10.dp))
        // Tappable "View Store" pill.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.10f))
                .clickable(onClick = onViewStore)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Store,
                    contentDescription = null,
                    tint = ScottsTechXColors.BluePrimary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "View Store",
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
private fun StoreStatusPill(status: com.scottsx.app.data.domain.StoreStatus) {
    val bg = when (status) {
        com.scottsx.app.data.domain.StoreStatus.Online -> Color(0xFFDCFCE7)
        com.scottsx.app.data.domain.StoreStatus.Away -> Color(0xFFFEF3C7)
    }
    val fg = when (status) {
        com.scottsx.app.data.domain.StoreStatus.Online -> Color(0xFF15803D)
        com.scottsx.app.data.domain.StoreStatus.Away -> Color(0xFFB45309)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = status.label,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
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

private data class SellerSidebarItem(
    val destination: SellerSidebarDestination,
    val label: String,
    val icon: ImageVector,
    val badge: Int?,
    val featured: SellerFeaturedKind? = null,
)

@Composable
private fun SellerSidebarRow(
    item: SellerSidebarItem,
    onClick: () -> Unit,
    index: Int = 0,
) {
    var appeared by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 22L).coerceAtMost(180L))
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(220),
        label = "seller-row-alpha",
    )
    val slide by animateFloatAsState(
        targetValue = if (appeared) 0f else 12f,
        animationSpec = tween(260),
        label = "seller-row-slide",
    )
    val accent = when (item.featured) {
        SellerFeaturedKind.SellerAi -> Color(0xFF6366F1)
        SellerFeaturedKind.Analytics -> Color(0xFF059669)
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
                .background(accent?.copy(alpha = 0.10f) ?: Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent?.copy(alpha = 0.25f) ?: ScottsTechXColors.PanelInputLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = accent ?: ScottsTechXColors.OnLight,
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
            if ((item.badge ?: 0) > 0) {
                SellerBadgePill(count = item.badge!!, featured = item.featured)
            } else if (item.featured != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent!!),
                )
            }
        }
    }
}

@Composable
private fun SellerBadgePill(count: Int, featured: SellerFeaturedKind?) {
    val tint = when (featured) {
        SellerFeaturedKind.SellerAi -> Color(0xFF6366F1)
        SellerFeaturedKind.Analytics -> Color(0xFF059669)
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
private fun SellerThemeRow(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.PanelInputLight)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = ScottsTechXColors.BluePrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Theme",
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                text = current.name,
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 11.sp,
            )
        }
        // Tiny three-pill toggle for quick access.
        Row {
            ThemeMode.values().forEach { mode ->
                val selected = mode == current
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) ScottsTechXColors.BluePrimary
                            else Color.Transparent,
                        )
                        .clickable { onPick(mode) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (selected) Color.White else ScottsTechXColors.OnLightSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerLogOutRow(onClick: () -> Unit) {
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