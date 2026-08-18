package com.scottsx.app.ai

import com.scottsx.app.UserPrefs
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.V2Client

/**
 * Shared AI helper used by BOTH assistants (buyer + seller).
 *
 * Before calling the backend, it builds a compact textual summary of the live
 * catalog and prepends it to the user prompt so the LLM can answer
 * store-specific questions accurately.
 */
object ScottsTechAi {

    val buyerQuickReplies = listOf(
        "What's near me?",
        "Cheapest Samsung phone",
        "Summarize my transactions",
        "Flash deals right now",
        "How does delivery work?",
        "Recommend a gift under UGX 100,000",
    )

    /** Compact catalog brief, e.g. "- iPhone 15 Pro (Electronics) UGX 4,500,000 from Tech Hub Uganda". */
    fun catalogBrief(products: List<Product>, limit: Int = 40): String =
        products.take(limit).joinToString("\n") { p ->
            "- ${p.title} (${p.category}) UGX ${p.priceUgx} from ${p.seller.name}" +
                (if (p.isFlashDeal) " [FLASH -${p.discountPercent}%]" else "")
        }

    /**
     * Ask the assistant with live-catalog context attached.
     * `screen` is passed through to the backend for per-screen behaviour.
     */
    suspend fun askWithCatalog(prompt: String, screen: String): String {
        val catalog = V2Client.fetchProductsList()
        val brief = catalogBrief(catalog)
        val personalised = if (UserPrefs.aiPersonalisationOn) {
            val name = UserPrefs.aiUserName.ifBlank { SessionHolder.displayName() }
            val city = UserPrefs.aiCity
            if (name.isNotBlank()) " (User: $name, based in $city)" else ""
        } else ""

        val full = "Live marketplace catalog (${catalog.size} products):\n$brief\n\nUser$personalised: $prompt"
        return V2Client.ask(full, screen)?.text ?: "Sorry, the assistant is unreachable right now."
    }

    private object SessionHolder {
        fun displayName(): String = com.scottsx.app.SessionCache.user.value?.displayName ?: ""
    }
}
