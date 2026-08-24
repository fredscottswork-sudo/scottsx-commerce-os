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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.domain.Message
import com.scottsx.app.data.domain.Product
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.flow.collectLatest

/**
 * Buyer ↔ seller chat. Auto-attaches the originating product (if any)
 * so the seller can answer against the exact SKU.
 */
@Composable
fun MessageThreadScreen(
    sellerId: String,
    productId: String?,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onViewStore: (String) -> Unit,
) {
    // Open or create the thread
    val thread = remember(sellerId, productId) {
        MarketplaceDataSource.openThreadWith(sellerId, productId)
    }
    val messages = remember(thread.id) { MarketplaceDataSource.messagesIn(thread.id) }
    val product = remember(productId) { productId?.let { MarketplaceDataSource.productFull(it) } }

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight)) {
        TopBar(
            sellerName = thread.sellerName,
            onBack = onBack,
        )
        // Live list of messages
        val listState = rememberLazyListState()
        // Re-read messages whenever the in-memory map updates
        var messagesState by remember { mutableStateOf(messages) }
        LaunchedEffect(thread.id) {
            snapshotFlow { MarketplaceDataSource.messagesIn(thread.id) }
                .collectLatest { msgs ->
                    messagesState = msgs
                    if (msgs.isNotEmpty()) {
                        listState.animateScrollToItem(msgs.lastIndex)
                    }
                }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item("header") {
                ThreadHeader(thread = thread, product = product, onOpenProduct = onOpenProduct, onViewStore = onViewStore)
            }
            items(messagesState, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
        }
        // Typing indicator (above the composer)
        var isPeerTyping by remember { mutableStateOf(false) }
        if (isPeerTyping) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(18.dp, 6.dp)
                    ) {
                        drawCircle(color = ScottsTechXColors.OnLightSecondary.copy(alpha = 0.6f), radius = 3f)
                        drawCircle(color = ScottsTechXColors.OnLightSecondary.copy(alpha = 0.4f), radius = 3f, center = Offset(6f, 0f))
                        drawCircle(color = ScottsTechXColors.OnLightSecondary.copy(alpha = 0.4f), radius = 3f, center = Offset(12f, 0f))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${thread.sellerName} is typing...",
                        fontSize = 11.sp,
                        color = ScottsTechXColors.OnLightSecondary,
                    )
                }
            }
        }
        // Composer
        ComposerBar(onSend = { text ->
            if (text.isNotBlank()) {
                MarketplaceDataSource.sendMessage(thread.id, text, isFromBuyer = true)
            }
        })
    }
}

@Composable
private fun TopBar(
    sellerName: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScottsTechXColors.BluePrimaryDark)
            .padding(start = 4.dp, end = 16.dp, top = 30.dp, bottom = 12.dp),
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
        Column(modifier = Modifier.weight(1f)) {
            Text(sellerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Online", color = Color(0xFF86EFAC), fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Storefront, contentDescription = "Store", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ThreadHeader(
    thread: com.scottsx.app.data.domain.MessageThread,
    product: Product?,
    onOpenProduct: (String) -> Unit,
    onViewStore: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (product != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable { onOpenProduct(product.id) }
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ScottsTechXColors.BluePrimary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Product context", color = ScottsTechXColors.OnLightSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(product.name, color = ScottsTechXColors.OnLight, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        Text("UGX ${com.scottsx.app.ui.util.formatUgx(product.priceUgx)}", color = ScottsTechXColors.BluePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 8.dp, start = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .clickable { onViewStore(thread.sellerId) }
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.BluePrimaryLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(thread.sellerName.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("About the seller", color = ScottsTechXColors.OnLightSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(thread.sellerName, color = ScottsTechXColors.OnLight, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val align = if (msg.isFromBuyer) Alignment.End else Alignment.Start
    val bg = if (msg.isFromBuyer) ScottsTechXColors.BluePrimary else Color.White
    val fg = if (msg.isFromBuyer) Color.White else ScottsTechXColors.OnLight
    val radius = RoundedCornerShape(
        topStart = 14.dp,
        topEnd = 14.dp,
        bottomStart = if (msg.isFromBuyer) 14.dp else 4.dp,
        bottomEnd = if (msg.isFromBuyer) 4.dp else 14.dp,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clip(radius)
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = msg.text,
                color = fg,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.widthIn(0.dp, 280.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = msg.timeLabel,
            color = ScottsTechXColors.OnLightSecondary,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun ComposerBar(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var showQuickReplies by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Attach button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { /* TODO: open image/file picker */ },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Attach",
                tint = ScottsTechXColors.OnLightSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        // Input field
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(ScottsTechXColors.PanelInputLight)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (text.isEmpty()) {
                Text("Message seller...", color = ScottsTechXColors.OnLightSecondary, fontSize = 13.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = ScottsTechXColors.OnLight, fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(2.dp))
        // Quick replies toggle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { showQuickReplies = !showQuickReplies },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.Star,
                contentDescription = "Quick replies",
                tint = ScottsTechXColors.OnLightSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        // Send button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (text.isNotBlank()) ScottsTechXColors.BluePrimary
                    else ScottsTechXColors.OnLightSecondary.copy(alpha = 0.3f)
                )
                .clickable(enabled = text.isNotBlank()) {
                    onSend(text.trim())
                    text = ""
                    showQuickReplies = false
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
    // Quick replies bar
    if (showQuickReplies) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.PanelInputLight)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("Got it", "On the way", "Thanks!", "Will check").forEach { reply ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable { text = reply },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(reply, fontSize = 13.sp, color = ScottsTechXColors.OnLight,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}

private fun Modifier.widthIn(min: androidx.compose.ui.unit.Dp, max: androidx.compose.ui.unit.Dp) =
    this.then(Modifier.fillMaxWidth(fraction = 0f).let { it })
