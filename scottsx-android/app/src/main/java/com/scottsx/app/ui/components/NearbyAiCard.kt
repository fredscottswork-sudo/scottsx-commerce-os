package com.scottsx.app.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Two high-priority feature cards placed side-by-side:
 *   - Nearby (location pin + map visualization)
 *   - AI Assistant (blue/purple gradient + AI icon)
 *
 * Both cards are premium, rounded, animated, and tap-able.
 */
@Composable
fun NearbyAiCard(
    onNearbyClick: () -> Unit,
    onAiClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(135.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NearbyCard(
            modifier = Modifier.weight(1f),
            onClick = onNearbyClick,
        )
        AiAssistantCard(
            modifier = Modifier.weight(1f),
            onClick = onAiClick,
        )
    }
}

@Composable
private fun NearbyCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F766E), Color(0xFF0EA5E9)),
                ),
            )
            .clickable { onClick() },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Concentric map rings
            for (i in 0 until 4) {
                val r = w * 0.18f + i * w * 0.13f
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f - i * 0.03f),
                    radius = r,
                    center = Offset(w * 0.78f, h * 0.85f),
                    style = Stroke(width = 1.4f),
                )
            }
            // Compass needle
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(w * 0.72f, h * 0.78f),
                end = Offset(w * 0.84f, h * 0.5f),
                strokeWidth = 2f,
            )
            // Small store dots
            for (d in listOf(
                Pair(0.20f, 0.62f),
                Pair(0.30f, 0.78f),
                Pair(0.55f, 0.92f),
                Pair(0.66f, 0.60f),
            )) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 3.5f,
                    center = Offset(w * d.first, h * d.second),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.NearMe,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(
                    text = "Nearby",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Find trending products\nand stores near you.",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun AiAssistantCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF312E81), Color(0xFF7C3AED), Color(0xFF3B82F6)),
                ),
            )
            .clickable { onClick() },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Soft glow blob
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = w * 0.55f,
                center = Offset(w * 0.85f, h * 0.18f),
            )
            // Star sparks
            for ((x, y, r) in listOf(
                Triple(0.78f, 0.28f, 2.5f),
                Triple(0.85f, 0.55f, 1.8f),
                Triple(0.20f, 0.20f, 2.0f),
                Triple(0.55f, 0.30f, 1.4f),
            )) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f),
                    radius = r,
                    center = Offset(w * x, h * y),
                )
            }
            // Chat bubble outline
            val path = Path().apply {
                moveTo(w * 0.15f, h * 0.5f)
                quadraticBezierTo(w * 0.15f, h * 0.35f, w * 0.30f, h * 0.35f)
                lineTo(w * 0.55f, h * 0.35f)
                quadraticBezierTo(w * 0.70f, h * 0.35f, w * 0.70f, h * 0.5f)
                lineTo(w * 0.70f, h * 0.62f)
                quadraticBezierTo(w * 0.70f, h * 0.77f, w * 0.55f, h * 0.77f)
                lineTo(w * 0.30f, h * 0.77f)
                close()
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.20f),
                style = Stroke(width = 1.6f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(
                    text = "AI Assistant",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Ask anything. Get smart\nrecommendations.",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}