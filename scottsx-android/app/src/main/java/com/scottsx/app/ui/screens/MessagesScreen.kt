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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Conversation
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.ListDivider
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.theme.ScottsTechXColors

/** Shared inbox UI used by both buyer and seller message lists. */
@Composable
fun ConversationListScreen(
    title: String,
    onBack: () -> Unit,
    onThreadClick: (String) -> Unit,
) {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        conversations = V2Client.fetchConversations()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)),
                    RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                )
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
            }
        }

        if (loading) {
            LoadingRow()
        } else if (conversations.isEmpty()) {
            EmptyState("💬", "No conversations yet", "Message a seller from any product page to start chatting.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation, onClick = { onThreadClick(conversation.id) })
                    ListDivider()
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Brush.linearGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                conversation.otherParty.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.otherParty.name,
                    fontWeight = if (conversation.unread > 0) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    conversation.lastTime.substringAfter("T").substring(0, 5).ifBlank { "" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.lastMessage,
                    fontSize = 13.sp,
                    color = if (conversation.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.unread > 0) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(ScottsTechXColors.BluePrimary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${conversation.unread}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            conversation.productTitle?.let { title ->
                Text("🛒 $title", fontSize = 11.sp, color = ScottsTechXColors.PurpleAccent)
            }
        }
    }
}

/** Buyer messages inbox. */
@Composable
fun MessagesScreen(onThreadClick: (String) -> Unit, onBack: () -> Unit = {}) {
    ConversationListScreen(title = "Messages", onBack = onBack, onThreadClick = onThreadClick)
}
