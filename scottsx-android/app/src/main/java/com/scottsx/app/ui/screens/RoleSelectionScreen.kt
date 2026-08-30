package com.scottsx.app.ui.screens

import com.scottsx.app.data.domain.Role

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.UgandaMapBackground
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Role selection — appears after onboarding.
 *
 * Interaction model (rewritten — the old cards nested Log in / Sign
 * up buttons INSIDE a clickable card, and nested clickables made the
 * selector feel dead/mis-firing on real devices): cards do ONE thing —
 * select. The chosen card gets the blue glow, a border and a check
 * badge. Two clean action buttons at the bottom act on the selected
 * role. Nothing is a clickable-inside-a-clickable anymore.
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

            SelectableRoleCard(
                role = Role.BUYER,
                selected = selected == Role.BUYER,
                onSelect = { selected = Role.BUYER },
            )

            Spacer(modifier = Modifier.height(14.dp))

            SelectableRoleCard(
                role = Role.SELLER,
                selected = selected == Role.SELLER,
                onSelect = { selected = Role.SELLER },
            )

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Sign up as ${selected.displayName}",
                onClick = { onSignUp(selected) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, ScottsTechXColors.BlueGlow.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    .clickable { onLogin(selected) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "I already have an account — Log in",
                    color = ScottsTechXColors.OnDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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
private fun SelectableRoleCard(
    role: Role,
    selected: Boolean,
    onSelect: () -> Unit,
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
    val borderColor = if (selected) {
        ScottsTechXColors.BlueGlow
    } else {
        Color(0x33FFFFFF)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush = fillBrush)
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onSelect() }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
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
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = ScottsTechXColors.BluePrimaryDark,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
