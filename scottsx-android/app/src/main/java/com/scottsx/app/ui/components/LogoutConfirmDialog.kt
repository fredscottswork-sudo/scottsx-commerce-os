package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Confirmation dialog shown when the user taps "Log Out" in the
 * buyer sidebar. The exact copy is mandated by the brief:
 *
 *   "Log out of ScottsTechX?"
 *   "You will need to sign in again to access your account."
 *
 * Buttons: Cancel | Log Out. Only [onConfirm] (the Log Out button)
 * actually signs the user out; [onCancel] just dismisses the dialog.
 */
@Composable
fun LogoutConfirmDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Theme-aware destructive palette (light-only hard-codes read as a
    // broken white patch on the dark sheet otherwise).
    val light = ScottsTechXColors.isLightPalette
    val destructiveBg = if (light) Color(0xFFFEE2E2) else Color(0xFFEF4444).copy(alpha = 0.18f)
    val destructiveBg2 = if (light) Color(0xFFFECACA) else Color(0xFFEF4444).copy(alpha = 0.26f)
    val destructiveFg = if (light) Color(0xFFB91C1C) else Color(0xFFFCA5A5)
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(destructiveBg)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    tint = destructiveFg,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .height(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onConfirm) {
                    Text(
                        text = "Log Out",
                        color = destructiveFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    text = "Cancel",
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Logout,
                contentDescription = null,
                tint = destructiveFg,
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                destructiveBg2,
                                destructiveBg,
                            ),
                        ),
                    )
                    .padding(8.dp),
            )
        },
        title = {
            Text(
                text = "Log out of ScottsTechX?",
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Text(
                text = "You will need to sign in again to access your account.",
                fontSize = 14.sp,
                color = ScottsTechXColors.OnCardSecondary,
            )
        },
        containerColor = ScottsTechXColors.CardSurface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
    )
}
