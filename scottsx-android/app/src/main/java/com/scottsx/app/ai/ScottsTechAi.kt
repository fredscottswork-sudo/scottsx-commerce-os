package com.scottsx.app.ai

import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.TransactionStatus
import com.scottsx.app.data.remote.ChatTurn
import com.scottsx.app.data.remote.RemoteAssistantClient

/**
 * Stage 4 — ScottsTechX AI orchestrator.
 *
 * Responsibilities:
 *  1. Build a role-aware, market-aware system prompt from the
 *     CapabilityRegistry + active context (current screen, product,
 *     seller, conversation, transaction).
 *  2. Try to answer common marketplace queries locally using the
 *     secure [AiTools] layer (these ALWAYS return real data and
 *     never invent anything).
 *  3. If no local pattern matches, send the question + history to
 *     [RemoteAssistantClient] (free LLM) — which is best-effort and
 *     may return LocalFallback. Local fallback means "the free LLM
 *     is down, but I can still help with structured commands".
 *  4. Every result is tagged with [Source]: LocalMarketplaceTool,
 *     RemoteLLM, or LocalFallback — the UI shows this label so the
 *     user can tell AI suggestions from real marketplace data.
 *
 * The AI never invents products, prices, sellers, payments, or
 * transactions. If a tool returns nothing, the AI honestly says so.
 */
object ScottsTechAi {

    /** Where the answer came from — the UI uses this for labelling. */
    enum class Source(val label: String) {
        LOCAL_TOOL("From ScottsTechX marketplace"),
        LOCAL_RULE("ScottsTechX rule"),
        REMOTE_LLM("AI suggestion"),
        LOCAL_FALLBACK("Offline reply"),
    }

    /** A single reply that the AI assistant shows. */
    data class Reply(
        val text: String,
        val source: Source,
        val toolCalls: List<String> = emptyList(),
        val suggestedActions: List<SuggestedAction> = emptyList(),
        val mentionedProductIds: List<String> = emptyList(),
        val mentionedSellerIds: List<String> = emptyList(),
        val mentionedReceiptNumber: String? = null,
        val mentionedTransactionId: String? = null,
    )

    /** An inline action the AI suggests to the user. */
    data class SuggestedAction(
        val label: String,
        val kind: Kind,
    ) {
        enum class Kind { OPEN_PRODUCT, OPEN_SELLER, OPEN_NEARBY, OPEN_TRANSACTION, OPEN_RECEIPT, CREATE_TRANSACTION, CREATE_RECEIPT, OPEN_SETTINGS, OPEN_THREAD }
    }

    /** Active screen-level context. */
    data class Context(
        val screen: String = "",
        val productId: String? = null,
        val sellerId: String? = null,
        val threadId: String? = null,
        val transactionId: String? = null,
    )

    /**
     * Main entry: answer a user question with full role + context.
     *
     * @param userMessage raw user-typed question
     * @param history recent chat history (last 8 turns)
     * @param context active screen context (optional)
     */
    suspend fun ask(
        userMessage: String,
        history: List<ChatTurn> = emptyList(),
        context: Context = Context(),
    ): Reply {
        val role = Session.roleOrNull()
            ?: return Reply(
                text = "Sign in first, then I can help you with ScottsTechX.",
                source = Source.LOCAL_FALLBACK,
            )
        val userId = Session.userIdOrNull() ?: return Reply(
            text = "Sign in first, then I can help you with ScottsTechX.",
            source = Source.LOCAL_FALLBACK,
        )

        // 1. Try local pattern match (deterministic, real data, role-checked).
        val localReply = LocalPatternRouter.respond(
            userMessage = userMessage,
            role = role,
            context = context,
        )
        if (localReply != null) {
            AiPersonalizationStore.recordAiOpened()
            return localReply
        }

        // 2. Fall back to the free LLM with a role-aware system prompt.
        val sys = buildSystemPrompt(role, context)
        val resp = RemoteAssistantClient().ask(
            message = userMessage,
            history = history,
        )
        AiPersonalizationStore.recordAiOpened()
        return when (resp) {
            is RemoteAssistantClient.Result.Remote -> Reply(
                text = resp.reply,
                source = Source.REMOTE_LLM,
            )
            is RemoteAssistantClient.Result.LocalFallback -> Reply(
                text = "I couldn't reach the AI server just now (${resp.reason}). " +
                    "You can still ask me structured questions like: \"Find phones under 1,000,000 UGX\", " +
                    "\"Summarize my messages\", or \"Create a receipt for Sarah\".",
                source = Source.LOCAL_FALLBACK,
            )
        }
    }

    /**
     * The role-aware system prompt is generated from the Capability
     * Registry + active context + safety rules. The free LLM receives
     * a markdown block describing the role, what tools it can use
     * (named), what it must NOT invent, and the current context.
     */
    fun buildSystemPrompt(role: Role, context: Context): String {
        val sb = StringBuilder()
        sb.append("You are ScottsTechX AI, the in-app assistant for the ScottsTechX marketplace.\n\n")
        sb.append("The user is a ").append(role.displayName).append(".\n\n")
        sb.append("ScottsTechX is NOT a payment processor. ScottsTechX helps the buyer and seller discover each other, " +
                "communicate, agree on a transaction, and generate a structured receipt. Payment happens outside ScottsTechX between the parties.\n\n")
        sb.append("# Your capabilities for this user\n\n")
        sb.append(CapabilityRegistry.toPromptMarkdown(role))
        sb.append("\n# Hard safety rules\n\n")
        sb.append("- Never invent products, prices, sellers, stock, payments, deliveries, agreements, or reviews. " +
                "If a tool returns nothing, say so honestly.\n")
        sb.append("- Label suggestions clearly as 'AI suggestion'. Real marketplace data must be labelled 'From ScottsTechX marketplace'.\n")
        sb.append("- Never pretend ScottsTechX processed a payment. Receipts are recorded by the seller.\n")
        sb.append("- Never finalize a receipt or transaction agreement without explicit user confirmation.\n")
        sb.append("- Do not reveal another user's private information.\n")
        sb.append("- Do not infer sensitive personal characteristics.\n")
        sb.append("- Do not make legal liability decisions on disputes.\n\n")
        sb.append("# Personalization signals (user-controlled)\n\n")
        sb.append(AiPersonalizationStore.summaryForRole(role)).append("\n")
        if (context.screen.isNotBlank() || context.productId != null || context.transactionId != null || context.threadId != null) {
            sb.append("\n# Active context\n\n")
            if (context.screen.isNotBlank()) sb.append("- Screen: ").append(context.screen).append("\n")
            if (context.productId != null) {
                val p = MarketplaceDataSource.productById(context.productId)
                sb.append("- Product: ${p?.name ?: context.productId}\n")
            }
            if (context.sellerId != null) {
                val s = MarketplaceDataSource.storefront(context.sellerId)?.seller
                sb.append("- Seller: ${s?.name ?: context.sellerId}\n")
            }
            if (context.threadId != null) {
                val t = MarketplaceDataSource.threadById(context.threadId)
                sb.append("- Conversation: ${t?.sellerName ?: context.threadId}\n")
            }
            if (context.transactionId != null) {
                val ag = TransactionStore.agreementById(context.transactionId)
                sb.append("- Transaction: ${ag?.id ?: context.transactionId} (${ag?.statusLabel() ?: "unknown"})\n")
            }
        }
        return sb.toString()
    }
}