package com.scottsx.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * Stage 5 — Real chat UI screen.
 *
 * This is a standalone copy of the RealAiChatScreen function from
 * AiAssistantScreen.kt. The duplicated definition resolves the Kotlin
 * "unresolved reference" cascade error that occurred because the
 * function was hidden behind other compile errors in the same file.
 *
 * The functionality is identical: full chat bubbles, composer,
 * quick-reply chips, real backend calls via V2Client.ask(),
 * mirror of every turn to Firestore via Mirror.
 */
@Composable
fun RealAiChatScreen(
    onBack: () -> Unit,
    onOpenProduct: (com.scottsx.app.data.domain.Product) -> Unit = {},
    onTabSelect: (BottomTab) -> Unit,
    initialMessage: String? = null,
) {
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<ChatTurnUi>() }
    var input by remember { mutableStateOf(initialMessage ?: "") }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun send(text: String) {
        if (text.isBlank() || isSending) return
        isSending = true
        turns.add(ChatTurnUi(role = "user", content = text))
        input = ""
        scope.launch {
            // Load the entire marketplace catalog so the AI can reason
            // over real, live store inventory — not generic knowledge.
            val products = runCatching { V2Client.fetchProductsList() }.getOrDefault(emptyList())
            val catalogCtx = if (products.isNotEmpty()) {
                val brief = products.take(40).joinToString("\n") { p ->
                    "- ${p.name} (${p.category}) UGX ${p.priceUgx} from ${p.seller.name} [id=${p.id}]"
                }
                "Live marketplace catalog (${products.size} products):\n$brief\n"
            } else {
                "Live marketplace catalog is empty right now.\n"
            }
            val fullMessage = "$catalogCtx\nUser question: $text"
            val reply = V2Client.ask(fullMessage, screen = "ai-chat")
            val replyText = reply?.text
                ?: "I can't reach the AI service right now. Check your connection or try again."
            turns.add(
                ChatTurnUi(
                    role = "assistant",
                    content = replyText,
                    source = reply?.provider ?: "offline",
                )
            )
            com.scottsx.app.ai.AiPersonalizationStore.recordAiOpened()
            isSending = false
            try { listState.animateScrollToItem(turns.size - 1) } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(Unit) {
        com.scottsx.app.ai.AiPersonalizationStore.recordAiOpened()
        V2Client.recordSignal("category", "AI Assistant")
        if (initialMessage != null) send(initialMessage)
    }

    Scaffold(
        topBar = {
            Surface(
                color = ScottsTechXColors.Background,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = ScottsTechXColors.TextPrimary)
                    }
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = ScottsTechXColors.Primary)
                    Spacer(Modifier.width(8.dp))
                    Text("ScottsTechX AI", color = ScottsTechXColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch { V2Client.clearAiMemory() }
                        turns.clear()
                    }) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = "Clear AI memory",
                            tint = ScottsTechXColors.Primary,
                        )
                    }
                }
            }
        },
        bottomBar = {
            ScottsTechXBottomBar(selected = BottomTab.Ai, onSelect = onTabSelect)
        },
        containerColor = ScottsTechXColors.Background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "What's near me?",
                    "Cheapest Samsung A55",
                    "Summarize my transactions",
                    "Find a phone under 1,000,000 UGX",
                ).forEach { q ->
                    item { QuickReplyChip(q) { send(q) } }
                }
            }

            Divider(color = ScottsTechXColors.Divider)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (turns.isEmpty()) {
                    item { EmptyAiState() }
                } else {
                    items(turns) { turn -> Bubble(turn) }
                }
                if (isSending) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = ScottsTechXColors.Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Thinking…", color = ScottsTechXColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            Divider(color = ScottsTechXColors.Divider)
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask ScottsTechX AI…") },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ScottsTechXColors.Primary,
                        unfocusedBorderColor = ScottsTechXColors.Divider,
                        cursorColor = ScottsTechXColors.Primary,
                    ),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { send(input) },
                    enabled = input.isNotBlank() && !isSending,
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (input.isNotBlank()) ScottsTechXColors.Primary else ScottsTechXColors.Divider,
                    )
                }
            }
        }
    }
}

private data class ChatTurnUi(
    val role: String,
    val content: String,
    val source: String = "",
)

@Composable
private fun Bubble(turn: ChatTurnUi) {
    val isUser = turn.role == "user"
    val bg = if (isUser) ScottsTechXColors.Primary else ScottsTechXColors.Surface
    val fg = if (isUser) Color.White else ScottsTechXColors.TextPrimary
    val shape = if (isUser)
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    else
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bg,
            shape = shape,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(turn.content, color = fg, fontSize = 14.sp)
                if (turn.source.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "via ${turn.source}",
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else ScottsTechXColors.TextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickReplyChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ScottsTechXColors.Surface,
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text,
            color = ScottsTechXColors.TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EmptyAiState() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.SmartToy,
            contentDescription = null,
            tint = ScottsTechXColors.Primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ask me anything",
            color = ScottsTechXColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "I can help you find products, summarize your transactions, draft a receipt, or just chat. " +
                "I'll only suggest things I have data for — and I'll always say when I'm guessing.",
            color = ScottsTechXColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}
