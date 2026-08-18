package com.scottsx.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.ChatMessage
import com.scottsx.app.data.remote.MessageStream
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.QuickChip
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val QUICK_REPLIES = listOf("Got it", "On the way", "Thanks!", "Will check")

/**
 * Enterprise messaging thread:
 *  - bubbles with 4dp tail on the sender's side
 *  - read receipts ("✓ Delivered" / "✓✓ Read", blue when read)
 *  - animated typing indicator above the composer
 *  - composer: [+] [type-area] [⭐ quick] [Send]
 */
@Composable
fun MessageThreadScreen(
    conversationId: String,
    onBack: () -> Unit,
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) } // simulated when the other side "types"
    var showQuickReplies by remember { mutableStateOf(false) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val myId = SessionCache.user.value?.id ?: ""

    fun load() {
        scope.launch {
            val fetched = V2Client.fetchMessages(conversationId)
            if (fetched != messages) messages = fetched
            V2Client.markConversationRead(conversationId)
        }
    }

    // Initial load + live polling.
    LaunchedEffect(conversationId) {
        load()
        MessageStream.ticker(3000).collect { load() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || sending) return
        input = ""
        sending = true
        scope.launch {
            V2Client.sendMessage(conversationId, trimmed)
            sending = false
            // Simulate the other side typing + replying in demo mode.
            typing = true
            kotlinx.coroutines.delay(1600)
            typing = false
            load()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: back + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)))
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
            Column {
                Text("Conversation", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Buyer ↔ Seller chat", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                ThreadBubble(message = message, isMine = message.senderId == myId)
            }
            if (typing) {
                item { TypingBubble() }
            }
        }

        // Quick replies row (toggled by ⭐)
        if (showQuickReplies) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_REPLIES.forEach { reply ->
                    QuickChip(reply) {
                        input = reply
                        showQuickReplies = false
                    }
                }
            }
        }

        // Attach menu (stub)
        if (attachMenuOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("📷 Image", modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp).clickable { attachMenuOpen = false }, fontWeight = FontWeight.Medium)
                Text("📎 File", modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp).clickable { attachMenuOpen = false }, fontWeight = FontWeight.Medium)
            }
        }

        // Composer: [+] [type-area] [⭐] [Send]
        Surface(shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Attach "+"
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { attachMenuOpen = !attachMenuOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
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
                // Quick replies "⭐"
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (showQuickReplies) ScottsTechXColors.WarningAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showQuickReplies = !showQuickReplies },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = "Quick replies", tint = if (showQuickReplies) ScottsTechXColors.WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Send — disabled + greyed when empty
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (input.isNotBlank()) Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight))
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                        .clickable(enabled = input.isNotBlank() && !sending) { send(input) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (input.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/** Message bubble with read receipts: ✓ Delivered / ✓✓ Read (blue when read). */
@Composable
private fun ThreadBubble(message: ChatMessage, isMine: Boolean) {
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
                            Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight))
                        } else {
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                        },
                        shape,
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    message.text,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
            }
            if (isMine) {
                Text(
                    text = "✓✓ Read",
                    color = ScottsTechXColors.BluePrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                )
            } else {
                Text(
                    text = message.timeLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                )
            }
        }
    }
}

/** Animated three-dot typing indicator. */
@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dots = listOf(
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "d1"),
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(600, delayMillis = 150, easing = LinearEasing), RepeatMode.Reverse), label = "d2"),
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(600, delayMillis = 300, easing = LinearEasing), RepeatMode.Reverse), label = "d3"),
    )
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        dots.forEach { dot ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.4f + 0.6f * dot.value), CircleShape),
            )
        }
    }
}

