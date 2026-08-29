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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import com.scottsx.app.ui.components.navBarSpacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.LiveMarketplace
import com.scottsx.app.data.Session
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.MessageStream
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Buyer ↔ seller chat, end-to-end real:
 *
 *  1. `POST /conversations { sellerId, productId? }` resolves (or creates)
 *     the canonical conversation.
 *  2. [MessageStream] polls `GET /conversations/:id/messages` so the
 *     thread stays live.
 *  3. Sending goes through `POST /conversations/:id/messages` and the
 *     server's reply is injected back into the stream immediately.
 *
 * No seed fixtures anywhere — an empty thread means no messages yet.
 */
@Composable
fun MessageThreadScreen(
    sellerId: String,
    productId: String?,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onViewStore: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var conversationId by remember { mutableStateOf<String?>(null) }
    var sellerName by remember { mutableStateOf("Seller") }
    var sellerLogoUrl by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var product by remember { mutableStateOf<Product?>(null) }
    val myUid = remember { Session.userIdOrNull().orEmpty() }

    // Resolve the conversation + the product context in parallel.
    LaunchedEffect(sellerId, productId) {
        loadError = null
        conversationId = null
        try {
            val id = V2Client.openConversation(sellerId, productId)
            conversationId = id
            if (id == null) loadError = "Couldn't open this conversation — try again."
        } catch (t: Throwable) {
            loadError = "Couldn't open this conversation: ${t.message ?: "unknown error"}"
        }
        launch {
            val storefront = try { V2Client.fetchStorefront(sellerId) } catch (_: Throwable) { null }
            storefront?.let {
                if (it.storeName.isNotBlank()) sellerName = it.storeName
                sellerLogoUrl = it.logoUrl
            }
        }
        if (productId != null) {
            launch {
                product = try { LiveMarketplace.byIdOrFetch(productId) } catch (_: Throwable) { null }
            }
        }
    }

    val convId = conversationId
    val messagesFlow = remember(convId) {
        if (convId != null) MessageStream.messagesFor(convId)
        else kotlinx.coroutines.flow.MutableStateFlow<List<V2Client.ChatMessage>>(emptyList())
    }
    val messages by messagesFlow.collectAsState()

    val uiMessages = remember(messages, myUid) {
        messages.map { UiMessage.from(it, myUid) }.sortedBy { it.sortKey }
    }

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight)) {
        TopBar(sellerName = sellerName, sellerLogoUrl = sellerLogoUrl, onBack = onBack)

        when {
            loadError != null && convId == null -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(loadError ?: "", color = ScottsTechXColors.OnPanelSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Back to messages",
                            color = ScottsTechXColors.BluePrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onBack() }
                                .padding(8.dp),
                        )
                    }
                }
            }
            convId == null -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = ScottsTechXColors.BluePrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(uiMessages.size) {
                    if (uiMessages.isNotEmpty()) listState.animateScrollToItem(uiMessages.lastIndex)
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
                        ThreadHeader(
                            sellerId = sellerId,
                            sellerName = sellerName,
                            product = product,
                            onOpenProduct = onOpenProduct,
                            onViewStore = onViewStore,
                        )
                    }
                    if (uiMessages.isEmpty()) {
                        item("empty") {
                            Text(
                                "No messages yet — say hello and ask about the product.",
                                color = ScottsTechXColors.OnPanelSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(uiMessages, key = { it.id }) { msg ->
                        MessageBubble(msg)
                    }
                }

                ComposerBar(onSend = { text ->
                    if (text.isNotBlank()) {
                        scope.launch {
                            val sent = try {
                                V2Client.sendMessage(convId, text, productId = productId)
                            } catch (_: Throwable) { null }
                            if (sent != null) {
                                MessageStream.pushLocal(convId, sent)
                            } else {
                                // Surface failures by re-fetching — never echo a
                                // message the server rejected as if it had been sent.
                                MessageStream.refreshNow(convId)
                            }
                        }
                    }
                })
            }
        }
    }
}

/** Chat message flattened for the bubbles (backend sends camelCase SQL rows). */
private data class UiMessage(
    val id: String,
    val body: String,
    val attachmentUrl: String?,
    val isFromBuyer: Boolean,
    val timeLabel: String,
    val sortKey: String,
) {
    companion object {
        fun from(m: V2Client.ChatMessage, myUid: String): UiMessage = UiMessage(
            id = m.id.ifBlank { "${m.createdAt}-${m.senderUid}" },
            body = m.content,
            attachmentUrl = m.attachmentUrl,
            isFromBuyer = m.senderUid == myUid,
            timeLabel = m.createdAt.replace('T', ' ').take(16),
            sortKey = m.createdAt,
        )
    }
}

@Composable
private fun TopBar(
    sellerName: String,
    sellerLogoUrl: String? = null,
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
        // The store's real logo when it has one — initial otherwise.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!sellerLogoUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = sellerLogoUrl,
                    contentDescription = sellerName,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    (sellerName.firstOrNull()?.uppercase() ?: "S").toString(),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(sellerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Live chat", color = Color(0xFF86EFAC), fontSize = 11.sp)
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
    sellerId: String,
    sellerName: String,
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
                    .background(ScottsTechXColors.CardSurface)
                    .clickable { onOpenProduct(product.id) }
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The product's REAL photo (web parity) — gradient initial
                    // only when the listing has no image.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ScottsTechXColors.BluePrimary),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!product.imageUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = product.imageUrl,
                                contentDescription = product.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Product context", color = ScottsTechXColors.OnCardSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(product.name, color = ScottsTechXColors.OnCard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(com.scottsx.app.ui.util.formatUgx(product.priceUgx), color = ScottsTechXColors.BluePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 8.dp, start = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ScottsTechXColors.CardSurface)
                .clickable { onViewStore(sellerId) }
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
                    Text(sellerName.firstOrNull()?.uppercase() ?: "S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("About the seller", color = ScottsTechXColors.OnCardSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(sellerName, color = ScottsTechXColors.OnCard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage) {
    val align = if (msg.isFromBuyer) Alignment.End else Alignment.Start
    val bg = if (msg.isFromBuyer) ScottsTechXColors.BluePrimary else ScottsTechXColors.CardSurface
    val fg = if (msg.isFromBuyer) Color.White else ScottsTechXColors.OnCard
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
                text = msg.body,
                color = fg,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = msg.timeLabel,
            color = ScottsTechXColors.OnPanelSecondary,
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
            .background(ScottsTechXColors.CardSurface)
            // Clear BOTH: the keyboard (imePadding — zero when it's closed)
            // and the gesture pill (navBarSpacer). The small double-lift when
            // the keyboard is open is the accepted trade-off for never hiding
            // the composer under either bar.
            .imePadding()
            .navBarSpacer()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Attach button (multipart upload ships with the image wave —
        // for now it opens the quick-replies sheet as a placeholder-free
        // no-op rather than pretending to send anything).
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { showQuickReplies = !showQuickReplies },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "More",
                tint = ScottsTechXColors.OnPanelSecondary,
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
                Text("Message seller...", color = ScottsTechXColors.OnPanelSecondary, fontSize = 13.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = ScottsTechXColors.OnPanel, fontSize = 13.sp),
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
                Icons.Filled.Star,
                contentDescription = "Quick replies",
                tint = ScottsTechXColors.OnPanelSecondary,
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
                    else ScottsTechXColors.OnPanelSecondary.copy(alpha = 0.3f)
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
                        .background(ScottsTechXColors.CardSurface)
                        .clickable { text = reply },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(reply, fontSize = 13.sp, color = ScottsTechXColors.OnCard,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}
