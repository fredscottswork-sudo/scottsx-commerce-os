package com.scottsx.app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.CartStore
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.WishlistStore
import com.scottsx.app.data.domain.NearbySeller
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductSpec
import com.scottsx.app.data.domain.ProductVariant
import com.scottsx.app.data.domain.Review
import com.scottsx.app.data.domain.Seller
import com.scottsx.app.data.domain.SessionCache
import com.scottsx.app.data.domain.StockStatus
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.ProductImageGallery
import com.scottsx.app.ui.components.SectionTitle
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import kotlinx.coroutines.launch

/**
 * Product Detail Page (Stage 3).
 *
 * Single composable, all 25+ brief sections stacked top-to-bottom:
 *  1. Top bar (back + share + heart)
 *  2. Image gallery
 *  3. Product header (name, rating, wishlist, share, purchases)
 *  4. Price block (with discount)
 *  5. Variants picker
 *  6. Stock pill
 *  7. Quantity picker
 *  8. Delivery section
 *  9. Nearby sellers carousel
 * 10. Seller card
 * 11. AI assistant CTA
 * 12. About this product
 * 13. Specifications table
 * 14. Ratings & Reviews preview
 * 15. Similar products
 * 16. Recommended products
 * 17. Sticky bottom action bar (Wishlist / Add to Cart / Buy Now)
 */
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    onViewSeller: (sellerId: String) -> Unit,
    onMessageSeller: (sellerId: String, productId: String?) -> Unit,
    onViewAllReviews: (productId: String) -> Unit,
    onOpenCart: () -> Unit,
    onOpenNearby: (productId: String) -> Unit,
    onOpenAi: (productId: String) -> Unit,
    onOpenProduct: (productId: String) -> Unit,
) {
    val ctx = LocalContext.current
    val product = remember(productId) { MarketplaceDataSource.productFull(productId) }
    if (product == null) {
        ProductNotFound(onBack)
        return
    }
    // Local UI state
    val reviews = remember(productId) { MarketplaceDataSource.reviewsFor(productId) }
    val distribution = remember(productId) { MarketplaceDataSource.ratingDistributionFor(productId) }
    val nearby = remember(productId) { MarketplaceDataSource.nearbySellersFor(productId) }
    val delivery = remember(productId) { MarketplaceDataSource.deliveryOptionsFor(productId) }
    val similar = remember(productId) { MarketplaceDataSource.similarProducts(productId) }
    val recommended = remember(productId) { MarketplaceDataSource.recommendedProducts(productId) }

    // Selected variant (first one by default, or null if no variants)
    var selectedVariant by remember(productId) {
        mutableStateOf(product.variants.firstOrNull())
    }
    val effectivePriceUgx = product.priceUgx + (selectedVariant?.priceDeltaUgx ?: 0L)
    val effectiveStock = selectedVariant?.stock ?: product.stock
    val stockStatus = MarketplaceDataSource.stockStatusFor(
        product.copy(stock = effectiveStock),
    )

    // Wishlist + cart state
    val isWishlisted = remember(productId) { WishlistStore.contains(productId) }
    var wishlisted by remember(productId) { mutableStateOf(isWishlisted) }
    var quantity by remember(productId) { mutableStateOf(1) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun addToCart() {
        CartStore.add(productId, quantity, selectedVariant?.id)
        scope.launch { snackbar.showSnackbar("Added to cart — $quantity × ${product.name}") }
    }
    fun buyNow() {
        CartStore.add(productId, quantity, selectedVariant?.id)
        onOpenCart()
    }
    fun toggleWishlist() {
        val nowIn = WishlistStore.toggle(productId)
        wishlisted = nowIn
        scope.launch {
            snackbar.showSnackbar(if (nowIn) "Added to wishlist" else "Removed from wishlist")
        }
    }
    fun shareProduct() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check this out on ScottsTechX: ${product.name} — UGX ${formatUgx(effectivePriceUgx)}")
        }
        ctx.startActivity(Intent.createChooser(send, "Share ${product.name}"))
    }

    Box(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopBar(
                onBack = onBack,
                onShare = { shareProduct() },
                wishlisted = wishlisted,
                onToggleWishlist = { toggleWishlist() },
            )
            // Scrollable body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                ProductImageGallery(
                    images = product.images,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(16.dp))
                ProductHeader(
                    product = product,
                    wishlisted = wishlisted,
                    onToggleWishlist = { toggleWishlist() },
                    onShare = { shareProduct() },
                )
                PriceBlock(
                    product = product,
                    effectivePriceUgx = effectivePriceUgx,
                )
                if (product.variants.isNotEmpty()) {
                    VariantsPicker(
                        variants = product.variants,
                        selected = selectedVariant,
                        onSelect = { selectedVariant = it },
                    )
                }
                QuantityAndStock(
                    stockStatus = stockStatus,
                    stock = effectiveStock,
                    quantity = quantity,
                    onQuantity = { quantity = it.coerceIn(1, effectiveStock.coerceAtLeast(1)) },
                )
                DeliverySection(
                    options = delivery,
                    userLocation = "Kampala",  // Stage 3 — wire to user profile saved-address once backend lands
                )
                NearbySellersCarousel(
                    nearby = nearby,
                    onSelect = { onViewSeller(it.sellerId) },
                )
                SellerCard(
                    seller = product.seller,
                    onViewStore = { onViewSeller(product.seller.id) },
                    onMessage = { onMessageSeller(product.seller.id, product.id) },
                )
                AiCta(
                    onAsk = { onOpenAi(productId) },
                )
                AboutSection(product = product)
                if (product.specs.isNotEmpty()) {
                    SpecificationsSection(product.specs)
                }
                RatingsAndReviews(
                    product = product,
                    distribution = distribution,
                    reviews = reviews,
                    onViewAll = { onViewAllReviews(productId) },
                )
                if (similar.isNotEmpty()) {
                    SimilarProducts(
                        products = similar,
                        onOpen = { onOpenProduct(it) },
                    )
                }
                if (recommended.isNotEmpty()) {
                    RecommendedProducts(
                        products = recommended,
                        onOpen = { onOpenProduct(it) },
                    )
                }
                Spacer(Modifier.height(96.dp))  // room for sticky action bar
            }
            // Sticky action bar
            ActionBar(
                isWishlisted = wishlisted,
                onWishlist = { toggleWishlist() },
                onAddToCart = { addToCart() },
                onBuyNow = { buyNow() },
                buyNowEnabled = stockStatus != StockStatus.OutOfStock,
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Snackbar(snackbarData = it)
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
    wishlisted: Boolean,
    onToggleWishlist: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(icon = Icons.Filled.ArrowBack, onClick = onBack)
        Spacer(Modifier.weight(1f))
        RoundIconButton(icon = Icons.Filled.IosShare, onClick = onShare)
        Spacer(Modifier.width(8.dp))
        RoundIconButton(
            icon = if (wishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            tint = if (wishlisted) Color(0xFFE11D48) else ScottsTechXColors.OnLight,
            onClick = onToggleWishlist,
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = ScottsTechXColors.OnLight,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ProductHeader(
    product: Product,
    wishlisted: Boolean,
    onToggleWishlist: () -> Unit,
    onShare: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = product.name,
            color = ScottsTechXColors.OnLight,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Star rating
            repeat(5) { idx ->
                val active = idx < product.rating.toInt()
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (active) Color(0xFFFBBF24) else Color(0xFFD1D5DB),
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${"%.1f".format(product.rating)} (${product.ratingCount} reviews)",
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${product.purchases}+ sold",
            color = ScottsTechXColors.OnLightSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PriceBlock(product: Product, effectivePriceUgx: Long) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "UGX ${formatUgx(effectivePriceUgx)}",
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
            )
            if (product.oldPriceUgx != null && product.oldPriceUgx > effectivePriceUgx) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "UGX ${formatUgx(product.oldPriceUgx)}",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 13.sp,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                )
            }
        }
        val save = product.oldPriceUgx?.let { ((it - effectivePriceUgx) * 100.0 / it).toInt() }
        if (save != null && save > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Save $save%",
                color = Color(0xFF059669),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun VariantsPicker(
    variants: List<ProductVariant>,
    selected: ProductVariant?,
    onSelect: (ProductVariant) -> Unit,
) {
    val byAxis = variants.groupBy { it.axis }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        byAxis.forEach { (axis, list) ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = axis.uppercase(),
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list) { v ->
                        val isSelected = selected?.id == v.id
                        val isOut = v.stock <= 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected -> ScottsTechXColors.BluePrimary
                                        isOut -> ScottsTechXColors.PanelInputLight
                                        else -> Color.White
                                    },
                                )
                                .clickable(enabled = !isOut) { onSelect(v) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = v.value + if (isOut) " · out" else "",
                                color = when {
                                    isSelected -> Color.White
                                    isOut -> ScottsTechXColors.OnLightSecondary
                                    else -> ScottsTechXColors.OnLight
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityAndStock(
    stockStatus: StockStatus,
    stock: Int,
    quantity: Int,
    onQuantity: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Stock pill
        val (pillText, pillBg) = when (stockStatus) {
            StockStatus.InStock -> "In Stock" to Color(0xFFD1FAE5)
            StockStatus.LowStock -> "Only $stock left" to Color(0xFFFEF3C7)
            StockStatus.OutOfStock -> "Out of Stock" to Color(0xFFFEE2E2)
        }
        val pillTextColor = when (stockStatus) {
            StockStatus.InStock -> Color(0xFF059669)
            StockStatus.LowStock -> Color(0xFFB45309)
            StockStatus.OutOfStock -> Color(0xFFB91C1C)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(pillBg)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                text = pillText,
                color = pillTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        // Quantity stepper
        if (stockStatus != StockStatus.OutOfStock) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ScottsTechXColors.PanelInputLight)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepperButton(icon = Icons.Filled.Remove) {
                    onQuantity(quantity - 1)
                }
                Text(
                    text = "$quantity",
                    color = ScottsTechXColors.OnLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                StepperButton(icon = Icons.Filled.Add) {
                    onQuantity(quantity + 1)
                }
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DeliverySection(
    options: List<com.scottsx.app.data.domain.DeliveryOption>,
    userLocation: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(title = "Delivery", modifier = Modifier.padding(start = 0.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Deliver to: $userLocation",
                    color = ScottsTechXColors.OnLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            options.forEach { opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (opt.id.endsWith("exp")) Icons.Filled.Bolt else Icons.Filled.LocalShipping,
                        contentDescription = null,
                        tint = if (opt.id.endsWith("exp")) Color(0xFFEA580C) else ScottsTechXColors.BluePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = opt.label,
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = opt.etaDaysLabel,
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (opt.feeUgx == 0L) "Free" else "UGX ${formatUgx(opt.feeUgx)}",
                        color = ScottsTechXColors.OnLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbySellersCarousel(
    nearby: List<NearbySeller>,
    onSelect: (NearbySeller) -> Unit,
) {
    if (nearby.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SectionTitle(
            title = "Available Near You",
            viewAll = "${nearby.count()} sellers",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(nearby) { ns ->
                NearbySellerCard(ns, onClick = { onSelect(ns) })
            }
        }
    }
}

@Composable
private fun NearbySellerCard(ns: NearbySeller, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.BluePrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ns.sellerName.first().uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ns.sellerName,
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "UGX ${formatUgx(ns.priceUgx)}",
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ScottsTechXColors.OnLightSecondary, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "${"%.1f".format(ns.distanceKm)} km · ${ns.deliveryDaysLabel}",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            val (pill, pillColor) = if (ns.inStock) "In Stock" to Color(0xFF059669) else "Out of Stock" to Color(0xFFB91C1C)
            Text(text = pill, color = pillColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SellerCard(
    seller: Seller,
    onViewStore: () -> Unit,
    onMessage: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.BluePrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = seller.name.first().uppercase(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = seller.name,
                            color = ScottsTechXColors.OnLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        if (seller.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(11.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${"%.1f".format(seller.rating)} · ${seller.location}",
                            color = ScottsTechXColors.OnLightSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                PrimaryButton(
                    text = "View Store",
                    onClick = onViewStore,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ScottsTechXColors.PanelInputLight)
                        .clickable(onClick = onMessage)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Filled.Message, contentDescription = "Message", tint = ScottsTechXColors.OnLight, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AiCta(onAsk: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(ScottsTechXColors.BluePrimary, Color(0xFF6366F1)),
                ),
            )
            .clickable(onClick = onAsk)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Ask ScottsTechX AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Compare prices, find similar, or check what's nearby", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun AboutSection(product: Product) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(title = "About this product", modifier = Modifier.padding(start = 0.dp))
        Text(
            text = product.description,
            color = ScottsTechXColors.OnLight,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(14.dp),
        )
    }
}

@Composable
private fun SpecificationsSection(specs: List<ProductSpec>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(title = "Specifications", modifier = Modifier.padding(start = 0.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            specs.forEachIndexed { i, spec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = spec.key,
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = spec.value,
                        color = ScottsTechXColors.OnLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (i != specs.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ScottsTechXColors.PanelBorderHint),
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingsAndReviews(
    product: Product,
    distribution: com.scottsx.app.data.domain.RatingDistribution,
    reviews: List<Review>,
    onViewAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(
            title = "Ratings & Reviews",
            viewAll = if (reviews.size > 3) "See all (${reviews.size})" else null,
            onViewAll = onViewAll,
            modifier = Modifier.padding(start = 0.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                    Text(
                        text = "%.1f".format(product.rating),
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                    )
                    Row {
                        repeat(5) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(11.dp))
                        }
                    }
                    Text(
                        text = "${reviews.size} reviews",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    for (stars in 5 downTo 1) {
                        RatingBarRow(stars = stars, percent = distribution.percent(stars))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        reviews.take(3).forEach { r -> ReviewRow(r) }
    }
}

@Composable
internal fun RatingBarRow(stars: Int, percent: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = "$stars", color = ScottsTechXColors.OnLightSecondary, fontSize = 10.sp, modifier = Modifier.width(10.dp))
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ScottsTechXColors.PanelInputLight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(Color(0xFFFBBF24)),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${(percent * 100).toInt()}%",
            color = ScottsTechXColors.OnLightSecondary,
            fontSize = 10.sp,
            modifier = Modifier.width(28.dp),
        )
    }
}

@Composable
internal fun ReviewRow(r: Review) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.PanelInputLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(r.authorName.first().uppercase(), color = ScottsTechXColors.OnLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.authorName, color = ScottsTechXColors.OnLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (r.verifiedPurchase) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFD1FAE5))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("Verified", color = Color(0xFF059669), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = if (it < r.rating) Color(0xFFFBBF24) else Color(0xFFD1D5DB), modifier = Modifier.size(10.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(r.dateLabel, color = ScottsTechXColors.OnLightSecondary, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(r.text, color = ScottsTechXColors.OnLight, fontSize = 12.sp, lineHeight = 17.sp)
            if (r.variantLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Purchased: ${r.variantLabel}",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SimilarProducts(products: List<Product>, onOpen: (String) -> Unit) {
    if (products.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SectionTitle(title = "You may also like", modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(products) { p ->
                ProductCard(
                    product = p,
                    width = 160.dp,
                    onClick = { onOpen(p.id) },
                    onAddToCart = { CartStore.add(p.id) },
                )
            }
        }
    }
}

@Composable
private fun RecommendedProducts(products: List<Product>, onOpen: (String) -> Unit) {
    if (products.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SectionTitle(title = "Recommended for you", modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(products) { p ->
                ProductCard(
                    product = p,
                    width = 160.dp,
                    onClick = { onOpen(p.id) },
                    onAddToCart = { CartStore.add(p.id) },
                )
            }
        }
    }
}

@Composable
private fun ActionBar(
    isWishlisted: Boolean,
    onWishlist: () -> Unit,
    onAddToCart: () -> Unit,
    onBuyNow: () -> Unit,
    buyNowEnabled: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Wishlist icon button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.PanelInputLight)
                    .clickable(onClick = onWishlist),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isWishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Wishlist",
                    tint = if (isWishlisted) Color(0xFFE11D48) else ScottsTechXColors.OnLight,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Add to Cart
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ScottsTechXColors.BluePrimaryLight)
                    .clickable(onClick = onAddToCart)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Add to Cart",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Buy Now - the prominent one
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (buyNowEnabled) Brush.horizontalGradient(
                            colors = listOf(ScottsTechXColors.BluePrimary, Color(0xFF6366F1)),
                        ) else Brush.horizontalGradient(colors = listOf(Color(0xFFD1D5DB), Color(0xFF9CA3AF))),
                    )
                    .clickable(enabled = buyNowEnabled, onClick = onBuyNow)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "BUY NOW",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductNotFound(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Product unavailable", color = ScottsTechXColors.OnLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("It may have been removed or is no longer in stock.", color = ScottsTechXColors.OnLightSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ScottsTechXColors.BluePrimary)
                .clickable(onClick = onBack)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text("Go back", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
