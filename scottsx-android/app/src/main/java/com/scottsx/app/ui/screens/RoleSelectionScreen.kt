package com.scottsx.app.ui.screens

import com.scottsx.app.data.domain.Role

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.components.UgandaMapBackground
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Role selection screen — appears after onboarding. The user picks
 * Buyer / Seller and whether they want to log in or sign up.
 *
 *   ┌─ I am a Buyer  ───────────────┐    ┌─ I am a Seller ──────────────┐
 *   │  Discover products...          │    │  List products, grow...      │
 *   │  [Log in]   [Sign up]         │    │  [Log in]   [Sign up]        │
 *   └───────────────────────────────┘    └──────────────────────────────┘
 *
 * The user can switch the active role by tapping either card; the
 * active card glows in the brand blue, the inactive card is dimmed.
 */
@Composable
fun RoleSelectionScreen(
    onLogin: (Role) -> Unit,
    onSignUp: (Role) -> Unit,
) {
    var selected by remember { mutableStateOf(Role.BUYER) }

    Box(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.BackgroundDark)) {
        UgandaMapBackground()

        // Dark overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x00000000),
                            Color(0x00000000),
                            Color(0xCC050711),
                            Color(0xEE050711),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "WELCOME",
                color = ScottsTechXColors.BluePrimaryLight,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 4.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "How will you use ScottsTechX?",
                color = ScottsTechXColors.OnDark,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                lineHeight = 34.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pick the role that fits you best — you can always switch later.",
                color = ScottsTechXColors.OnDarkSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            RoleCard(
                role = Role.BUYER,
                selected = selected == Role.BUYER,
                onSelect = { selected = Role.BUYER },
                onLogin = { onLogin(Role.BUYER) },
                onSignUp = { onSignUp(Role.BUYER) },
            )

            Spacer(modifier = Modifier.height(14.dp))

            RoleCard(
                role = Role.SELLER,
                selected = selected == Role.SELLER,
                onSelect = { selected = Role.SELLER },
                onLogin = { onLogin(Role.SELLER) },
                onSignUp = { onSignUp(Role.SELLER) },
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "By continuing you agree to ScottsTechX's Terms of Service and Privacy Policy.",
                color = ScottsTechXColors.OnDarkMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RoleCard(
    role: Role,
    selected: Boolean,
    onSelect: () -> Unit,
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
) {
    val fillBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xCC1E40AF),
                Color(0xCC3B82F6),
                Color(0xCC1E3A8A),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x66121329),
                Color(0x661A2540),
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush = fillBrush)
            .clickable { onSelect() }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Role badge — circle with initial.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) Color.White else Color(0x551E3A8A),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = role.displayName.first().toString(),
                    color = if (selected) ScottsTechXColors.BluePrimaryDark else ScottsTechXColors.OnDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "I am a " + role.displayName,
                    color = ScottsTechXColors.OnDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
                Text(
                    text = role.tagline,
                    color = ScottsTechXColors.OnDarkSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        if (selected) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton(
                    text = "Log in",
                    primary = false,
                    onClick = onLogin,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = "Sign up",
                    primary = true,
                    onClick = onSignUp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (primary) Color.White else Color(0x00000000)
    val fg = if (primary) ScottsTechXColors.BluePrimaryDark else ScottsTechXColors.OnDark
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 1.sp,
        )
    }
}