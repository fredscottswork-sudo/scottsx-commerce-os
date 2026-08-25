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
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Stage 5.x — Notifications inbox.
 *
 * Today the screen renders a curated set of marketplace events seeded
 * locally (price drops, restocks, deal of the day, etc.). The data
 * source can be swapped for `V2Client.fetchNotifications()` once the
 * backend endpoint is wired.
 *
 * Each notification has:
 *  - icon (price / restock / announcement)
 *  - title + body
 *  - timestamp
 *  - mark-as-read toggle on tap
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
        val kind: String,
        val time: String,
        var read: Boolean = false,
    )

    val seeded = remember {
        listOf(
            NotificationItem(
                "n1", "Flash deal starts in 1 hour",
                "Your wishlist items have new discounts. Tap to see.",
                "deal", "now",
            ),
            NotificationItem(
                "n2", "Price drop on Wireless Earbuds",
                "Now UGX 95,000 (was UGX 120,000). Limited stock.",
                "price", "2h ago",
            ),
            NotificationItem(
                "n3", "Order delivered",
                "Your order #ORD-4412 was marked delivered by the courier.",
                "order", "yesterday",
            ),
            NotificationItem(
                "n4", "Welcome to ScottsTechX",
                "Browse, buy, and chat with sellers from anywhere in Uganda.",
                "system", "3d ago", read = true,
            ),
        )
    }
    var items by remember { mutableStateOf(seeded) }
    val unreadCount = items.count { !it.read }

    Column(modifier = modifier.fillMaxSize().background(ScottsTechXColors.BackgroundLight)) {
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
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Mark all read", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
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
                            if (item.kind in setOf("deal", "price")) {
                                onOpenProduct(item.id)
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
