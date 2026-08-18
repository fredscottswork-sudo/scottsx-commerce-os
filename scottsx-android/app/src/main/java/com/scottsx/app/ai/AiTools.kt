package com.scottsx.app.ai

import com.scottsx.app.data.remote.V2Client

/**
 * Seller AI tool layer — each tool is a canned question the LLM can answer
 * using the live catalog. Tools are surfaced as a 2x2 grid on the
 * SellerAIAssistantScreen and each one pushes a user + AI message into the chat.
 */
object AiTools {

    enum class SellerTool(
        val title: String,
        val subtitle: String,
        val prompt: String,
        val emoji: String,
    ) {
        SalesAnalytics(
            "Sales analytics",
            "Weekly revenue, orders, top sellers",
            "Give me a sales analytics report: weekly revenue, order volume and top-selling products, based on the catalog. Use bullet points.",
            "📈",
        ),
        LowStock(
            "Low stock",
            "Refill alerts + demand signals",
            "Which of my products are low on stock? List refill alerts and any demand signals you can infer from the catalog.",
            "📦",
        ),
        PricingTips(
            "Pricing tips",
            "Competitor comparison, margin lift",
            "Give me pricing tips: compare my prices with similar items and suggest small margin lifts that keep me competitive.",
            "🏷️",
        ),
        MarketingIdeas(
            "Marketing ideas",
            "Campaigns, promos, retention",
            "Suggest marketing ideas: campaigns, promos and buyer-retention tactics that work well for a Ugandan marketplace store.",
            "📣",
        ),
    }

    /** Runs a tool: pushes the canned prompt through the AI with catalog context. */
    suspend fun runTool(tool: SellerTool): Pair<String, String> {
        val answer = ScottsTechAi.askWithCatalog(tool.prompt, "seller-tool:${tool.title}")
        return tool.title to answer
    }

    /** Freeform seller question. */
    suspend fun askFreeform(question: String): String =
        ScottsTechAi.askWithCatalog(question, "seller-ai-chat")
}
