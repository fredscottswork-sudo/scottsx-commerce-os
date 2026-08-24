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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.OfflineBanner
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.RatingRow
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.navBarSpacer
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Product detail with image hero, price, seller card and "Message seller". */
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    onMessageSeller: suspend (String) -> Unit,
    onViewCart: () -> Unit = {},
    onEditProduct: (String) -> Unit = {},
    onDeleted: () -> Unit = {},
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var wished by remember { mutableStateOf(false) }
    var messaging by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf(false) }
    var checkoutError by remember { mutableStateOf<String?>(null) }
    var addedToCart by remember { mutableStateOf(false) }
    // Owner management: the seller sees edit/delete instead of buy/chat,
    // plus what their listing's review state is.
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val currentUser by SessionCache.user.collectAsState()

    LaunchedEffect(productId, refresh) {
        val (loaded, failed) = V2Client.fetchProductByIdOutcome(productId)
        product = loaded
        loadError = failed
        // The heart must reflect the REAL saved state, not a local guess:
        // open a saved product and the heart starts filled.
        wished = V2Client.fetchBookmarks().any { it.id == productId }
    }

    if (loadError) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("We couldn't load this product", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Check your connection and try again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { refresh += 1 }) { Text("Retry") }
            }
        }
        return
    }

    val p = product
    if (p == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadError) CircularProgressIndicator()
            else {
                // The request succeeded but the product is gone (delisted or
                // removed) — say so instead of spinning forever.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This product is no longer available", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onBack) { Text("Go back") }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            // The gallery: one photo per position. Sellers can upload up to
            // five; buyers page through with the edge tap zones and jump
            // with the dots. A single-photo product renders exactly like the
            // old fixed hero image did.
            val galleryImages = p.gallery.ifEmpty { listOf(p.imageUrl) }
            var galleryIndex by remember { mutableIntStateOf(0) }
            if (galleryIndex >= galleryImages.size) galleryIndex = 0
            val shownImage = galleryImages.getOrNull(galleryIndex) ?: p.imageUrl
            AsyncImage(
                model = V2Client.absoluteMediaUrl(shownImage),
                contentDescription = p.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (galleryImages.size > 1) {
                // Declared BEFORE the back/heart controls so those stay on
                // top and keep their taps; the paging band keeps clear of
                // the status bar and the flash-deal badge.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarSpacer()
                        .padding(bottom = 48.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                galleryIndex =
                                    if (galleryIndex > 0) galleryIndex - 1 else galleryImages.size - 1
                            },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                galleryIndex = (galleryIndex + 1) % galleryImages.size
                            },
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    galleryImages.forEachIndexed { i, _ ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == galleryIndex) Color.White
                                    else Color.White.copy(alpha = 0.45f)
                                )
                                .clickable { galleryIndex = i },
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    // The hero photo deliberately runs under the status bar,
                    // but the back button must stay tappable below it.
                    .statusBarSpacer()
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarSpacer()
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    // Same translucent scrim as the back button, so the two
                    // controls read as a pair instead of one white puck and one
                    // dark circle.
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable {
                        // Optimistic flip, corrected by the server's answer:
                        // if the backend refuses (offline, unauthenticated)
                        // the heart flips back instead of lying.
                        val next = !wished
                        wished = next
                        scope.launch {
                            val saved = V2Client.toggleBookmark(p.id)
                            if (saved != next) wished = saved
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (wished) "Saved" else "Save",
                    tint = if (wished) ScottsTechXColors.ErrorRed else Color.White,
                )
            }
            if (p.isFlashDeal) {
                Surface(
                    color = ScottsTechXColors.ErrorRed,
                    shape = RoundedCornerShape(bottomEnd = 14.dp),
                    modifier = Modifier.align(Alignment.BottomStart),
                ) {
                    Text(
                        "FLASH DEAL  -${p.discountPercent}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // A stale product page is a silent trap for a buyer about to pay:
        // show the connection state above the scrolling content.
        OfflineBanner()

        // The owner's review state — buyers never see anything but approved
        // products, so this banner only ever renders for the seller.
        val isOwner = p.seller.id.isNotBlank() && p.seller.id == currentUser?.id
        val ownerBanner: OwnerBanner? = when (p.status) {
            "pending" -> OwnerBanner(
                "⏳",
                ScottsTechXColors.WarningAmber,
                "Awaiting admin review",
                "You'll get a notification when it's decided.",
            )
            "rejected" -> OwnerBanner(
                "⚠️",
                ScottsTechXColors.ErrorRed,
                "Rejected by admin",
                (p.rejectionReason ?: "The admin didn't give a reason.") +
                    " Edit the listing to resubmit it for review.",
            )
            "suspended" -> OwnerBanner(
                "⛔",
                ScottsTechXColors.ErrorRed,
                "Suspended",
                p.rejectionReason ?: "Contact support for details.",
            )
            else -> null
        }
        if (isOwner) {
            ownerBanner?.let { b ->
                Surface(
                    color = b.tint.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(b.emoji, fontSize = 16.sp)
                        Column {
                            Text(b.headline, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = b.tint)
                            Text(b.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(p.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            RatingRow(p.rating, p.ratingCount)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    formatUgx(p.priceMinor),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScottsTechXColors.BluePrimary,
                )
                if (p.oldPriceMinor != null && p.oldPriceMinor > p.priceMinor) {
                    Text(
                        formatUgx(p.oldPriceMinor),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
            Text(
                "${p.stockQuantity} in stock · ${p.location.ifBlank { "Uganda" }}",
                style = MaterialTheme.typography.labelMedium,
                color = if (p.stockQuantity > 5) ScottsTechXColors.SuccessGreen else ScottsTechXColors.WarningAmber,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))

            Surface(
                color = ScottsTechXColors.BluePrimary.copy(alpha = 0.07f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Brush.linearGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(p.seller.name.firstOrNull()?.uppercase() ?: "S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(p.seller.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            if (p.seller.verified) Text("✓", color = ScottsTechXColors.SuccessGreen, fontWeight = FontWeight.Bold)
                        }
                        Text("${p.seller.rating} ★ · ${p.seller.location.ifBlank { "Uganda" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Description", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(p.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Bottom action bar. The seller managing their own listing gets
        // Edit + Delete instead of the buy bar; everyone else gets Add to
        // cart (cash on delivery) + Message seller. The Nylon Pay "Buy now"
        // route used to live here, but it 503s until those credentials are
        // configured — this path works today.
        Surface(shadowElevation = 10.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navBarSpacer()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isOwner) {
                    PrimaryButton(
                        text = "Edit listing",
                        enabled = !deleting,
                        onClick = { onEditProduct(p.id) },
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        color = ScottsTechXColors.ErrorRed,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { confirmDelete = true },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete listing", tint = Color.White)
                        }
                    }
                } else {
                val soldOut = p.stockQuantity <= 0
                PrimaryButton(
                    text = when {
                        soldOut -> "Out of stock"
                        buying -> "Adding…"
                        else -> "Add to cart"
                    },
                    loading = buying,
                    enabled = !buying && !messaging && !soldOut,
                    onClick = {
                        buying = true
                        checkoutError = null
                        scope.launch {
                            V2Client.addToCart(p.id, quantity = 1)
                                .onSuccess { addedToCart = true }
                                .onFailure {
                                    checkoutError = it.message ?: "Could not add this to your cart."
                                }
                            buying = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = ScottsTechXColors.BluePrimary,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            messaging = true
                            scope.launch {
                                onMessageSeller(p.seller.id)
                                messaging = false
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ChatBubble, contentDescription = "Chat", tint = Color.White)
                    }
                }
                }
            }
        }
    }

    if (addedToCart) {
        AlertDialog(
            onDismissRequest = { addedToCart = false },
            title = { Text("Added to cart") },
            text = { Text("Keep browsing, or review your cart and place the order.") },
            confirmButton = {
                TextButton(onClick = {
                    addedToCart = false
                    onViewCart()
                }) { Text("View cart") }
            },
            dismissButton = {
                TextButton(onClick = { addedToCart = false }) { Text("Keep shopping") }
            },
        )
    }

    checkoutError?.let { err ->
        AlertDialog(
            onDismissRequest = { checkoutError = null },
            title = { Text("Could not add to cart") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { checkoutError = null }) { Text("OK") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this listing?") },
            text = {
                Text(
                    "\"${p.title}\" will be removed from the marketplace for good — " +
                        "buyers who have it open will see a \"no longer available\" page.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        deleting = true
                        scope.launch {
                            val ok = V2Client.deleteSellerProduct(p.id)
                            deleting = false
                            if (ok) onDeleted()
                            else deleteError = "Delete failed — check your connection and try again."
                        }
                    },
                ) {
                    Text(if (deleting) "Deleting…" else "Delete", color = ScottsTechXColors.ErrorRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }

    deleteError?.let { err ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text("Could not delete") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } },
        )
    }
}

/** The seller-only review-state banner shown above the listing content. */
private data class OwnerBanner(
    val emoji: String,
    val tint: Color,
    val headline: String,
    val detail: String,
)
