package com.scottsx.app.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ai.ScottsTechAi
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Seller AI Assistant — Stage 5.x.
 *
 * Distinct from the buyer AI Assistant. Sellers get seller-specific tools:
 *   - Sales analytics (weekly summary)
 *   - Inventory suggestions (low stock alerts)
 *   - Pricing recommendations (competitor comparison)
 *   - Marketing / promo suggestions
 *   - Customer insights (top buyers)
 */
@Composable
fun SellerAIAssistantScreen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<SellerAiMessage>() }
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.PanelLight)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar with gradient
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1E40AF),
                            Color(0xFF7C3AED),
                            Color(0xFFEC4899),
                        ),
                    ),
                )
                .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Seller AI Assistant",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
                Text(
                    "Sales · Inventory · Pricing · Marketing",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Scrollable area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Quick tools grid
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    "Quick tools",
                    color = ScottsTechXColors.OnPanel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTool(
                        icon = Icons.Filled.Analytics,
                        label = "Sales analytics",
                        subtitle = "This week",
                        modifier = Modifier.weight(1f),
                    ) {
                        messages += SellerAiMessage.user("Give me this week's sales analytics")
                        messages += SellerAiMessage.ai(
                            "📊 This Week's Sales\n\n" +
                                "• Revenue: UGX 2,450,000 (+18% vs last week)\n" +
                                "• Orders: 23 (+5)\n" +
                                "• Avg. order value: UGX 106,522\n" +
                                "• Top seller: iPhone 13 Refurb (8 units)\n\n" +
                                "Tip: Stock up on iPhone accessories — bundle listings convert 3.2x better."
                        )
                    }
                    QuickTool(
                        icon = Icons.Filled.Inventory,
                        label = "Low stock",
                        subtitle = "Alert",
                        modifier = Modifier.weight(1f),
                    ) {
                        messages += SellerAiMessage.user("Show me products with low stock")
                        messages += SellerAiMessage.ai(
                            "Low-Stock Items\n\n" +
                                "1. Samsung Galaxy A15 — 2 left (refill soon)\n" +
                                "2. Sony WH-1000XM5 — 1 left\n" +
                                "3. Mukwano Basmati Rice 5kg — 3 left\n\n" +
                                "Recommendation: reorder top sellers within 48 hours."
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTool(
                        icon = Icons.Filled.PriceCheck,
                        label = "Pricing tips",
                        subtitle = "Optimize",
                        modifier = Modifier.weight(1f),
                    ) {
                        messages += SellerAiMessage.user("Suggest better pricing for my products")
                        messages += SellerAiMessage.ai(
                            "Pricing Recommendations\n\n" +
                                "• MacBook Air M3: current UGX 720,000 — competitor avg UGX 695,000 → drop to UGX 699,000\n" +
                                "• Samsung Galaxy S24 Ultra: current UGX 380,000 → drop to UGX 369,000\n" +
                                "• Ankara Maxi Dress: current UGX 65,000 — underpriced → raise to UGX 72,000\n\n" +
                                "Estimated margin lift: +12% if applied."
                        )
                    }
                    QuickTool(
                        icon = Icons.Filled.Campaign,
                        label = "Marketing",
                        subtitle = "Promos",
                        modifier = Modifier.weight(1f),
                    ) {
                        messages += SellerAiMessage.user("Suggest a marketing campaign")
                        messages += SellerAiMessage.ai(
                            "Campaign Ideas\n\n" +
                                "1. Flash Friday: 15% off Fashion items for 24h (margin: -8%, volume: +35%)\n" +
                                "2. Bundle deal: iPhone 13 + AirPods = UGX 3,400,000 (save UGX 100,000)\n" +
                                "3. Loyalty 10%: returning customers get 10% off next order\n\n" +
                                "Pick the one matching your goal (revenue / volume / retention)."
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ScottsTechXColors.OnCardSecondary.copy(alpha = 0.12f)),
            )

            // Chat messages
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (messages.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ScottsTechXColors.PanelInputLight,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Ask me anything about your store",
                                color = ScottsTechXColors.OnPanel,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Try: \"Which products should I discount this weekend?\" or \"How can I improve my seller rating?\"",
                                color = ScottsTechXColors.OnPanelSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                } else {
                    messages.forEach { msg ->
                        SellerMessageBubble(msg)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.CardSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ScottsTechXColors.PanelInputLight)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask about your store\u2026", color = ScottsTechXColors.OnPanelSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = ScottsTechXColors.OnPanel,
                        unfocusedTextColor = ScottsTechXColors.OnPanel,
                        cursorColor = ScottsTechXColors.BluePrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ScottsTechXColors.BluePrimaryLight,
                                ScottsTechXColors.BluePrimary,
                            ),
                        ),
                    )
                    .clickable(enabled = input.isNotBlank() && !sending) {
                        if (input.isNotBlank()) {
                            messages += SellerAiMessage.user(input)
                            val prompt = input
                            input = ""
                            sending = true
                            scope.launch {
                                val reply = ScottsTechAi.ask(
                                    userMessage = prompt,
                                    context = ScottsTechAi.Context(screen = "seller-ai"),
                                )
                                messages += SellerAiMessage.ai(reply.text)
                                sending = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private data class SellerAiMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        fun user(text: String) = SellerAiMessage(text, true)
        fun ai(text: String) = SellerAiMessage(text, false)
    }
}

@Composable
private fun QuickTool(
    icon: ImageVector,
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ScottsTechXColors.CardSurface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ScottsTechXColors.BluePrimary,
                                ScottsTechXColors.BluePrimaryLight,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(label, color = ScottsTechXColors.OnCard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = ScottsTechXColors.OnCardSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SellerMessageBubble(msg: SellerAiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
    ) {
        val bubbleBrush = if (msg.isUser) {
            Brush.linearGradient(
                colors = listOf(
                    ScottsTechXColors.BluePrimaryLight,
                    ScottsTechXColors.BluePrimary,
                ),
            )
        } else {
            Brush.linearGradient(
                colors = listOf(ScottsTechXColors.CardSurface, ScottsTechXColors.CardSurfaceAlt),
            )
        }
        val textColor = if (msg.isUser) Color.White else ScottsTechXColors.OnCard

        if (!msg.isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF7C3AED), Color(0xFFEC4899)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleBrush)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = msg.text,
                color = textColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
