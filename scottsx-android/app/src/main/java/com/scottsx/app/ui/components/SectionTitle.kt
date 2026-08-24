package com.scottsx.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Section title row used everywhere on the buyer home
 * ("Flash Deals", "Recommended for you", etc.) with an optional
 * leading icon and a "View All >" affordance on the right.
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    viewAll: String? = null,
    onViewAll: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
            )
        }
        if (viewAll != null) {
            Text(
                text = viewAll,
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .clickable { onViewAll() },
            )
        }
    }
}