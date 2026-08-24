package com.scottsx.app.ai

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.firebase.Mirror
import kotlinx.coroutines.launch

/**
 * Stage 4 — Adaptive AI personalization store.
 *
 * Tracks legitimate user interaction signals so the AI can adapt its
 * suggestions without inferring sensitive characteristics. The user
 * can disable personalization and clear the memory at any time via
 * the AI Personalization screen.
 *
 * Stored signals (no sensitive characteristics inferred):
 *  - frequently viewed categories
 *  - recently searched products
 *  - preferred price range
 *  - followed sellers
 *  - preferred language
 *  - seller product categories (sellers)
 *  - quick action usage
 *
 * The user can see every signal stored here, and clear any of it.
 */
object AiPersonalizationStore {

    /** Whether personalization is enabled. The user can toggle this. */
    val enabled = mutableStateOf(true)

    // Background coroutine for fire-and-forget backend signals.
    private val personalisationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )


    /** Categories the user frequently browses. Limited size. */
    val frequentCategories = mutableStateListOf<ProductCategory>()

    /** Recently searched product names. Limited size. */
    val recentSearches = mutableStateListOf<String>()

    /** Highest and lowest price the user has recently shown interest in. */
    val preferredPriceLowUgx = mutableStateOf<Long?>(null)
    val preferredPriceHighUgx = mutableStateOf<Long?>(null)

    /** Sellers the user has followed (buyer side). */
    val followedSellers = mutableStateListOf<String>()

    /** Sellers' product categories — for seller AI adaptation. */
    val sellerCategories = mutableStateListOf<ProductCategory>()

    /** Quick-action usage counters, used to surface relevant shortcuts. */
    val quickActionCounts = mutableStateOf<Map<String, Int>>(emptyMap())

    /** Language hint (e.g. "en"). */
    val preferredLanguage = mutableStateOf("en")

    /** Theme preference (passed through; stored elsewhere too). */
    val themePreference = mutableStateOf("system")

    /** Number of times AI has been opened. Used to surface Ask AI shortcut. */
    val aiOpenedCount = mutableStateOf(0)

    // ----------------------------------------------------------------
    // Recording signals
    // ----------------------------------------------------------------

    fun recordCategory(category: ProductCategory, maxList: Int = 5) {
        if (!enabled.value) return
        frequentCategories.remove(category)
        frequentCategories.add(0, category)
        while (frequentCategories.size > maxList) frequentCategories.removeAt(frequentCategories.size - 1)

        mirror()
        personalisationScope.launch { com.scottsx.app.data.remote.V2Client.recordSignal("category", category.name) }
    }

    fun recordSearch(query: String, maxList: Int = 8) {
        if (!enabled.value) return
        if (query.isBlank()) return
        val q = query.trim()
        recentSearches.remove(q)
        recentSearches.add(0, q)
        while (recentSearches.size > maxList) recentSearches.removeAt(recentSearches.size - 1)

        mirror()
        personalisationScope.launch { com.scottsx.app.data.remote.V2Client.recordSignal("search", q) }
    }

    fun recordPrice(ugx: Long) {
        if (!enabled.value) return
        val low = preferredPriceLowUgx.value
        val high = preferredPriceHighUgx.value
        preferredPriceLowUgx.value = if (low == null) ugx else minOf(low, ugx)
        preferredPriceHighUgx.value = if (high == null) ugx else maxOf(high, ugx)
    
        mirror()
    }

    fun recordFollow(sellerId: String) {
        if (!enabled.value) return
        if (!followedSellers.contains(sellerId)) followedSellers.add(sellerId)

        mirror()
        personalisationScope.launch { com.scottsx.app.data.remote.V2Client.recordSignal("seller", sellerId) }
    }

    fun recordUnfollow(sellerId: String) {
        followedSellers.remove(sellerId)
    
        mirror()
    }

    fun recordSellerCategory(category: ProductCategory, maxList: Int = 4) {
        if (!enabled.value) return
        sellerCategories.remove(category)
        sellerCategories.add(0, category)
        while (sellerCategories.size > maxList) sellerCategories.removeAt(sellerCategories.size - 1)
    
        mirror()
    }

    fun recordQuickAction(action: String) {
        if (!enabled.value) return
        val map = quickActionCounts.value.toMutableMap()
        map[action] = (map[action] ?: 0) + 1
        quickActionCounts.value = map
    
        mirror()
    }

    fun recordAiOpened() {
        aiOpenedCount.value = aiOpenedCount.value + 1
    
        mirror()
    }

    fun recordLanguage(lang: String) {
        preferredLanguage.value = lang
    
        mirror()
    }

    fun recordTheme(theme: String) {
        themePreference.value = theme
    
        mirror()
    }

    // ----------------------------------------------------------------
    // User-facing controls
    // ----------------------------------------------------------------

    fun setEnabled(v: Boolean) {
        enabled.value = v
        if (!v) clearMemory()
        mirror()
    }

    fun clearMemory() {
        frequentCategories.clear()
        recentSearches.clear()
        preferredPriceLowUgx.value = null
        preferredPriceHighUgx.value = null
        followedSellers.clear()
        sellerCategories.clear()
        quickActionCounts.value = emptyMap()
        // language / theme / aiOpenedCount are user-set preferences, not memory
        mirror()
    }

    // ----------------------------------------------------------------
    // Query helpers used by the AI
    // ----------------------------------------------------------------

    /**
     * Render a compact preference summary the AI can read into its
     * system prompt. Strict — never includes sensitive characteristics.
     */
    fun summaryForRole(role: Role): String {
        if (!enabled.value) return "Personalization disabled by user."
        val sb = StringBuilder()
        when (role) {
            Role.BUYER -> {
                if (frequentCategories.isNotEmpty())
                    sb.append("- Frequently browses: ").append(frequentCategories.joinToString(", ") { it.displayName }).append("\n")
                if (recentSearches.isNotEmpty())
                    sb.append("- Recent searches: ").append(recentSearches.take(3).joinToString(", ")).append("\n")
                if (preferredPriceLowUgx.value != null && preferredPriceHighUgx.value != null)
                    sb.append("- Price range of interest: ").append(preferredPriceLowUgx.value).append("-").append(preferredPriceHighUgx.value).append(" UGX\n")
                if (followedSellers.isNotEmpty())
                    sb.append("- Followed sellers: ").append(followedSellers.size).append("\n")
            }
            Role.SELLER -> {
                if (sellerCategories.isNotEmpty())
                    sb.append("- Seller's main categories: ").append(sellerCategories.joinToString(", ") { it.displayName }).append("\n")
            }
        }
        if (quickActionCounts.value.isNotEmpty())
            sb.append("- Quick action usage: ").append(quickActionCounts.value.entries.sortedByDescending { it.value }.take(3).joinToString(", ") { it.key + "×" + it.value }).append("\n")
        if (aiOpenedCount.value > 0)
            sb.append("- AI opened ").append(aiOpenedCount.value).append("×\n")
        return if (sb.isEmpty()) "No personalization signals recorded yet." else sb.toString()
    }

    private fun mirror() {
        Mirror.aiMemory(
            recentSearches = recentSearches.toList(),
            topCategories = frequentCategories.map { it.displayName },
            followedSellers = followedSellers.toList(),
            priceLowUgx = preferredPriceLowUgx.value,
            priceHighUgx = preferredPriceHighUgx.value,
            aiOpenCount = aiOpenedCount.value,
        )
    }
}