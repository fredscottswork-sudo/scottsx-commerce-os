package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.secondsToHms
import kotlinx.coroutines.delay

/**
 * Functional countdown timer for Flash Deals. Counts down from
 * [initialSeconds] one tick per second. The brief explicitly
 * requires this — "Do not fake the countdown by displaying
 * static numbers."
 */
@Composable
fun CountdownTimer(
    initialSeconds: Int,
    modifier: Modifier = Modifier,
) {
    var secondsLeft by remember { mutableIntStateOf(initialSeconds) }
    LaunchedEffect(initialSeconds) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = Color(0xFFFBBF24),
            modifier = Modifier.size(14.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        Text(
            text = secondsToHms(secondsLeft),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
        )
    }
}
