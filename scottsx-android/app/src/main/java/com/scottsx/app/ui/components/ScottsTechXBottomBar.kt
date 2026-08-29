package com.scottsx.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * ScottsTechX signature bottom navigation.
 *
 * Floating translucent blue glass pill that hovers above the
 * content. Each tab is an icon + label; the active tab gets a
 * strong blue glow, an animated background pill, and a
 * blue→light gradient highlight.
 */
@Composable
fun ScottsTechXBottomBar(
    selected: BottomTab?,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Soft blue glow underneath the bar (ambient light)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ScottsTechXColors.BlueGlow,
                            Color.Transparent,
                        ),
                        radius = 600f,
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xCC1E40AF),
                            Color(0xCC1E3A8A),
                            Color(0xCC312E81),
                        ),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cartItems by com.scottsx.app.data.CartStore.items.collectAsState()
            val cartCount = cartItems.sumOf { it.quantity }
            // Live message / notification counts — 30s poll while the bar is
            // composed; guests simply keep zero badges (web parity).
            var chatsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
            var alertsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    chatsCount = try {
                        com.scottsx.app.data.remote.V2Client.fetchConversationUnreadCount()
                    } catch (_: Throwable) { null } ?: chatsCount
                    alertsCount = try {
                        com.scottsx.app.data.remote.V2Client.fetchUnreadNotificationCount()
                    } catch (_: Throwable) { null } ?: alertsCount
                    kotlinx.coroutines.delay(30_000)
                }
            }
            BottomTab.values().forEach { tab ->
                NavItem(
                    tab = tab,
                    selected = tab == selected,
                    badgeCount = when (tab) {
                        BottomTab.Cart -> cartCount
                        BottomTab.Chats -> chatsCount
                        BottomTab.Alerts -> alertsCount
                        else -> 0
                    },
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

enum class BottomTab(val label: String, val icon: ImageVector) {
    // Parity with the web's mobile bottom nav (web/src/components/MainNav.tsx):
    // Home / Nearby / AI / Cart / Chats / Alerts — the three badged destinations
    // carry live counts exactly like the web's icon-badge. The AI tab is the
    // one-tap assistant entry point the web has; the tab reads "Nearby" (the
    // old "Explore" label never matched the route it opens).
    Home("Home", Icons.Filled.Home),
    Nearby("Nearby", Icons.Filled.LocationOn),
    Ai("AI", Icons.Filled.SmartToy),
    Cart("Cart", Icons.Filled.ShoppingCart),
    Chats("Chats", Icons.Filled.ChatBubble),
    Alerts("Alerts", Icons.Filled.Notifications),
}

@Composable
private fun NavItem(
    tab: BottomTab,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
) {
    val activeColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.55f)
    val iconColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(300),
        label = "nav-icon-color",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(300),
        label = "nav-label-color",
    )
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.85f,
        animationSpec = tween(300),
        label = "nav-pill-scale",
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(300),
        label = "nav-pill-alpha",
    )

    Box(
        modifier = Modifier
            .height(52.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Animated active pill (behind the icon+label)
        Box(
            modifier = Modifier
                .size(width = 70.dp * pillScale, height = 44.dp * pillScale)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ScottsTechXColors.BluePrimaryLight.copy(alpha = pillAlpha),
                            ScottsTechXColors.BluePrimary.copy(alpha = pillAlpha),
                        ),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp),
                )
                if (badgeCount > 0) {
                    // Live cart dot — same count the web header shows.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-4).dp)
                            .height(15.dp)
                            .width(if (badgeCount > 9) 22.dp else 15.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = tab.label,
                color = labelColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 9.sp,
            )
        }
    }
}