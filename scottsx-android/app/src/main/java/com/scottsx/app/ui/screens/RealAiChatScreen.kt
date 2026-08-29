package com.scottsx.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.BottomTab
import androidx.compose.foundation.layout.imePadding
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.components.navBarSpacer
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import kotlinx.coroutines.launch

/**
 * ScottsTechX AI — ChatGPT-style assistant on a true-black canvas.
 *
 * Every byte of content is real: answers come from the backend's
 * catalog-grounded assistant (POST /api/v1/ai/v2/ask), the empty-state
 * suggestion cards come from GET /api/v1/ai/agents, and product cards are
 * the exact rows the model grounded on — tapping one opens the real
 * product. No canned replies, no demo inventory.
 */

private data class ChatTurnUi(
    val role: String,                 // "user" | "assistant"
    val content: String,
    val provider: String = "",
    val model: String = "",
    val agentName: String = "",
    val products: List<V2Client.AiProduct> = emptyList(),
)

@Composable
fun RealAiChatScreen(
    onBack: () -> Unit,
    onOpenProduct: (com.scottsx.app.data.domain.Product) -> Unit = {},
    onOpenProductId: (String) -> Unit = {},
    onTabSelect: (BottomTab) -> Unit,
    initialMessage: String? = null,
) {
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<ChatTurnUi>() }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var starters by remember { mutableStateOf<List<String>>(emptyList()) }
    var agentLabel by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var initialSent by remember { mutableStateOf(false) }

    fun send(raw: String) {
        val prompt = raw.trim()
        if (prompt.isEmpty() || isSending) return
        isSending = true
        input = ""
        turns.add(ChatTurnUi(role = "user", content = prompt))
        scope.launch {
            // The backend grounds itself on the live catalog; we only send
            // a short rolling transcript so answers stay conversational.
            val history = turns.takeLast(12).dropLast(1).map { it.role to it.content }
            val reply = try {
                V2Client.askV2(prompt, screen = "ai-chat", history = history)
            } catch (_: Throwable) { null }
            if (reply == null || reply.text.isBlank()) {
                turns.add(
                    ChatTurnUi(
                        role = "assistant",
                        content = "I couldn't reach the AI service just now — check your connection and try again.",
                    ),
                )
            } else {
                turns.add(
                    ChatTurnUi(
                        role = "assistant",
                        content = reply.text,
                        provider = reply.provider,
                        model = reply.model,
                        agentName = reply.agentName,
                        products = reply.products,
                    ),
                )
                if (reply.agentName.isNotBlank()) agentLabel = reply.agentName
            }
            isSending = false
            try { listState.animateScrollToItem(turns.lastIndex.coerceAtLeast(0)) } catch (_: Throwable) {}
        }
    }

    // Load the agent directory once — its starters power the empty-state
    // suggestion cards (real backend content), then honour any suggested
    // first message the navigation passed in.
    LaunchedEffect(Unit) {
        com.scottsx.app.ai.AiPersonalizationStore.recordAiOpened()
        val agents = try { V2Client.fetchAiAgents() } catch (_: Throwable) { emptyList() }
        val shopping = agents.firstOrNull { it.id == "shopping" } ?: agents.firstOrNull()
        if (shopping != null) {
            starters = shopping.starters
            agentLabel = shopping.name
        }
        if (!initialSent && !initialMessage.isNullOrBlank()) {
            initialSent = true
            send(initialMessage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The composer must rise above the soft keyboard — edge-to-edge
                // disables the old resize behaviour, so handle it here.
                .imePadding(),
        ) {

            // ── Top bar ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarSpacer()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.SurfaceElevatedDark)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ArrowBack, contentDescription = "Back",
                        tint = ScottsTechXColors.OnDark, modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ScottsTechX AI",
                        color = ScottsTechXColors.OnDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Text(
                        if (agentLabel.isBlank()) "Grounded on the live catalog"
                        else "$agentLabel · grounded on the live catalog",
                        color = ScottsTechXColors.OnDarkMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (turns.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ScottsTechXColors.SurfaceElevatedDark)
                            .clickable {
                                turns.clear()
                                isSending = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Edit, contentDescription = "New chat",
                            tint = ScottsTechXColors.OnDarkSecondary, modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // ── Conversation ────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (turns.isEmpty() && !isSending) {
                    item {
                        AiEmptyState(
                            starters = starters,
                            onStarter = { send(it) },
                        )
                    }
                }
                items(turns) { turn ->
                    if (turn.role == "user") {
                        UserBubble(turn.content)
                    } else {
                        AssistantTurn(
                            turn = turn,
                            onOpenProductId = onOpenProductId,
                        )
                    }
                }
                if (isSending) {
                    item { TypingIndicator() }
                }
            }

            // Follow-up chips once a conversation exists
            if (turns.any { it.role == "assistant" } && !isSending) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(listOf("Cheaper options", "Similar products", "Compare the top two")) { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ScottsTechXColors.SurfaceElevatedDark)
                                .border(1.dp, ScottsTechXColors.DarkBorder, RoundedCornerShape(50))
                                .clickable { send(chip) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(chip, color = ScottsTechXColors.OnDarkSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Composer ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(ScottsTechXColors.SurfaceElevatedDark)
                    .border(1.dp, ScottsTechXColors.DarkBorder, RoundedCornerShape(26.dp))
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (input.isEmpty()) {
                        Text(
                            "Message ScottsTechX AI…",
                            color = ScottsTechXColors.OnDarkMuted,
                            fontSize = 14.5.sp,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = ScottsTechXColors.OnDark, fontSize = 14.5.sp),
                        cursorBrush = SolidColor(ScottsTechXColors.BluePrimary),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                val canSend = input.isNotBlank() && !isSending
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (canSend) ScottsTechXColors.BluePrimary else ScottsTechXColors.DarkPanelHover)
                        .clickable(enabled = canSend) { send(input) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else ScottsTechXColors.OnDarkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Bottom navigation
            ScottsTechXBottomBar(selected = null, onSelect = onTabSelect)
            Spacer(Modifier.navBarSpacer())
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(ScottsTechXColors.DarkPanelHover)
                .padding(horizontal = 15.dp, vertical = 11.dp),
        ) {
            Text(text, color = ScottsTechXColors.OnDark, fontSize = 14.5.sp)
        }
    }
}

@Composable
private fun AssistantTurn(
    turn: ChatTurnUi,
    onOpenProductId: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ChatGPT-style gradient sparkle avatar
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ScottsTechXColors.BluePrimary,
                                ScottsTechXColors.CyanAccent,
                                ScottsTechXColors.PurpleAccent,
                            ),
                            start = Offset.Zero,
                            end = Offset(200f, 200f),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                turn.agentName.ifBlank { "ScottsTechX AI" },
                color = ScottsTechXColors.OnDarkSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            turn.content,
            color = ScottsTechXColors.OnDark,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp),
        )
        if (turn.model.isNotBlank() || turn.provider.isNotBlank()) {
            Text(
                listOf(turn.model, turn.provider).filter { it.isNotBlank() }.joinToString(" · "),
                color = ScottsTechXColors.OnDarkMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
            )
        }
        if (turn.products.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(start = 40.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(turn.products, key = { it.id }) { product ->
                    AiProductCard(product = product, onOpen = { onOpenProductId(product.id) })
                }
            }
        }
    }
}

