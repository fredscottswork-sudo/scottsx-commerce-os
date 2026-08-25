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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    selected: BottomTab,
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
            BottomTab.values().forEach { tab ->
                NavItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

enum class BottomTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Nearby("Explore", Icons.Filled.LocationOn),
    Ai("AI", Icons.Filled.AutoAwesome),
    Wishlist("Cart", Icons.Filled.Favorite),
    Profile("Account", Icons.Filled.Person),
}

@Composable
private fun NavItem(
    tab: BottomTab,
    selected: Boolean,
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
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
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