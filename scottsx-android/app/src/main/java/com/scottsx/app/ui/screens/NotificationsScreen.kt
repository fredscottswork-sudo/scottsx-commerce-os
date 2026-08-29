package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.components.statusBarSpacer

/**
 * Notifications inbox — fully live. Renders the caller's real rows
 * from `GET /api/v1/me/notifications` (order updates, messages,
 * marketing) with server-side read state; mark-as-read persists
 * through the API so web and app stay in sync. The deep-link payload
 * (`data.screen`/`data.id`) routes taps to the right screen.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenProduct: (productId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    data class NotificationItem(
        val id: String,
        val title: String,
        val body: String,
        val kind: String,          // deal | order | chat | system — derived from the server type
        val time: String,
        var read: Boolean = false,
        val screen: String? = null,
        val targetId: String? = null,
    )

    var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        isLoading = true
        loadError = null
        val arr = try { com.scottsx.app.data.remote.V2Client.fetchNotifications() } catch (t: Throwable) {
            loadError = "Couldn't load notifications: ${t.message ?: "unknown error"}"; null
        }
        if (arr != null) {
            items = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    val type = o.optString("type")
                    val data = o.optJSONObject("data")
                    NotificationItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        body = o.optString("body"),
                        kind = when {
                            type.contains("order") -> "order"
                            type.contains("message") || type.contains("chat") -> "chat"
                            type.contains("market") || type.contains("deal") || type.contains("price") -> "deal"
                            else -> "system"
                        },
                        time = o.optString("createdAt").replace('T', ' ').take(16),
                        read = o.optBoolean("read", false),
                        screen = data?.optString("screen")?.takeIf { it.isNotBlank() },
                        targetId = data?.optString("id")?.takeIf { it.isNotBlank() },
                    )
                }
            }
        } else if (loadError == null) {
            loadError = "Couldn't load notifications — check your connection."
        }
        isLoading = false
    }

    val unreadCount = items.count { !it.read }

    Column(modifier = modifier.fillMaxSize().background(ScottsTechXColors.BackgroundLight).statusBarSpacer()) {
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
                    Text("Notifications",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(if (unreadCount > 0) "$unreadCount unread" else "All caught up",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
                if (items.any { !it.read }) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable {
                                items = items.map { it.copy(read = true) }
                                scope.launch {
                                    try { com.scottsx.app.data.remote.V2Client.markAllNotificationsRead() } catch (_: Throwable) { }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Mark all read", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = ScottsTechXColors.BluePrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
            return@Column
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    loadError ?: "No notifications yet — order updates and messages will appear here.",
                    color = ScottsTechXColors.OnPanelSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
            items(items, key = { it.id }) { item ->
                val icon = when (item.kind) {
                    "deal" -> Icons.Filled.LocalOffer
                    "price" -> Icons.Filled.LocalOffer
                    "order" -> Icons.Filled.ShoppingBag
                    else -> Icons.Filled.Campaign
                }
                val accent = when (item.kind) {
                    "deal" -> Color(0xFFFB7185)
                    "price" -> Color(0xFF22C55E)
                    "order" -> ScottsTechXColors.BluePrimary
                    else -> Color(0xFF6B7280)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            items = items.map {
                                if (it.id == item.id) it.copy(read = true) else it
                            }
                            scope.launch {
                                try { com.scottsx.app.data.remote.V2Client.markNotificationRead(item.id) } catch (_: Throwable) { }
                            }
                            // Deep link: the backend carries {screen, id}
                            // payloads — product taps open the PDP.
                            if (item.screen == "product" && item.targetId != null) {
                                onOpenProduct(item.targetId)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = accent,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.title,
                                color = ScottsTechXColors.OnLight,
                                fontWeight = if (item.read) FontWeight.Medium else FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!item.read) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ScottsTechXColors.BluePrimary),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.body,
                            color = ScottsTechXColors.OnLightSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.time,
                            color = ScottsTechXColors.OnLightSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
