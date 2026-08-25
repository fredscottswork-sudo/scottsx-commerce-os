package com.scottsx.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Spa
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
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Horizontally-scrolling category chip row used by the buyer
 * dashboard. "All" is shown selected by default; tapping a chip
 * updates the selection state in the parent.
 */
@Composable
fun CategoryRow(
    selected: ProductCategory,
    onSelect: (ProductCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = listOf(
        ProductCategory.All,
        ProductCategory.Electronics,
        ProductCategory.Fashion,
        ProductCategory.HomeLiving,
        ProductCategory.Beauty,
        ProductCategory.Sports,
        ProductCategory.Groceries,
        ProductCategory.Automotive,
        ProductCategory.More,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ordered.forEach { cat ->
            CategoryChip(
                category = cat,
                selected = cat == selected,
                onClick = { onSelect(cat) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: ProductCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor by animateColorAsState(
        targetValue = if (selected) ScottsTechXColors.BluePrimary else Color(0xFFE5E7EB),
        label = "chip-ring",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) ScottsTechXColors.BluePrimary else ScottsTechXColors.OnLightSecondary,
        label = "chip-label",
    )
    val iconBgBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFF1F3F7), Color(0xFFF8FAFC)),
        )
    }
    val iconTint = if (selected) Color.White else ScottsTechXColors.OnLightSecondary

    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(iconBgBrush)
                .padding(if (selected) 1.dp else 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIconFor(category),
                contentDescription = category.displayName,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = category.displayName,
            color = labelColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

private fun categoryIconFor(c: ProductCategory): ImageVector = when (c) {
    ProductCategory.All -> Icons.Filled.ExpandMore        // used as 'all' glyph
    ProductCategory.Electronics -> Icons.Filled.Devices
    ProductCategory.Fashion -> Icons.Filled.Checkroom
    ProductCategory.HomeLiving -> Icons.Filled.Home
    ProductCategory.Beauty -> Icons.Filled.Spa
    ProductCategory.Sports -> Icons.Filled.DirectionsRun
    ProductCategory.Groceries -> Icons.Filled.LocalGroceryStore
    ProductCategory.Automotive -> Icons.Filled.DirectionsCar
    ProductCategory.More -> Icons.Filled.Brush
}

// Local helper — Modifier.horizontalScroll without pulling in foundation scroll