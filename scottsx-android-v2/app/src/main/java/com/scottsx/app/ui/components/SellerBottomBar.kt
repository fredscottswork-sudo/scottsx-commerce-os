package com.scottsx.app.ui.components

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Seller bottom nav — 5 items with a prominent center Add button.
 *
 * Order from the brief:
 *   Home / Orders / [Add] / Messages / Analytics
 *
 * The center Add button is a larger pill with the brand gradient and
 * floats above the bar so it pulls the eye. The bar itself mirrors
 * the buyer [ScottsTechXBottomBar] styling for visual consistency.
 */
@Composable
fun SellerBottomBar(
    selected: SellerBottomTab,
    onSelect: (SellerBottomTab) -> Unit,
    onAddClicked: () -> Unit,
    onAiClicked: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Frosted-blue glass bar.
        Box(
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
                .padding(horizontal = 8.dp),
        ) {
            // 4 nav items split into 2 groups (left of FAB / right of FAB)
            // so the layout is balanced: Home | AI  [FAB]  Messages | Analytics
            // Each side has equal weight to the right and left, so the FAB
            // is visually centered and the buttons don't crowd it.
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left half: Home + AI
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().height(64.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SellerNavItem(
                        tab = SellerBottomTab.Home,
                        selected = selected == SellerBottomTab.Home,
                        onClick = { onSelect(SellerBottomTab.Home) },
                    )
                    SellerNavItem(
                        tab = SellerBottomTab.AI,
                        selected = selected == SellerBottomTab.AI,
                        onClick = { onSelect(SellerBottomTab.AI) },
                    )
                }
                // Center FAB spacer (same width as the Add button)
                Spacer(Modifier.width(72.dp))
                // Right half: Messages + Analytics
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().height(64.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SellerNavItem(
                        tab = SellerBottomTab.Messages,
                        selected = selected == SellerBottomTab.Messages,
                        onClick = { onSelect(SellerBottomTab.Messages) },
                    )
                    SellerNavItem(
                        tab = SellerBottomTab.Analytics,
                        selected = selected == SellerBottomTab.Analytics,
                        onClick = { onSelect(SellerBottomTab.Analytics) },
                    )
                }
            }
        }

        // Center Add FAB — sits above the bar, larger pill.
        val pressed by animateFloatAsState(
            targetValue = if (selected == SellerBottomTab.Add) 1.04f else 1f,
            animationSpec = tween(160),
            label = "add-fab-scale",
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
                .graphicsLayer { scaleX = pressed; scaleY = pressed }
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ScottsTechXColors.BluePrimaryLight,
                            ScottsTechXColors.BluePrimary,
                            ScottsTechXColors.BluePrimaryDark,
                        ),
                    ),
                )
                .clickable(onClick = onAddClicked),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add Product",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SellerNavItem(
    tab: SellerBottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val targetScale = if (selected) 1.0f else 0.85f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(180),
        label = "seller-nav-scale",
    )
    val tint = if (selected) Color.White else Color.White.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tab.label,
                color = tint,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
            )
        }
    }
}

/** The five seller tabs. The Add tab is reserved for the FAB. */
enum class SellerBottomTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Add("Add", Icons.Filled.AddCircle),
    AI("AI Assistant", Icons.Filled.AutoAwesome),
    Messages("Messages", Icons.Filled.ChatBubble),
    Analytics("Analytics", Icons.Filled.Analytics),
}
