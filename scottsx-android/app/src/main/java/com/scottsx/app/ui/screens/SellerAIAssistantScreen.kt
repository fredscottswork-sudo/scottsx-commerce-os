package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ai.AiTools
import com.scottsx.app.ui.components.ChatTurn
import com.scottsx.app.ui.components.ChatTurnBubble
import com.scottsx.app.ui.components.GradientHeader
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * SELLER AI — gradient header (blue → purple → pink), a 2x2 grid of quick
 * tools (sales analytics / low stock / pricing / marketing), chat log and a
 * rounded composer.
 */
@Composable
fun SellerAIAssistantScreen(onBack: () -> Unit) {
    var turns by remember {
        mutableStateOf(
            listOf(
                ChatTurn(false, "Hello seller! Use a tool below or ask me anything about your store, inventory or pricing."),
            ),
        )
    }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun pushUser(text: String) {
        turns = turns + ChatTurn(true, text)
    }

    fun runTool(tool: AiTools.SellerTool) {
        if (thinking) return
        pushUser(tool.title)
        thinking = true
        scope.launch {
            val (_, answer) = AiTools.runTool(tool)
            turns = turns + ChatTurn(false, answer)
            thinking = false
        }
    }

    fun sendFreeform(text: String) {
        if (text.isBlank() || thinking) return
        input = ""
        pushUser(text)
        thinking = true
        scope.launch {
            val answer = AiTools.askFreeform(text)
            turns = turns + ChatTurn(false, answer)
            thinking = false
        }
    }

    LaunchedEffect(turns.size, thinking) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GradientHeader(
            title = "Seller AI Assistant",
            subtitle = "Analytics · stock · pricing · marketing",
            colors = listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent, ScottsTechXColors.PinkAccent),
            onBack = onBack,
        )

        // 2x2 quick tools grid
        val tools = AiTools.SellerTool.entries
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tools.chunked(2).forEach { rowTools ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowTools.forEach { tool ->
                        ToolCard(
                            tool = tool,
                            onClick = { runTool(tool) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // An odd number of tools would stretch the last card across
                    // the whole row; keep the grid on its 2-column rhythm.
                    if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            items(turns) { turn -> ChatTurnBubble(turn) }
            if (thinking) {
                item { ChatTurnBubble(ChatTurn(false, "…")) }
            }
        }

        Surface(shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask about your store…") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent, ScottsTechXColors.PinkAccent)),
                            RoundedCornerShape(23.dp),
                        )
                        .clickable(enabled = input.isNotBlank() && !thinking) { sendFreeform(input) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (input.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: AiTools.SellerTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when (tool) {
        AiTools.SellerTool.SalesAnalytics -> Icons.Filled.Analytics
        AiTools.SellerTool.LowStock -> Icons.Filled.Inventory2
        AiTools.SellerTool.PricingTips -> Icons.Filled.PriceCheck
        AiTools.SellerTool.MarketingIdeas -> Icons.Filled.Campaign
    }
    val gradient = when (tool) {
        AiTools.SellerTool.SalesAnalytics -> listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)
        AiTools.SellerTool.LowStock -> listOf(ScottsTechXColors.PurpleAccent, ScottsTechXColors.PinkAccent)
        AiTools.SellerTool.PricingTips -> listOf(ScottsTechXColors.PinkAccent, ScottsTechXColors.WarningAmber)
        AiTools.SellerTool.MarketingIdeas -> listOf(ScottsTechXColors.BluePrimaryLight, ScottsTechXColors.BluePrimaryDark)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(gradient[0].copy(alpha = 0.12f), gradient[1].copy(alpha = 0.06f))),
                    RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Brush.linearGradient(gradient), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(tool.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(tool.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
