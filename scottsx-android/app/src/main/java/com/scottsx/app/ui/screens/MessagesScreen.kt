package com.scottsx.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.domain.Conversation
import com.scottsx.app.data.domain.InboxCounts
import com.scottsx.app.data.remote.MessageStream
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.ListDivider
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Inbox filters, mirroring the web app and the API's `?filter=` values. */
private val INBOX_FILTERS = listOf(
    "all" to "All",
    "unread" to "Unread",
    "offers" to "Offers",
    "pinned" to "Pinned",
    "archived" to "Archived",
)

/**
 * Renders an ISO timestamp the way a chat app should: time today, "Yesterday",
 * weekday within the last week, otherwise a short date.
 *
 * Written defensively — the previous version did
 * `lastTime.substringAfter("T").substring(0, 5)`, which threw
 * StringIndexOutOfBoundsException on a brand-new thread whose lastTime is "".
 */
internal fun chatTimeLabel(iso: String): String {
    if (iso.isBlank()) return ""
    val timePart = iso.substringAfter('T', "")
    val hhmm = if (timePart.length >= 5) timePart.take(5) else ""
    val datePart = iso.substringBefore('T', "")
    if (datePart.length < 10) return hhmm

    return try {
        val today = java.time.LocalDate.now()
        val then = java.time.LocalDate.parse(datePart)
        val days = java.time.temporal.ChronoUnit.DAYS.between(then, today)
        when {
            days == 0L -> hhmm
            days == 1L -> "Yesterday"
            days in 2..6 -> then.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            )
            else -> "${then.dayOfMonth} ${then.month.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            )}"
        }
    } catch (e: Exception) {
        hhmm
    }
}

/** Shared inbox UI used by both buyer and seller message lists. */
@Composable
fun ConversationListScreen(
    title: String,
    onBack: () -> Unit,
    onThreadClick: (String) -> Unit,
) {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var counts by remember { mutableStateOf(InboxCounts()) }
    var filter by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val myId = SessionCache.user.value?.id ?: ""

    suspend fun refresh() {
        val inbox = V2Client.fetchInbox(filter, search)
        conversations = inbox.conversations
        counts = inbox.counts
        loading = false
    }

    // Reload whenever the filter or the search term changes.
    LaunchedEffect(filter, search) {
        refresh()
    }

    // Keep the list live without a websocket.
    LaunchedEffect(Unit) {
        MessageStream.ticker(10_000).collect { refresh() }
    }

    fun mutate(conversation: Conversation, pinned: Boolean? = null, archived: Boolean? = null) {
        scope.launch {
            V2Client.setConversationState(conversation.id, pinned = pinned, archived = archived)
            refresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent),
                    ),
                    RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                )
                .padding(14.dp),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            .size(32.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (counts.unread > 0) {
                            Text(
                                "${counts.unread} unread",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search conversations",
                        tint = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (searchOpen) 0.28f else 0.15f))
                            .clickable {
                                searchOpen = !searchOpen
                                if (!searchOpen) search = ""
                            }
                            .padding(6.dp)
                            .size(30.dp),
                    )
                    Icon(
                        Icons.Filled.ChatBubble,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }

                AnimatedVisibility(visible = searchOpen) {
                    TextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search people, products or messages…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.16f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.16f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    )
                }
            }
        }

        // ── Filter chips with counts ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            INBOX_FILTERS.forEach { (key, label) ->
                val n = when (key) {
                    "unread" -> counts.unread
                    "offers" -> counts.offers
                    "pinned" -> counts.pinned
                    "archived" -> counts.archived
                    else -> counts.all
                }
                FilterChip(label = label, count = n, selected = filter == key) { filter = key }
            }
        }

        // ── List ────────────────────────────────────────────────────────────
        if (loading) {
            LoadingRow()
        } else if (conversations.isEmpty()) {
            val (emptyTitle, emptyBody) = when (filter) {
                "unread" -> "Nothing unread" to "You are all caught up."
                "offers" -> "No open offers" to "Price offers awaiting a reply appear here."
                "pinned" -> "No pinned chats" to "Pin important conversations to keep them on top."
                "archived" -> "Archive is empty" to "Archived chats return when a new message arrives."
                else -> "No conversations yet" to "Message a seller from any product page to start chatting."
            }
            EmptyState("💬", emptyTitle, emptyBody)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        isMine = conversation.lastSenderId == myId,
                        onClick = { onThreadClick(conversation.id) },
                        onTogglePin = { mutate(conversation, pinned = !conversation.pinned) },
                        onToggleArchive = { mutate(conversation, archived = !conversation.archived) },
                    )
                    ListDivider()
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) ScottsTechXColors.BluePrimary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) Color.White.copy(alpha = 0.26f)
                        else ScottsTechXColors.BluePrimary.copy(alpha = 0.16f),
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    "$count",
                    color = if (selected) Color.White else ScottsTechXColors.BluePrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    isMine: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (conversation.unread > 0) {
                    ScottsTechXColors.BluePrimary.copy(alpha = 0.06f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Brush.linearGradient(
                        listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent),
                    ),
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
                if (conversation.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = ScottsTechXColors.BluePrimary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    conversation.otherParty.name,
                    fontWeight = if (conversation.unread > 0) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
                if (conversation.otherParty.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = "Verified",
                        tint = ScottsTechXColors.CyanAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    chatTimeLabel(conversation.lastTime),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.muted) {
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = "Muted",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = (if (isMine) "You: " else "") +
                        conversation.lastMessage.ifBlank { "No messages yet" },
                    fontSize = 13.sp,
                    color = if (conversation.unread > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.pendingOffers > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(ScottsTechXColors.WarningAmber.copy(alpha = 0.20f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "💰 ${conversation.pendingOffers}",
                            color = ScottsTechXColors.WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (conversation.unread > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(ScottsTechXColors.BluePrimary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${conversation.unread}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            conversation.productTitle?.let { productTitle ->
                Text(
                    "🛒 $productTitle",
                    fontSize = 11.sp,
                    color = ScottsTechXColors.PurpleAccent,
                    maxLines = 1,
                )
            }
        }

        // Inline pin / archive actions.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = if (conversation.pinned) "Unpin" else "Pin to top",
                tint = if (conversation.pinned) {
                    ScottsTechXColors.BluePrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePin)
                    .padding(6.dp)
                    .size(18.dp),
            )
            Icon(
                if (conversation.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                contentDescription = if (conversation.archived) "Move to inbox" else "Archive",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleArchive)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}

/** Buyer messages inbox. */
@Composable
fun MessagesScreen(onThreadClick: (String) -> Unit, onBack: () -> Unit = {}) {
    ConversationListScreen(title = "Messages", onBack = onBack, onThreadClick = onThreadClick)
}
