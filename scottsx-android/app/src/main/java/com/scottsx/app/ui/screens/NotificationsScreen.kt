package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.AppNotification
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Notifications inbox with mark-read. */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            notifications = V2Client.fetchNotifications()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Notifications", onBack = onBack)
        if (loading) {
            LoadingRow()
        } else if (notifications.isEmpty()) {
            EmptyState("🔔", "No notifications yet", "Order updates and messages will appear here.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(notifications, key = { it.id }) { notification ->
                    Surface(
                        color = if (notification.read) MaterialTheme.colorScheme.surface else ScottsTechXColors.BluePrimary.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable {
                                if (!notification.read) {
                                    scope.launch {
                                        V2Client.markNotificationRead(notification.id)
                                        reload()
                                    }
                                }
                            },
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = if (notification.read) MaterialTheme.colorScheme.surfaceVariant else ScottsTechXColors.BluePrimary,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (notification.read) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        notification.title,
                                        fontWeight = if (notification.read) FontWeight.Normal else FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!notification.read) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(ScottsTechXColors.BluePrimary, CircleShape),
                                        )
                                    }
                                }
                                Text(notification.body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    notification.createdAt.substringAfter("T").substring(0, 5),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
