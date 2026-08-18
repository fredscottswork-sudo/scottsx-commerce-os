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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Cart
import com.scottsx.app.data.domain.CartItem
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.ListDivider
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.ProductImage
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.bottomInset
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * CartScreen — review the basket and place a cash-on-delivery order.
 *
 * Mirrors the web cart. Checkout goes through POST /me/cart/checkout, which
 * creates one order per line and decrements stock; the Nylon Pay hosted-payment
 * route is deliberately not used here because it 503s until those credentials
 * exist, and a buyer should still be able to buy.
 *
 * Every mutation renders the cart the *server* returned rather than patching
 * local state, so quantity, totals and stock can never drift from the truth.
 */
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit = {},
    onBrowse: () -> Unit = {},
    onOrderPlaced: () -> Unit = {},
) {
    var cart by remember { mutableStateOf(Cart()) }
    var loading by remember { mutableStateOf(true) }
    /** Product id currently being mutated, so only that row shows a spinner. */
    var busyId by remember { mutableStateOf<String?>(null) }
    var placing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var placed by remember { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    /** Applies a cart-returning call, surfacing the server's message on failure. */
    fun apply(productId: String?, block: suspend () -> Result<Cart>) {
        busyId = productId
        error = null
        scope.launch {
            block()
                .onSuccess { cart = it }
                .onFailure { error = it.message ?: "Something went wrong. Please try again." }
            busyId = null
        }
    }

    LaunchedEffect(Unit) {
        V2Client.fetchCart()
            .onSuccess { cart = it }
            .onFailure { error = it.message ?: "Could not load your cart." }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent),
                    ),
                    RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                )
                .statusBarSpacer()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onBack)
                        .padding(4.dp)
                        .size(32.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your cart", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (cart.itemCount == 1) "1 item" else "${cart.itemCount} items",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
                if (cart.items.isNotEmpty()) {
                    Text(
                        "Clear",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { confirmClear = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        error?.let { message ->
            Surface(
                color = ScottsTechXColors.ErrorRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    message,
                    color = ScottsTechXColors.ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        when {
            loading -> LoadingRow()

            cart.isEmpty -> Column {
                EmptyState("🛒", "Your cart is empty", "Browse the marketplace and add something you like.")
                PrimaryButton(
                    text = "Start shopping",
                    onClick = onBrowse,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            else -> Column(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp + bottomInset()),
                ) {
                    items(cart.items, key = { it.productId }) { item ->
                        CartRow(
                            item = item,
                            busy = busyId == item.productId,
                            onOpen = { onProductClick(item.productId) },
                            onDecrease = {
                                apply(item.productId) {
                                    V2Client.setCartQuantity(item.productId, item.quantity - 1)
                                }
                            },
                            onIncrease = {
                                apply(item.productId) {
                                    V2Client.setCartQuantity(item.productId, item.quantity + 1)
                                }
                            },
                            onRemove = {
                                apply(item.productId) { V2Client.removeFromCart(item.productId) }
                            },
                        )
                        ListDivider()
                    }
                }

                // Summary + place order
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Subtotal", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(
                                formatUgx(cart.subtotalMinor),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScottsTechXColors.BluePrimary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pay with cash on delivery. The seller is notified as soon as you order.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))

                        val blocked = cart.items.any { it.isUnavailable }
                        if (blocked) {
                            Text(
                                "Remove the unavailable items to continue.",
                                fontSize = 12.sp,
                                color = ScottsTechXColors.ErrorRed,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        PrimaryButton(
                            text = if (placing) "Placing order…" else "Place order",
                            onClick = {
                                placing = true
                                error = null
                                scope.launch {
                                    V2Client.checkoutCart()
                                        .onSuccess {
                                            placed = it.orderCount
                                            cart = Cart()
                                        }
                                        .onFailure {
                                            error = it.message ?: "Could not place the order."
                                        }
                                    placing = false
                                }
                            },
                            enabled = !placing && !blocked && busyId == null,
                            loading = placing,
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Empty your cart?") },
            text = { Text("This removes every item. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    apply(null) { V2Client.clearCart() }
                }) { Text("Empty cart") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep items") }
            },
        )
    }

    placed?.let { count ->
        AlertDialog(
            onDismissRequest = {
                placed = null
                onOrderPlaced()
            },
            title = { Text("Order placed") },
            text = {
                Text(
                    if (count == 1) {
                        "Your order is on its way to the seller. Pay cash when it arrives."
                    } else {
                        "$count orders placed — one per seller. Pay cash on delivery."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    placed = null
                    onOrderPlaced()
                }) { Text("View orders") }
            },
            dismissButton = {
                TextButton(onClick = { placed = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun CartRow(
    item: CartItem,
    busy: Boolean,
    onOpen: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductImage(
            imageKey = item.productId,
            categoryLabel = item.title,
            imageUrl = item.imageUrl.ifBlank { null },
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp)),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (item.isUnavailable) TextDecoration.LineThrough else null,
            )
            Text(
                item.sellerName.ifBlank { "ScottsTechX seller" },
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            // Be explicit about *why* a line cannot be bought, so the buyer can
            // act instead of guessing why checkout is disabled.
            when {
                item.status != "approved" -> UnavailableNote("No longer available from this seller")
                item.stockQuantity <= 0 -> UnavailableNote("Out of stock")
                item.quantity > item.stockQuantity ->
                    UnavailableNote("Only ${item.stockQuantity} left — reduce the quantity")
                else -> Text(
                    formatUgx(item.priceMinor) + " each",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QtyButton("−", enabled = !busy && item.quantity > 0, onClick = onDecrease)
                Text(
                    if (busy) "…" else item.quantity.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp),
                )
                // Never offer a quantity the seller cannot fulfil.
                QtyButton(
                    "+",
                    enabled = !busy && item.quantity < item.stockQuantity,
                    onClick = onIncrease,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove ${item.title}",
                    tint = ScottsTechXColors.ErrorRed,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !busy, onClick = onRemove)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }

        Text(
            formatUgx(item.lineTotalMinor),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ScottsTechXColors.BluePrimary,
        )
    }
}

@Composable
private fun UnavailableNote(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ScottsTechXColors.ErrorRed)
}

@Composable
private fun QtyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (enabled) {
            ScottsTechXColors.BluePrimary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = CircleShape,
        modifier = Modifier
            .size(30.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    ScottsTechXColors.BluePrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}
