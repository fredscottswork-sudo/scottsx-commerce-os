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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ai.ScottsTechAi
import com.scottsx.app.ui.components.GradientHeader
import com.scottsx.app.ui.components.QuickChip
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** BUYER AI — the current assistant (v0.22.1). Answers from the live catalog. */
@Composable
fun RealAiChatScreen(onBack: () -> Unit) {
    var turns by remember {
        mutableStateOf(
            listOf(
                ChatTurn(
                    false,
                    "Hi! I'm ScottsTechX AI. Ask me about products, prices, nearby sellers or flash deals.",
                ),
            ),
        )
    }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun send(prompt: String) {
        if (prompt.isBlank() || thinking) return
        input = ""
        turns = turns + ChatTurn(true, prompt)
        thinking = true
        scope.launch {
            val reply = ScottsTechAi.askWithCatalog(prompt, "ai-chat")
            turns = turns + ChatTurn(false, reply)
            thinking = false
        }
    }

    LaunchedEffect(turns.size, thinking) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GradientHeader(
            title = "ScottsTechX AI",
            subtitle = "Buyer assistant · live catalog answers",
            colors = listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent),
            onBack = onBack,
        )

        // Quick-reply chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ScottsTechAi.buyerQuickReplies.size) { index ->
                QuickChip(ScottsTechAi.buyerQuickReplies[index]) { send(ScottsTechAi.buyerQuickReplies[index]) }
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

        // Composer
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
                    placeholder = { Text("Ask about products, prices, sellers…") },
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
                            Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)),
                            RoundedCornerShape(23.dp),
                        )
                        .clickable(enabled = input.isNotBlank() && !thinking) { send(input) },
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
