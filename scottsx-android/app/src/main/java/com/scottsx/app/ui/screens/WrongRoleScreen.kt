package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Role
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Shown when a user authenticates with a role that doesn't match the
 * one they tapped at the role-selection screen.
 *
 * Example: user taps "Sign in as Buyer" on the role picker, then types
 * `[email protected]` which is on Firestore as a Seller. Firebase Auth signs them in,
 * Firestore role lookup returns Seller, the AuthRepository hands us a
 * `RoleMismatch(actual = Seller)`, the LoginScreen's `onRoleMismatch`
 * callback navigates here with the actual server role.
 *
 * From here the user has two options:
 *  1. Tap "Continue as <actual role>" — routes them to the dashboard that
 *     matches the actual server role, with the seller email allowed in.
 *  2. Tap "Use a different account" — pops the back stack so they can
 *     pick another email / Google account.
 *
 * The header is the brand blue gradient so the screen still feels
 * ScottsTechX even though it's an off-flow edge case.
 */
@Composable
fun WrongRoleScreen(
    actualRole: Role,
    pickedRole: Role,
    onContinueAsActual: () -> Unit,
    onUseDifferentAccount: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.PanelLight),
    ) {
        // Header — back button + brand banner.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            ScottsTechXColors.BluePrimaryDark,
                            ScottsTechXColors.BluePrimary,
                        ),
                    ),
                )
                .padding(top = 36.dp, start = 12.dp, end = 16.dp, bottom = 28.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(8.dp)
                        .clickable { onUseDifferentAccount() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(0.dp))
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 12.dp))
                Text(
                    text = "Wrong account type",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                )
            }
        }

        // Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                ScottsTechXColors.BluePrimary,
                                ScottsTechXColors.BluePrimaryLight,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "This account is registered as a ${actualRole.displayName}, not a ${pickedRole.displayName}",
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "You're signed in to ScottsTechX, but the role on the server doesn't match what you picked. You can continue to the ${actualRole.displayName} dashboard with this account, or sign back out and pick a different email.",
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(36.dp))

            PrimaryButton(
                text = "Continue as ${actualRole.displayName}",
                onClick = onContinueAsActual,
            )

            Spacer(Modifier.height(12.dp))

            // Use a different account — same call, framed as a CTA.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(ScottsTechXColors.PanelInputLight)
                    .clickable { onUseDifferentAccount() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Use a different account",
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

