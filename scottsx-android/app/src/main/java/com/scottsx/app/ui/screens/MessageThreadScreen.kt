package com.scottsx.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.ChatMessage
import com.scottsx.app.data.domain.Conversation
import com.scottsx.app.data.domain.QuickReplyItem
import com.scottsx.app.data.remote.MessageStream
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.QuickChip
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

private val DEFAULT_QUICK_REPLIES = listOf(
    "Is this still available?",
    "What is your best price?",
    "Do you deliver?",
    "Thanks!",
)

/**
 * Conversation thread — the full messaging surface.
 *
 *  - text, photo, price-offer and system messages
 *  - offers negotiated inline (accept / decline / withdraw)
 *  - real read receipts (✓ sent, ✓✓ read) driven by the API, not faked
 *  - real typing indicator from the other party's heartbeat
 *  - retract your own messages, pin / mute the thread
 *  - saved quick replies, falling back to sensible defaults
 */
@Composable
fun MessageThreadScreen(
    conversationId: String,
    onBack: () -> Unit,
) {
    var conversation by remember { mutableStateOf<Conversation?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var otherTyping by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showQuickReplies by remember { mutableStateOf(false) }
    var quickReplies by remember { mutableStateOf<List<QuickReplyItem>>(emptyList()) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var photoDialogOpen by remember { mutableStateOf(false) }
    var photoUrl by remember { mutableStateOf("") }
    var offerDialogOpen by remember { mutableStateOf(false) }
    var offerAmount by remember { mutableStateOf("") }
    var offerQty by remember { mutableStateOf("1") }
    var pendingDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var lastTypingPing by remember { mutableStateOf(0L) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val myId = SessionCache.user.value?.id ?: ""

    suspend fun reload() {
        conversation = V2Client.fetchConversation(conversationId)
        val transcript = V2Client.fetchTranscript(conversationId)
        if (transcript.messages != messages) messages = transcript.messages
        otherTyping = transcript.otherTyping
        V2Client.markConversationRead(conversationId)
    }

    LaunchedEffect(conversationId) {
        reload()
        quickReplies = V2Client.fetchQuickReplies()
        // Poll for new messages, receipts and typing state.
        MessageStream.ticker(2500).collect { reload() }
    }

    LaunchedEffect(messages.size, otherTyping) {
        val target = messages.size - if (otherTyping) 0 else 1
        if (target >= 0 && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || sending) return
        input = ""
        sending = true
        scope.launch {
            V2Client.sendMessage(conversationId, trimmed)
            V2Client.setTyping(conversationId, false)
            sending = false
            reload()
        }
    }

    fun onInputChanged(value: String) {
        input = value
        // Throttle the typing heartbeat to one ping per 3 seconds.
        val now = System.currentTimeMillis()
        if (value.isNotBlank() && now - lastTypingPing > 3000) {
            lastTypingPing = now
            scope.launch { V2Client.setTyping(conversationId, true) }
        }
    }

    fun respond(message: ChatMessage, action: String) {
        scope.launch {
            V2Client.respondToOffer(conversationId, message.id, action)
            reload()
        }
    }

    val quickChips = if (quickReplies.isEmpty()) {
        DEFAULT_QUICK_REPLIES
    } else {
        quickReplies.map { it.text }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onBack)
                    .padding(4.dp)
                    .size(34.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation?.otherParty?.name ?: "Conversation",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (conversation?.otherParty?.verified == true) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = "Verified",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Text(
                    text = when {
                        otherTyping -> "typing…"
                        conversation?.otherParty?.role == "seller" -> "Seller"
                        conversation != null -> "Buyer"
                        else -> "Loading…"
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            }
            // Mute
            Icon(
                if (conversation?.muted == true) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                contentDescription = if (conversation?.muted == true) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val next = !(conversation?.muted ?: false)
                        scope.launch {
                            V2Client.setConversationState(conversationId, muted = next)
                            reload()
                        }
                    }
                    .padding(6.dp)
                    .size(22.dp),
            )
            // Pin
            Icon(
                Icons.Filled.PushPin,
                contentDescription = if (conversation?.pinned == true) "Unpin" else "Pin",
                tint = if (conversation?.pinned == true) ScottsTechXColors.WarningAmber else Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val next = !(conversation?.pinned ?: false)
                        scope.launch {
                            V2Client.setConversationState(conversationId, pinned = next)
                            reload()
                        }
                    }
                    .padding(6.dp)
                    .size(22.dp),
            )
        }

        // ── Product context bar ─────────────────────────────────────────────
        conversation?.productTitle?.let { productTitle ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🛍", fontSize = 15.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        productTitle,
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    conversation?.productPriceMinor?.let { minor ->
                        Text(
                            formatUgx(minor / 100),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScottsTechXColors.BluePrimary,
                        )
                    }
                }
            }
        }

        // ── Transcript ──────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(
                count = messages.size,
                key = { index -> messages[index].id },
            ) { index ->
                val message = messages[index]
                val mine = message.senderId == myId
                when {
                    message.isSystem -> SystemNotice(message.text)
                    message.isOffer -> OfferCard(
                        message = message,
                        isMine = mine,
                        onAccept = { respond(message, "accept") },
                        onDecline = { respond(message, "decline") },
                        onWithdraw = { respond(message, "withdraw") },
                    )
                    else -> ThreadBubble(
                        message = message,
                        isMine = mine,
                        onLongPress = { if (mine && !message.isRetracted) pendingDelete = message },
                    )
                }
            }
            if (otherTyping) {
                item(key = "typing-indicator") { TypingBubble() }
            }
        }

        // ── Quick replies ───────────────────────────────────────────────────
        AnimatedVisibility(visible = showQuickReplies) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickChips.forEach { reply ->
                    QuickChip(reply) {
                        input = reply
                        showQuickReplies = false
                    }
                }
            }
        }

        // ── Attach menu ─────────────────────────────────────────────────────
        AnimatedVisibility(visible = attachMenuOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "📷 Photo",
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            attachMenuOpen = false
                            photoDialogOpen = true
                        }
                        .padding(10.dp),
                    fontWeight = FontWeight.Medium,
                )
                // Offers only make sense for the buyer on a product thread.
                if (conversation?.mySide == "buyer" && conversation?.productId != null) {
                    Text(
                        "💰 Make an offer",
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ScottsTechXColors.WarningAmber.copy(alpha = 0.18f))
                            .clickable {
                                attachMenuOpen = false
                                offerDialogOpen = true
                            }
                            .padding(10.dp),
                        fontWeight = FontWeight.Medium,
                        color = ScottsTechXColors.WarningAmber,
                    )
                }
            }
        }

        // ── Composer ────────────────────────────────────────────────────────
        Surface(shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (attachMenuOpen) {
                                ScottsTechXColors.BluePrimary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .clickable { attachMenuOpen = !attachMenuOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Attach",
                        tint = if (attachMenuOpen) {
                            ScottsTechXColors.BluePrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                TextField(
                    value = input,
                    onValueChange = { onInputChanged(it) },
                    placeholder = { Text("Type a message…") },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (showQuickReplies) {
                                ScottsTechXColors.WarningAmber.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .clickable { showQuickReplies = !showQuickReplies },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Quick replies",
                        tint = if (showQuickReplies) {
                            ScottsTechXColors.WarningAmber
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (input.isNotBlank()) {
                                Brush.horizontalGradient(
                                    listOf(
                                        ScottsTechXColors.BluePrimary,
                                        ScottsTechXColors.BluePrimaryLight,
                                    ),
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                )
                            },
                            CircleShape,
                        )
                        .clickable(enabled = input.isNotBlank() && !sending) { send(input) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (input.isNotBlank()) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    )
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────
    if (photoDialogOpen) {
        AlertDialog(
            onDismissRequest = { photoDialogOpen = false },
            title = { Text("Send a photo") },
            text = {
                Column {
                    Text("Paste a link to the image you want to share.", fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = photoUrl,
                        onValueChange = { photoUrl = it },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = photoUrl.startsWith("http"),
                    onClick = {
                        val url = photoUrl.trim()
                        photoDialogOpen = false
                        photoUrl = ""
                        scope.launch {
                            V2Client.sendImageMessage(
                                conversationId,
                                url,
                                url.substringAfterLast('/').take(120),
                            )
                            reload()
                        }
                    },
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { photoDialogOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (offerDialogOpen) {
        AlertDialog(
            onDismissRequest = { offerDialogOpen = false },
            title = { Text("Make an offer") },
            text = {
                Column {
                    conversation?.productPriceMinor?.let { minor ->
                        Text(
                            "Listed at ${formatUgx(minor / 100)}. The seller can accept or decline.",
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    TextField(
                        value = offerAmount,
                        onValueChange = { offerAmount = it.filter { ch -> ch.isDigit() } },
                        placeholder = { Text("Your price per unit (UGX)") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = offerQty,
                        onValueChange = { offerQty = it.filter { ch -> ch.isDigit() } },
                        placeholder = { Text("Quantity") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (offerAmount.toLongOrNull() ?: 0L) > 0L,
                    onClick = {
                        val amount = offerAmount.toLongOrNull() ?: 0L
                        val qty = offerQty.toIntOrNull() ?: 1
                        offerDialogOpen = false
                        offerAmount = ""
                        offerQty = "1"
                        scope.launch {
                            // The API works in minor units (cents).
                            V2Client.sendOffer(conversationId, amount * 100, qty)
                            reload()
                        }
                    },
                ) { Text("Send offer") }
            },
            dismissButton = {
                TextButton(onClick = { offerDialogOpen = false }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this message?") },
            text = { Text("It will be removed for everyone in this conversation.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        V2Client.deleteMessage(conversationId, target.id)
                        reload()
                    }
                }) { Text("Delete", color = ScottsTechXColors.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

/** Centred pill for offer-accepted / declined events. */
@Composable
private fun SystemNotice(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            color = ScottsTechXColors.BluePrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Price offer with inline accept / decline / withdraw. */
@Composable
private fun OfferCard(
    message: ChatMessage,
    isMine: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val statusColor = when (message.offerStatus) {
        "accepted" -> ScottsTechXColors.SuccessGreen
        "pending" -> ScottsTechXColors.WarningAmber
        else -> ScottsTechXColors.ErrorRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ScottsTechXColors.WarningAmber.copy(alpha = 0.10f))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isMine) "💰 Your offer" else "💰 Offer received",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    message.offerStatus ?: "",
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                formatUgx((message.offerMinor ?: 0L) / 100),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (message.offerQuantity > 1) {
                Text(
                    "for ${message.offerQuantity} units",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message.productTitle?.let {
                Text(
                    "on $it",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            if (message.offerPending) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isMine) {
                        OfferButton("Withdraw", filled = false, onClick = onWithdraw)
                    } else {
                        OfferButton("Accept", filled = true, onClick = onAccept)
                        OfferButton("Decline", filled = false, onClick = onDecline)
                    }
                }
            }
            Text(
                message.timeLabel,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun OfferButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (filled) ScottsTechXColors.BluePrimary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (filled) Color.White else MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Message bubble.
 *
 * Read receipts are driven by `readByOther` from the API — the previous
 * implementation hardcoded "✓✓ Read" on every outgoing message, which claimed
 * the recipient had read messages they had never opened.
 */
@Composable
private fun ThreadBubble(
    message: ChatMessage,
    isMine: Boolean,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMine) 16.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 16.dp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (isMine) {
                            Brush.horizontalGradient(
                                listOf(
                                    ScottsTechXColors.BluePrimary,
                                    ScottsTechXColors.BluePrimaryLight,
                                ),
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        },
                        shape,
                    )
                    .clickable(onClick = onLongPress)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    if (message.isRetracted) {
                        Text(
                            "This message was deleted",
                            color = if (isMine) {
                                Color.White.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.None,
                            fontWeight = FontWeight.Light,
                        )
                    } else {
                        message.imageUrl?.let { url ->
                            Text(
                                "📷 ${message.attachmentName ?: "Photo"}",
                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                url,
                                color = if (isMine) {
                                    Color.White.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                            if (message.text.isNotBlank()) Spacer(Modifier.height(4.dp))
                        }
                        if (message.text.isNotBlank()) {
                            Text(
                                message.text,
                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            ) {
                Text(
                    message.timeLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                if (isMine && !message.isRetracted) {
                    Icon(
                        if (message.readByOther) Icons.Filled.DoneAll else Icons.Filled.Done,
                        contentDescription = if (message.readByOther) "Read" else "Sent",
                        tint = if (message.readByOther) {
                            ScottsTechXColors.BluePrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/** Animated three-dot typing indicator. */
@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dots = listOf(
        transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
            label = "d1",
        ),
        transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(600, delayMillis = 150, easing = LinearEasing), RepeatMode.Reverse),
            label = "d2",
        ),
        transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(600, delayMillis = 300, easing = LinearEasing), RepeatMode.Reverse),
            label = "d3",
        ),
    )
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        dots.forEach { dot ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        ScottsTechXColors.BluePrimary.copy(alpha = 0.4f + 0.6f * dot.value),
                        CircleShape,
                    ),
            )
        }
    }
}
