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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.domain.StorefrontTab
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import com.scottsx.app.ui.components.statusBarSpacer

/**
 * Public seller storefront. Reached via the buyer PDP's "View Store"
 * button. Mirrors the brief: store header, Follow + Message actions,
 * tabbed body (Products / Categories / Reviews / About).
 */
@Composable
fun SellerStorefrontScreen(
    sellerId: String,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onMessageSeller: (String, String?) -> Unit,
    onViewAllReviews: (String) -> Unit,
) {
    // Fully live storefront: `/api/v1/sellers/:id` for the store +
    // catalogue; `/me/favorites` for the follow state.
    var page by remember(sellerId) { mutableStateOf<com.scottsx.app.data.remote.V2Client.StorefrontPage?>(null) }
    var loadFailed by remember(sellerId) { mutableStateOf(false) }
    var isFollowing by remember(sellerId) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(sellerId) {
        loadFailed = false
        val fetched = try { com.scottsx.app.data.remote.V2Client.fetchStorefront(sellerId) } catch (_: Throwable) { null }
        page = fetched
        if (fetched == null) {
            loadFailed = true
            return@LaunchedEffect
        }
        com.scottsx.app.data.LiveMarketplace.cache(fetched.products)
        val favs = try { com.scottsx.app.data.remote.V2Client.fetchFavoriteSellers() } catch (_: Throwable) { null }
        isFollowing = favs?.any { it.id == sellerId } == true
    }

    if (page == null && !loadFailed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScottsTechXColors.PanelLight),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = ScottsTechXColors.BluePrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        }
        return
    }
    if (page == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight),
            contentAlignment = Alignment.Center,
        ) {
            Text("Store unavailable", color = ScottsTechXColors.OnLight)
        }
        return
    }
    val storefront = page!!
    // Domain projections from the real storefront payload.
    val sellerDomain = com.scottsx.app.data.domain.Seller(
        id = storefront.sellerId,
        name = storefront.storeName,
        rating = storefront.rating.toFloat(),
        location = storefront.city.ifBlank { "Uganda" },
        verified = storefront.verified,
    )
    val reviewCount = storefront.products.sumOf { it.ratingCount }
    val categoriesRows = storefront.products
        .groupBy { it.category }
        .map { (cat, list) -> com.scottsx.app.data.domain.SellerCategoryRow(cat, list.size) }
        .sortedByDescending { it.productCount }
    var tab by remember(sellerId) { mutableStateOf(StorefrontTab.Products) }

    // Is the caller the owner of this store? If so, tiles switch to owner
    // view: no wishlist hearts (you can't wishlist yourself) and moderation
    // status chips on each tile — same view the web seller dashboard gives.
    var ownerUid by remember(sellerId) { mutableStateOf<String?>(null) }
    LaunchedEffect(sellerId) {
        ownerUid = try { com.scottsx.app.data.remote.V2Client.fetchUserProfile()?.optString("uid") } catch (_: Throwable) { null }
    }
    val isOwner = ownerUid != null && ownerUid == sellerId

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight)) {
        // ---- Top bar with back ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.BluePrimaryDark)
                .statusBarSpacer()
                .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Store", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            StoreHeader(
                storeName = sellerDomain.name,
                location = sellerDomain.location,
                rating = sellerDomain.rating,
                reviewCount = reviewCount,
                followers = -1,  // -1 = hide the followers segment (not public data)
                productCount = storefront.products.size,
                verified = storefront.verified,
                isFollowing = isFollowing,
                onToggleFollow = {
                    scope.launch {
                        val ok = try {
                            if (isFollowing) com.scottsx.app.data.remote.V2Client.unsaveSeller(sellerId)
                            else com.scottsx.app.data.remote.V2Client.saveSeller(sellerId)
                        } catch (_: Throwable) { false }
                        if (ok) isFollowing = !isFollowing
                    }
                },
                onMessage = { onMessageSeller(sellerId, null) },
            )
            TabBar(selected = tab, onSelect = { tab = it })
            when (tab) {
                StorefrontTab.Products -> ProductsTab(storefront.products, onOpenProduct, isOwner)
                StorefrontTab.Categories -> CategoriesTab(categoriesRows)
                StorefrontTab.Reviews -> ReviewsTab(sellerDomain, onViewAllReviews)
                StorefrontTab.About -> AboutTab(sellerDomain, storefront.description, "")
            }
        }
    }
}

