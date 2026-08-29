package com.scottsx.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.data.domain.Role
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Shared auth backdrop for the Google-only Login + Sign-Up pages.
 *
 * Design language (identical to the web auth brand panel):
 *  - deep navy → brand blue gradient with two soft animated glow orbs
 *  - the transparent brand lockup up top
 *  - a floating white content card (rounded 28dp) centred in the lower
 *    two-thirds of the screen, sliding up + fading in on entry
 *  - a role badge pill ("Buying" / "Selling") so the user always knows
 *    which account lane they're in
 *
 * Every colour in this file is a fixed value — invisible-text bugs from
 * missing theme tokens are impossible by construction.
 */
@Composable
fun GoogleOnlyAuthLayout(
    role: Role,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val slideIn = remember { Animatable(60f) }
    val fadeIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { slideIn.animateTo(0f, tween(520, easing = EaseOutCubic)) }
        launch { fadeIn.animateTo(1f, tween(520, easing = EaseOutCubic)) }
    }

    val orbFloat by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(6000, easing = EaseOutCubic),
            RepeatMode.Reverse,
        ),
        label = "orb-float",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0B1B3A),
                    0.45f to ScottsTechXColors.BluePrimaryDark,
                    1f to ScottsTechXColors.BluePrimary,
                ),
            )
            .systemBarsPadding(),
    ) {
        // Glow orb — top right
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-60 + orbFloat * 24).dp)
                .clip(CircleShape)
                .background(Color(0xFF60A5FA).copy(alpha = 0.18f)),
        )
        // Glow orb — bottom left
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = (60 - orbFloat * 20).dp)
                .clip(CircleShape)
                .background(Color(0xFF93C5FD).copy(alpha = 0.12f)),
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopStart),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        // Role badge pill — top right
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (role == Role.SELLER) "Selling" else "Buying",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
        }

        // Brand lockup — upper third
        Image(
            painter = painterResource(R.drawable.brand_lockup),
            contentDescription = "ScottsTechX",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
                .fillMaxWidth()
                .padding(horizontal = 72.dp),
        )

        // White content card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = slideIn.value.dp.toPx()
                    alpha = fadeIn.value
                }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            content()
        }
    }
}

@Composable
fun GoogleOnlyAuthHeader(
    title: String,
    sub: String,
) {
    Column {
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            letterSpacing = (-0.5).sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sub,
            fontSize = 14.sp,
            color = Color(0xFF475569),
            lineHeight = 20.sp,
        )
    }
}

@Composable
fun GoogleOnlyErrorSlot(message: String?) {
    if (message == null) return
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = message,
        color = Color(0xFFB91C1C),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun GoogleOnlyFooterLink(
    text: String,
    linkText: String,
    onClick: () -> Unit,
) {
    Spacer(modifier = Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$text ",
            color = Color(0xFF64748B),
            fontSize = 13.sp,
        )
        Text(
            text = linkText,
            color = ScottsTechXColors.AccentLink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
