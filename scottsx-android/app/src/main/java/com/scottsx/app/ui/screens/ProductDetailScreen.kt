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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.RatingRow
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Product detail with image hero, price, seller card and "Message seller". */
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    onMessageSeller: suspend (String) -> Unit,
    onViewCart: () -> Unit = {},
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var wished by remember { mutableStateOf(false) }
    var messaging by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf(false) }
    var checkoutError by remember { mutableStateOf<String?>(null) }
    var addedToCart by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(productId) {
        product = V2Client.fetchProductById(productId)
    }

    val p = product
    if (p == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            AsyncImage(
                model = p.imageUrl,
                contentDescription = p.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White)
            }
            Surface(
                color = Color.White.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { wished = !wished },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (wished) ScottsTechXColors.ErrorRed else ScottsTechXColors.OnLightSecondary,
                    )
                }
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

        // Bottom action bar: Add to cart (cash on delivery) + Message seller.
        // The Nylon Pay "Buy now" route used to live here, but it 503s until
        // those credentials are configured — this path works today.
        Surface(shadowElevation = 10.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
}
