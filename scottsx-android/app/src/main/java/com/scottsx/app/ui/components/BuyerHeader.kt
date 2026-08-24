package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Buyer personalized header — avatar + welcome text on the left,
 * floating glass notification + cart buttons on the right.
 * Per brief: "Notification badge: Show the number of unread
 * notifications. Cart badge: Show the number of products/items
 * currently in the cart. If the number is zero, hide the
 * badge."
 */
@Composable
fun BuyerHeader(
    displayName: String,
    email: String,
    notificationCount: Int,
    cartCount: Int,
    onNotificationsClick: () -> Unit,
    onCartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar with initial
            Box(
                modifier = Modifier
                    .size(48.dp)
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
                    text = (displayName.firstOrNull()?.uppercase() ?: "U").toString(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Welcome back,",
                    color = ScottsTechXColors.OnDarkSecondary,
                    fontSize = 12.sp,
                )
                Text(
                    text = "@${displayName.take(16)}",
                    color = ScottsTechXColors.OnDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "Shop smart. Support local.",
                    color = ScottsTechXColors.BluePrimaryLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingIconButton(
                icon = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                badge = notificationCount,
                onClick = onNotificationsClick,
            )
            FloatingIconButton(
                icon = Icons.Filled.ShoppingCart,
                contentDescription = "Cart",
                badge = cartCount,
                onClick = onCartClick,
            )
        }
    }
}

@Composable
private fun FloatingIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    badge: Int,
    onClick: () -> Unit,
) {
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = ScottsTechXColors.OnLight,
                // Note: should be dark icon on glass — ScottsTechXColors.OnLight
                // is dark in our light theme. Use the dark color explicitly.
                modifier = Modifier.size(20.dp),
            )
        }
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE11D48))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badge > 9) "9+" else badge.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                )
            }
        }
    }
}
