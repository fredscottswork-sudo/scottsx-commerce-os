package com.scottsx.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors

/** Seller bottom tabs. "Add" is reserved for the centre FAB and is NOT rendered as a nav item. */
enum class SellerBottomTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Add("Add", Icons.Filled.AddCircle),
    AI("AI Assistant", Icons.Filled.AutoAwesome),
    Messages("Messages", Icons.Filled.ChatBubble),
    Analytics("Analytics", Icons.Filled.Analytics),
}

/**
 * THE BALANCED LAYOUT (v0.22.1):
 *
 *   Row split into a left half + 72dp centre spacer + right half:
 *     [Home | AI]      [Add FAB]      [Messages | Analytics]
 *
 * Each half is a Row with SpaceEvenly and equal weight, so the FAB sits
 * visually centred and the AI button is never crowded next to it.
 */
@Composable
fun SellerBottomBar(
    selected: SellerBottomTab,
    onTabSelected: (SellerBottomTab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leftTabs = listOf(SellerBottomTab.Home, SellerBottomTab.AI)
    val rightTabs = listOf(SellerBottomTab.Messages, SellerBottomTab.Analytics)

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leftTabs.forEach { tab -> NavItem(tab, selected == tab) { onTabSelected(tab) } }
                }

                // Reserve room for the FAB so the two halves stay balanced.
                Spacer(Modifier.width(72.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rightTabs.forEach { tab -> NavItem(tab, selected == tab) { onTabSelected(tab) } }
                }
            }
        }

        // Centre Add FAB — 64dp circle, gradient, scale animation on press.
        AddFab(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp),
        )
    }
}

@Composable
private fun NavItem(tab: SellerBottomTab, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = if (isSelected) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            tab.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(120),
        label = "fabScale",
    )

    val gradient = Brush.linearGradient(
        listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .scale(scale)
            .background(gradient, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add product",
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}
