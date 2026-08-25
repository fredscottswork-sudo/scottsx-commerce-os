package com.scottsx.app.ui.screens

import com.scottsx.app.data.CartStore

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import com.scottsx.app.data.remote.ChatTurn
import com.scottsx.app.data.remote.RemoteAssistantClient
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ai.ScottsTechAi
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.domain.Product
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.scottsx.app.data.remote.V2Client
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

/**
 * Stage-2 AI Assistant landing screen.
 *
 * The brief requires:
 *   - "Do not create a fake AI interface that only displays
 *      static responses."
 *   - "If the backend AI is not yet available, create the UI
 *      and service abstraction so it can be connected cleanly
 *      later."
 *
 * We deliver a clean chat-bubble UI with a local keyword-based
 * recommender that returns real products from [MarketplaceDataSource]
 * when the user types one of the suggested queries. The
 * composition is structured to drop in a real backend client by
 * replacing the [recommend] function.
 */
@Composable
fun AiAssistantScreen(
    onBack: () -> Unit,
    onOpenProduct: (com.scottsx.app.data.domain.Product) -> Unit = {},
    onTabSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remote = remember { RemoteAssistantClient() }
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("local") }  // "remote" | "local" — drives the badge in the header
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "Hi! I'm your ScottsTechX AI Assistant. Ask me to find products, compare options, or suggest items within your budget.",
                isFromUser = false,
            ),
        )
    }
    var input by remember { mutableStateOf("") }
    var bottomTab by remember { mutableStateOf(BottomTab.Home) }

    val suggestions = listOf(
        "Phones under UGX 800,000",
        "Shoes near Kampala",
        "Best laptop for work",
        "Affordable groceries",
        "Trending electronics",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp),
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ScottsTechXColors.BluePrimaryDark,
                                ScottsTechXColors.BluePrimary,
                            ),
                        ),
                    )
                    .padding(top = 36.dp, bottom = 18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Assistant",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                        )
                        Text(
                            text = "Smart recommendations for you",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                        )
                        // Source badge — drives trust signal for testers.
                        // "remote" = the free-LLM backend answered.
                        // "local"  = backend was unreachable; we're using
                        //             the on-device keyword recommender.
                        Spacer(Modifier.height(6.dp))
                        val badge = if (source == "remote")
                            "●  Remote — apifreellm.com"
                        else
                            "●  Local fallback"
                        Text(
                            text = badge,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Messages
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages.toList(), key = { it.text.hashCode() }) { msg ->
                    ChatBubble(message = msg)
                    if (msg.products.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(msg.products, key = { it.id }) { p ->
                                ProductCard(
                                    product = p,
                                    width = 160.dp,
                                    onClick = { onOpenProduct(p) },
                                    onAddToCart = { CartStore.add(p.id) },
                                )
                            }
                        }
                    }
                }

                // Suggestion chips
                if (messages.size <= 1) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Try asking:",
                            color = ScottsTechXColors.OnLightSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(suggestions) { suggestion ->
                                            SuggestionChip(
                            text = suggestion,
                            onClick = {
                                messages.add(ChatMessage(text = suggestion, isFromUser = true))
                                scope.launch {
                                    val reply = askRemoteOrLocal(suggestion, ScottsTechAi.Context(screen = "ai-assistant"))
                                    source = reply.source.label
                                    messages.add(ChatMessage(text = reply.text, isFromUser = false))
                                }
                            },
                        )
                    }
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = null,
                            tint = ScottsTechXColors.BluePrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = {
                                Text(
                                    text = "Ask me anything...",
                                    color = ScottsTechXColors.OnLightSecondary,
                                    fontSize = 14.sp,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = ScottsTechXColors.OnLight,
                                unfocusedTextColor = ScottsTechXColors.OnLight,
                            ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
                            ),
                        )
                        .clickable {
                            if (input.isNotBlank()) {
                                messages.add(ChatMessage(text = input, isFromUser = true))
                                val userInput = input
                                input = ""
                                scope.launch {
                                    val reply = askRemoteOrLocal(userInput, ScottsTechAi.Context(screen = "ai-assistant"))
                                    source = reply.source.label
                                    messages.add(ChatMessage(text = reply.text, isFromUser = false))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            ScottsTechXBottomBar(
                selected = bottomTab,
                onSelect = { tab ->
                    bottomTab = tab
                    onTabSelect(tab)
                },
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!message.isFromUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
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
                .background(
                    if (message.isFromUser) ScottsTechXColors.BluePrimary
                    else Color.White,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = message.text,
                color = if (message.isFromUser) Color.White else ScottsTechXColors.OnLight,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = ScottsTechXColors.BluePrimary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * Local keyword-based recommender. Returns real products from
 * [MarketplaceDataSource] that match the user's query. Replace
 * with a backend call when available.
 */
private fun recommend(query: String): Pair<String, List<Product>> {
    val q = query.lowercase()
    val all = MarketplaceDataSource.allProducts
    val matches = when {
        "phone" in q || "iphone" in q || "samsung" in q || "vivo" in q ->
            all.filter { p ->
                val lc = (p.name + " " + p.shortDescription).lowercase()
                "phone" in lc || "iphone" in lc || "samsung" in lc || "vivo" in lc
            }
        "shoe" in q || "sneaker" in q || "nike" in q || "adidas" in q || "balance" in q ->
            all.filter { p ->
                val lc = (p.name + " " + p.shortDescription).lowercase()
                "shoe" in lc || "sneaker" in lc || "nike" in lc || "adidas" in lc || "balance" in lc
            }
        "laptop" in q || "work" in q || "study" in q ->
            all.filter { p ->
                val lc = (p.name + " " + p.shortDescription + " " + p.description).lowercase()
                "laptop" in lc || "study" in lc || "work" in lc
            }
        "grocery" in q || "groceries" in q || "rice" in q || "oil" in q || "food" in q ->
            all.filter { p -> p.category.displayName.lowercase().contains("grocery") }
        "beauty" in q || "lipstick" in q || "soap" in q || "cosmetic" in q ->
            all.filter { p -> p.category.displayName.lowercase().contains("beauty") }
        else -> all.filter { it.rating >= 4.5f }.take(4)
    }
    val response = when {
        matches.isEmpty() ->
            "I couldn't find anything matching \"$query\". Try one of the suggested categories below."
        else ->
            "Here are ${matches.take(4).size} products I found for \"" + query + "\":"
    }
    return response to matches.take(4)
}

/**
 * A single message in the AI chat.
 */
private data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val products: List<Product> = emptyList(),
)
/**
 * Try the unified AI backend at /api/v1/ai/assistant first. If that
 * fails for any reason (no network, backend disabled, 4xx/5xx), fall
 * back to the local keyword recommender so the UI never gets stuck.
 *
 * Returns the user-visible reply text, an optional list of products
 * to render under the bubble, and a flag indicating which source
 * produced the reply (so the header badge can show "Remote" vs
 * "Local fallback").
 */
private suspend fun askRemoteOrLocal(
    query: String,
    context: ScottsTechAi.Context,
): ScottsTechAi.Reply {
    return ScottsTechAi.ask(
        userMessage = query,
        history = emptyList(),
        context = context,
    )
}