@Composable
private fun StoreHeader(
    storeName: String,
    location: String,
    rating: Float,
    reviewCount: Int,
    followers: Int,
    productCount: Int,
    verified: Boolean,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onMessage: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = storeName.first().uppercase(),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = storeName,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, false),
                        )
                        if (verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${"%.1f".format(rating)} ($reviewCount) · $location",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = if (followers >= 0) "$followers followers · $productCount products"
                               else "$productCount products",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                // Follow / Following button (prominent)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isFollowing) Color.White.copy(alpha = 0.18f) else Color.White,
                        )
                        .clickable(onClick = onToggleFollow)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isFollowing) Icons.Filled.Check else Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (isFollowing) Color.White else ScottsTechXColors.BluePrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            color = if (isFollowing) Color.White else ScottsTechXColors.BluePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Message button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onMessage)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Message, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Message", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBar(selected: StorefrontTab, onSelect: (StorefrontTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        StorefrontTab.values().forEach { t ->
            TabPill(label = t.label, selected = t == selected) { onSelect(t) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) ScottsTechXColors.BluePrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else ScottsTechXColors.OnLightSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ProductsTab(products: List<Product>, onOpen: (String) -> Unit, isOwner: Boolean = false) {
    if (products.isEmpty()) {
        Text("No products yet", color = ScottsTechXColors.OnLightSecondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        return
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        products.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { p ->
                    ProductCard(
                        product = p,
                        width = 0.dp, // 0dp + Modifier.weight → take available width
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(p.id) },
                        onAddToCart = { com.scottsx.app.data.CartStore.add(p.id) },
                        // Owner view: hearts off, moderation state on the tile.
                        showWishlist = !isOwner,
                        statusLabel = if (isOwner) p.status else null,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoriesTab(rows: List<com.scottsx.app.data.domain.SellerCategoryRow>) {
    if (rows.isEmpty()) {
        Text("No categories yet", color = ScottsTechXColors.OnLightSecondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.PanelInputLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Category, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.category.displayName,
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${row.productCount} product${if (row.productCount == 1) "" else "s"}",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReviewsTab(seller: com.scottsx.app.data.domain.Seller, onViewAll: (String) -> Unit) {
    // Real review count: sum of this store's listings' live rating counts
    // (pulled from the same catalogue rows the Products tab renders).
    val reviewCount = com.scottsx.app.data.LiveMarketplace.products.value
        .filter { it.seller.id == seller.id }
        .sumOf { it.ratingCount }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%.1f".format(seller.rating),
                color = ScottsTechXColors.OnLight,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Row {
                    repeat(5) {
                        Icon(Icons.Filled.Star, contentDescription = null,
                            tint = if (it < kotlin.math.round(seller.rating.toDouble()).toInt()) Color(0xFFFBBF24) else Color(0xFFD1D5DB),
                            modifier = Modifier.size(12.dp))
                    }
                }
                Text("$reviewCount reviews across all products", color = ScottsTechXColors.OnLightSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            if (reviewCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ScottsTechXColors.BluePrimary)
                        .clickable { onViewAll("") }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("See all", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (reviewCount == 0) {
            Text("No reviews yet — ratings from your product pages will roll up here.",
                color = ScottsTechXColors.OnLightSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun AboutTab(
    seller: com.scottsx.app.data.domain.Seller,
    description: String,
    joinedLabel: String = "",  // hidden when blank — no fabricated dates
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(14.dp),
        ) {
            Column {
                Text("About", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(description, color = ScottsTechXColors.OnLightSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ScottsTechXColors.OnLightSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(seller.location, color = ScottsTechXColors.OnLight, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${"%.1f".format(seller.rating)} rating", color = ScottsTechXColors.OnLight, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                if (joinedLabel.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = ScottsTechXColors.OnLightSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(joinedLabel, color = ScottsTechXColors.OnLight, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
