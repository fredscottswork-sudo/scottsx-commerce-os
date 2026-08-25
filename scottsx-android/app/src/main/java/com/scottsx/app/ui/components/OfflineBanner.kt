package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.ConnectionWatcher
import com.scottsx.app.ui.theme.ScottsTechXColors

/** Dark amber for the label — WarningAmber on white would not meet contrast. */
private val OfflineText = Color(0xFF8A4B08)

/**
 * Slim amber strip shown atop a network-driven screen while the device has
 * no validated internet connection.
 *
 * Draws nothing while online, so layouts do not shift. This is the
 * "Offline" state of the production contract: a lost connection is surfaced
 * explicitly instead of being hidden behind a stale or empty list.
 */
@Composable
fun OfflineBanner() {
    val connected by ConnectionWatcher.isConnected.collectAsState()
    if (connected) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScottsTechXColors.WarningAmber.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = OfflineText,
            modifier = Modifier.size(14.dp),
        )
        Text(
            "Offline — check your connection",
            color = OfflineText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
