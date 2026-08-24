package com.scottsx.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Three-dot/page indicator. Active dot is wider + dark blue; the
 * other two are smaller and dim. The brief disallows a progress
 * bar — this is a row of three dots.
 */
@Composable
fun PageIndicator(
    activeIndex: Int,
    size: Int = 3,
    modifier: Modifier = Modifier,
    activeColor: Color = ScottsTechXColors.BluePrimary,
    inactiveColor: Color = ScottsTechXColors.OnDarkMuted,
    activeWidth: Dp = 28.dp,
    dotSize: Dp = 8.dp,
    height: Dp = 8.dp,
) {
    Row(modifier = modifier) {
        repeat(size) { idx ->
            val isActive = idx == activeIndex
            val width by animateFloatAsState(
                targetValue = if (isActive) activeWidth.value else dotSize.value,
                animationSpec = tween(durationMillis = 220),
                label = "indicator-width",
            )
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(width.dp)
                    .height(height)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) activeColor else inactiveColor),
            )
        }
    }
}
