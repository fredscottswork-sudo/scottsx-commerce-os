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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.Session
import com.scottsx.app.data.remote.MessageStream
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Stage 5.x — Buyer Messages inbox.
 *
 * Shows the caller's recent conversations (derived from the
 * marketplace's seed sellers for now; the real implementation
 * subscribes to /api/v1/chat/v2/conversations). Tapping a row
 * navigates to a chat thread via [onOpenThread] — the host wires
 * this up to the existing MessageThreadScreen / V2Client.sendMessage
 * pipeline.
 *
 * The header mirrors the rest of the app's premium look — gradient
 * backdrop, white avatar / back button, search button on the right.
 */
@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    onOpenThread: (sellerId: String, productId: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<V2Client.Conversation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    // Hydrate from backend; fall back to marketplace seed if offline.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val list = V2Client.fetchConversations()
            conversations = list
            if (list.isEmpty()) loadError = "No conversations yet. Start a chat from a product page."
        } catch (t: Throwable) {
            loadError = "Couldn't load conversations: ${t.message ?: "unknown"}"
        } finally {
            isLoading = false
        }
    }

    val filtered = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter {
            it.otherPartyDisplayName.contains(query, ignoreCase = true) ||
                it.lastMessagePreview.orEmpty().contains(query, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(ScottsTechXColors.BackgroundLight)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ScottsTechXColors.BluePrimaryDark,
                            ScottsTechXColors.BluePrimary,
                        ),
                    ),
                )
                .padding(top = 36.dp, bottom = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Messages", color = Color.White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text("Your conversations with sellers",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null,
                tint = ScottsTechXColors.OnLightSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = ScottsTechXColors.OnLight,
                    fontSize = 14.sp,
                ),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Search conversations",
                            color = ScottsTechXColors.OnLightSecondary, fontSize = 13.sp)
                    }
                    inner()
                },
            )
        }

        // Body
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = ScottsTechXColors.BluePrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        } else if (loadError != null && conversations.isEmpty()) {
            // Empty / error state — show the marketplace sellers as a
            // friendly fallback so the user can tap a store to start a
            // conversation.
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(loadError ?: "", color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Text("Or message one of these stores:",
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                MarketplaceDataSource.allSellers.take(20).forEach { seller ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(seller.id, null) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ScottsTechXColors.BluePrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Store, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(seller.name, color = ScottsTechXColors.OnLight,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Tap to start a chat", color = ScottsTechXColors.OnLightSecondary,
                                fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
                items(filtered, key = { it.conversationId }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        onClick = { onOpenThread(conv.otherPartyId, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: V2Client.Conversation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
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
            Text(
                text = conversation.otherPartyDisplayName.firstOrNull()?.uppercase() ?: "?",
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.otherPartyDisplayName,
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                conversation.lastMessageAt?.let { ts ->
                    Text(ts.take(10),
                        color = ScottsTechXColors.OnLightSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = conversation.lastMessagePreview ?: "Tap to chat",
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (conversation.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ScottsTechXColors.BluePrimary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = conversation.unreadCount.toString(),
                    color = Color.White, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