/** Grounded product card — a REAL catalog row the model answered with. */
@Composable
private fun AiProductCard(product: V2Client.AiProduct, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .width(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ScottsTechXColors.SurfaceElevatedDark)
            .border(1.dp, ScottsTechXColors.DarkBorder, RoundedCornerShape(16.dp))
            .clickable { onOpen() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(ScottsTechXColors.DarkPanel),
        ) {
            if (!product.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AutoAwesome, contentDescription = null,
                        tint = ScottsTechXColors.OnDarkMuted, modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (product.discountPercent > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.LocalOffer, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(9.dp),
                    )
                    Text(
                        " -${product.discountPercent}%",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                product.title,
                color = ScottsTechXColors.OnDark,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Text(
                    formatUgx(product.priceMinor),
                    color = ScottsTechXColors.BluePrimaryLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (product.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Verified, contentDescription = "Verified seller",
                        tint = Color(0xFF16A34A), modifier = Modifier.size(12.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.Filled.Star, contentDescription = null,
                    tint = Color(0xFFFBBF24), modifier = Modifier.size(11.dp),
                )
                Text(
                    " ${"%.1f".format(product.rating)}" +
                        (product.city?.let { " · $it" } ?: ""),
                    color = ScottsTechXColors.OnDarkMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ScottsTechXColors.BluePrimary,
                            ScottsTechXColors.CyanAccent,
                            ScottsTechXColors.PurpleAccent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AutoAwesome, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.5.dp)
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.BluePrimaryLight),
            )
        }
    }
}

@Composable
private fun AiEmptyState(
    starters: List<String>,
    onStarter: (String) -> Unit,
) {
    // Slow breathing glow behind the sparkle.
    val transition = rememberInfiniteTransition(label = "glow")
    val glow by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-scale",
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size((86 * glow).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ScottsTechXColors.BluePrimary.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                        radius = 320f,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ScottsTechXColors.BluePrimary,
                                ScottsTechXColors.CyanAccent,
                                ScottsTechXColors.PurpleAccent,
                            ),
                            start = Offset.Zero,
                            end = Offset(300f, 300f),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "How can I help you shop today?",
            color = ScottsTechXColors.OnDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Grounded on the live catalog — I only suggest products that really exist.",
            color = ScottsTechXColors.OnDarkMuted,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        starters.forEach { starter ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ScottsTechXColors.SurfaceElevatedDark)
                    .border(1.dp, ScottsTechXColors.DarkBorder, RoundedCornerShape(16.dp))
                    .clickable { onStarter(starter) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = ScottsTechXColors.BluePrimaryLight,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(starter, color = ScottsTechXColors.OnDarkSecondary, fontSize = 13.sp)
            }
        }
    }
}
