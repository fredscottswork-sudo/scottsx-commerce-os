package com.scottsx.app.ai

import com.scottsx.app.data.domain.Role

/**
 * ScottsTechX Capability Registry (Stage 4).
 *
 * Every major marketplace feature is described to the AI with a stable
 * shape so the AI understands WHAT it can do, WHEN it can do it, WHO it
 * can do it for, and WHAT IT MUST NOT pretend to know.
 *
 * The registry is the canonical source of:
 *   - feature description
 *   - available AI actions (mapped to secure tool calls)
 *   - required permissions
 *   - allowed user roles
 *   - safety restrictions
 *
 * Adding a new feature in the future:
 *   1. Add a new `Feature` constant
 *   2. Add a [Capability] entry to [ALL]
 *   3. Implement the corresponding [AiTool] in the secure tool layer
 *
 * The AI's system prompt is generated from this registry — the AI never
 * invents capabilities, and it never sees a tool name it does not have.
 */
object CapabilityRegistry {

    /** Stable ids for each major ScottsTechX feature. */
    object Feature {
        const val MARKETPLACE = "marketplace"
        const val PRODUCTS = "products"
        const val SELLERS = "sellers"
        const val NEARBY = "nearby"
        const val SEARCH = "search"
        const val WISHLIST = "wishlist"
        const val CART = "cart"
        const val MESSAGING = "messaging"
        const val TRANSACTIONS = "transactions"
        const val RECEIPTS = "receipts"
        const val DELIVERY = "delivery"
        const val REVIEWS = "reviews"
        const val PROFILE = "profile"
        const val STORE_PROFILE = "store_profile"
        const val NOTIFICATIONS = "notifications"
        const val PROMOTIONS = "promotions"
        const val SELLER_TOOLS = "seller_tools"
        const val SELLER_ANALYTICS = "seller_analytics"
        const val SELLER_INVENTORY = "seller_inventory"
        const val SELLER_ORDERS = "seller_orders"
        const val AI_ASSISTANT = "ai_assistant"
        const val PERSONALIZATION = "personalization"
        const val DISPUTES = "disputes"
        const val CATEGORIES = "categories"
    }

    /**
     * A single capability registered with the AI. The shape is
     * deliberately minimal — the AI gets a markdown rendering of this
     * for the system prompt, not the raw object.
     */
    data class Capability(
        val id: String,
        val name: String,
        val purpose: String,
        val aiActions: List<String>,
        val permissions: List<String>,
        val roles: Set<Role>,
        val relatedFeatures: List<String>,
        val safetyRules: List<String>,
        val notAllowedTo: List<String>,
    )

    /** The full registry, evaluated once. */
    val ALL: List<Capability> by lazy { build() }

    /** Quick lookup by feature id. */
    fun byId(id: String): Capability? = ALL.firstOrNull { it.id == id }

    /** All capabilities available to a given role. */
    fun availableTo(role: Role): List<Capability> =
        ALL.filter { role in it.roles }

    /** Render the registry as a compact markdown block for the system prompt. */
    fun toPromptMarkdown(role: Role): String {
        val caps = availableTo(role)
        if (caps.isEmpty()) return "(no capabilities registered for role $role)"
        return buildString {
            caps.forEach { c ->
                append("### ").append(c.name).append("\n")
                append("id: `").append(c.id).append("`\n")
                append("purpose: ").append(c.purpose).append("\n")
                if (c.aiActions.isNotEmpty()) {
                    append("ai_actions: ")
                        .append(c.aiActions.joinToString(", "))
                        .append("\n")
                }
                if (c.permissions.isNotEmpty()) {
                    append("permissions: ")
                        .append(c.permissions.joinToString(", "))
                        .append("\n")
                }
                if (c.relatedFeatures.isNotEmpty()) {
                    append("related_features: ")
                        .append(c.relatedFeatures.joinToString(", "))
                        .append("\n")
                }
                if (c.safetyRules.isNotEmpty()) {
                    append("safety: ").append(c.safetyRules.joinToString(" ")).append("\n")
                }
                if (c.notAllowedTo.isNotEmpty()) {
                    append("must_not: ")
                        .append(c.notAllowedTo.joinToString(" "))
                        .append("\n")
                }
                append("\n")
            }
        }
    }

    private fun build(): List<Capability> = listOf(
        Capability(
            id = Feature.MARKETPLACE,
            name = "ScottsTechX Marketplace",
            purpose = "Buyers and sellers discover and transact. ScottsTechX is not a payment processor; payment happens between the parties.",
            aiActions = listOf("getMarketplaceOverview"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.SELLERS, Feature.TRANSACTIONS),
            safetyRules = listOf("never claim ScottsTechX processed a payment"),
            notAllowedTo = listOf("invent products", "invent sellers", "invent prices"),
        ),
        Capability(
            id = Feature.PRODUCTS,
            name = "Products",
            purpose = "Buyers browse and search products; sellers manage their catalogue.",
            aiActions = listOf("searchProducts", "getProduct", "getProductReviews", "getProductAvailability"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.SELLERS, Feature.REVIEWS, Feature.NEARBY, Feature.CART, Feature.WISHLIST),
            safetyRules = listOf("return only products that exist in the marketplace"),
            notAllowedTo = listOf("invent product names", "invent stock counts", "invent prices"),
        ),
        Capability(
            id = Feature.SELLERS,
            name = "Sellers",
            purpose = "Each product belongs to a seller. Sellers have a public storefront.",
            aiActions = listOf("getSeller", "getSellerReviews", "getSellerStore"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.STORE_PROFILE, Feature.NEARBY, Feature.PRODUCTS),
            safetyRules = listOf("only return sellers that exist"),
            notAllowedTo = listOf("reveal another seller's private analytics to a buyer"),
        ),
        Capability(
            id = Feature.NEARBY,
            name = "Nearby",
            purpose = "Find products, sellers and stores close to the user. Uses device location with explicit permission.",
            aiActions = listOf("findNearbyProducts", "findNearbySellers"),
            permissions = listOf("location"),
            roles = setOf(Role.BUYER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.SELLERS, Feature.DELIVERY),
            safetyRules = listOf("never invent nearby sellers", "honor denied location permission"),
            notAllowedTo = listOf("fabricate distance", "fabricate availability"),
        ),
        Capability(
            id = Feature.SEARCH,
            name = "Search",
            purpose = "Search the live product catalogue by name, brand, category, subcategory.",
            aiActions = listOf("searchProducts", "getMarketplaceCategories"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.CATEGORIES),
            safetyRules = listOf(),
            notAllowedTo = listOf(),
        ),
        Capability(
            id = Feature.WISHLIST,
            name = "Wishlist",
            purpose = "Buyer saves products they may buy later. Private to the buyer.",
            aiActions = listOf("getWishlist", "addToWishlist", "removeFromWishlist"),
            permissions = listOf(),
            roles = setOf(Role.BUYER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.CART),
            safetyRules = listOf("only show the current user's wishlist"),
            notAllowedTo = listOf("reveal another user's wishlist"),
        ),
        Capability(
            id = Feature.CART,
            name = "Cart",
            purpose = "Buyer's pending purchase list. Private to the buyer.",
            aiActions = listOf("getCart", "addToCart", "removeFromCart"),
            permissions = listOf(),
            roles = setOf(Role.BUYER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.TRANSACTIONS),
            safetyRules = listOf("only show the current user's cart"),
            notAllowedTo = listOf("reveal another user's cart"),
        ),
        Capability(
            id = Feature.MESSAGING,
            name = "Messaging",
            purpose = "Buyers and sellers exchange messages and structured proposals about a specific product.",
            aiActions = listOf("getConversationContext", "summarizeConversation"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.TRANSACTIONS, Feature.RECEIPTS),
            safetyRules = listOf(
                "never reveal another user's private messages",
                "summarize only what the current user is a party to",
            ),
            notAllowedTo = listOf("fabricate messages", "fabricate agreements"),
        ),
        Capability(
            id = Feature.TRANSACTIONS,
            name = "Transactions",
            purpose = "A structured agreement between a buyer and a seller about a specific product, quantity, price, payment method and delivery.",
            aiActions = listOf(
                "createTransactionDraft",
                "updateTransactionDraft",
                "getTransactionAgreement",
                "confirmTransaction",
                "listMyTransactions",
                "summarizeAgreement",
            ),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.MESSAGING, Feature.RECEIPTS, Feature.DELIVERY, Feature.DISPUTES),
            safetyRules = listOf(
                "both buyer and seller must confirm before status changes to Confirmed",
                "changing price / quantity / product / delivery / payment creates a new revision",
                "never silently change an agreed transaction",
            ),
            notAllowedTo = listOf("pretend ScottsTechX processed payment", "auto-finalize financial agreements"),
        ),
        Capability(
            id = Feature.RECEIPTS,
            name = "Receipts",
            purpose = "A structured receipt recording what was agreed between the parties. ScottsTechX does not process payment — the receipt records what was agreed and what the seller recorded as received.",
            aiActions = listOf("generateReceiptDraft", "getReceipt", "listMyReceipts", "duplicateReceiptAsTemplate"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.TRANSACTIONS, Feature.MESSAGING, Feature.DELIVERY),
            safetyRules = listOf(
                "never finalize a receipt without explicit seller confirmation",
                "label the receipt clearly: 'Payment recorded by seller' (not 'processed by ScottsTechX')",
                "receipt number is unique per receipt",
            ),
            notAllowedTo = listOf("invent receipt numbers", "pretend ScottsTechX processed payment"),
        ),
        Capability(
            id = Feature.DELIVERY,
            name = "Delivery",
            purpose = "Document the agreed delivery or pickup arrangement. ScottsTechX does not physically perform delivery.",
            aiActions = listOf("recordDeliveryAgreement", "updateDeliveryStatus"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.TRANSACTIONS, Feature.NEARBY),
            safetyRules = listOf("do not fabricate GPS or delivery status"),
            notAllowedTo = listOf("pretend to track a courier in real time"),
        ),
        Capability(
            id = Feature.REVIEWS,
            name = "Reviews",
            purpose = "Buyers rate products and sellers. Reviews are public on the product and storefront pages.",
            aiActions = listOf("getProductReviews", "getSellerReviews"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.SELLERS),
            safetyRules = listOf("never invent reviews"),
            notAllowedTo = listOf("fabricate review text", "fabricate rating counts"),
        ),
        Capability(
            id = Feature.PROFILE,
            name = "Buyer Profile",
            purpose = "The buyer's own profile and preferences. Private.",
            aiActions = listOf("getMyProfile", "updateMyPreferences"),
            permissions = listOf(),
            roles = setOf(Role.BUYER),
            relatedFeatures = listOf(Feature.PERSONALIZATION),
            safetyRules = listOf("only show the current user's profile"),
            notAllowedTo = listOf("reveal another user's profile"),
        ),
        Capability(
            id = Feature.STORE_PROFILE,
            name = "Store Profile",
            purpose = "A seller's public storefront and private store settings.",
            aiActions = listOf("getMyStore", "updateStoreSettings"),
            permissions = listOf(),
            roles = setOf(Role.SELLER),
            relatedFeatures = listOf(Feature.SELLERS, Feature.SELLER_INVENTORY, Feature.PROMOTIONS),
            safetyRules = listOf("a seller can only manage their own store"),
            notAllowedTo = listOf("modify another seller's store"),
        ),
        Capability(
            id = Feature.NOTIFICATIONS,
            name = "Notifications",
            purpose = "In-app notifications about orders, messages, transactions and disputes.",
            aiActions = listOf("getMyNotifications"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.TRANSACTIONS, Feature.MESSAGING),
            safetyRules = listOf("only show the current user's notifications"),
            notAllowedTo = listOf(),
        ),
        Capability(
            id = Feature.PROMOTIONS,
            name = "Promotions",
            purpose = "Sellers run promotions (offers, flash sales, discounts, bundles, coupons, featured products).",
            aiActions = listOf("getMyPromotions", "createPromotionDraft"),
            permissions = listOf(),
            roles = setOf(Role.SELLER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.SELLER_TOOLS),
            safetyRules = listOf("promotions must reference real products"),
            notAllowedTo = listOf("create promotions for another seller's products"),
        ),
        Capability(
            id = Feature.SELLER_TOOLS,
            name = "Seller Tools",
            purpose = "Promotion creation, marketing insights and quick actions for the seller.",
            aiActions = listOf("getMyPromotions", "createPromotionDraft"),
            permissions = listOf(),
            roles = setOf(Role.SELLER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.SELLER_ANALYTICS),
            safetyRules = listOf(),
            notAllowedTo = listOf(),
        ),
        Capability(
            id = Feature.SELLER_ANALYTICS,
            name = "Seller Analytics",
            purpose = "Revenue, orders, average order value, best products, sales chart.",
            aiActions = listOf("getSellerAnalytics"),
            permissions = listOf(),
            roles = setOf(Role.SELLER),
            relatedFeatures = listOf(Feature.SELLER_INVENTORY, Feature.SELLER_ORDERS),
            safetyRules = listOf("only show the current seller's own analytics"),
            notAllowedTo = listOf("reveal another seller's analytics"),
        ),
        Capability(
            id = Feature.SELLER_INVENTORY,
            name = "Seller Inventory",
            purpose = "The current seller's product catalogue, stock levels and variants.",
            aiActions = listOf("getSellerInventory"),
            permissions = listOf(),
            roles = setOf(Role.SELLER),
            relatedFeatures = listOf(Feature.PRODUCTS, Feature.SELLER_ANALYTICS),
            safetyRules = listOf("only the current seller's inventory is visible"),
            notAllowedTo = listOf("reveal another seller's inventory"),
        ),
        Capability(
            id = Feature.SELLER_ORDERS,
            name = "Seller Orders",
            purpose = "Incoming orders / sales for the current seller.",
            aiActions = listOf("getSellerOrders"),
            permissions = listOf(),
            roles = setOf(Role.SELLER),
            relatedFeatures = listOf(Feature.TRANSACTIONS, Feature.RECEIPTS),
            safetyRules = listOf(),
            notAllowedTo = listOf(),
        ),
        Capability(
            id = Feature.AI_ASSISTANT,
            name = "ScottsTechX AI",
            purpose = "The in-app AI assistant. Has controlled access to marketplace tools. Never invents information.",
            aiActions = listOf(),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.PERSONALIZATION, Feature.MESSAGING, Feature.TRANSACTIONS),
            safetyRules = listOf(
                "only use information returned by a tool",
                "if a tool returns nothing, say so honestly",
                "label suggestions clearly as 'AI suggestion'",
                "never invent products, prices, sellers, payments, delivery status",
            ),
            notAllowedTo = listOf(
                "process payment",
                "act as escrow",
                "expose another user's private data",
                "make legal liability decisions on a dispute",
            ),
        ),
        Capability(
            id = Feature.PERSONALIZATION,
            name = "Personalization",
            purpose = "Adaptive behaviour based on legitimate user interactions. The user can disable and clear it.",
            aiActions = listOf("getUserPreferences", "clearUserMemory", "setPersonalizationEnabled"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.AI_ASSISTANT),
            safetyRules = listOf(
                "do not infer sensitive personal characteristics",
                "do not create hidden profiles",
                "do not manipulate the user",
                "user can always disable personalization",
            ),
            notAllowedTo = listOf(),
        ),
        Capability(
            id = Feature.DISPUTES,
            name = "Disputes",
            purpose = "If a transaction goes wrong, the user can report it. The AI can summarize the case but does not decide liability.",
            aiActions = listOf("summarizeDispute"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.TRANSACTIONS, Feature.RECEIPTS, Feature.MESSAGING),
            safetyRules = listOf(
                "preserve the agreement, receipt, message history, status history",
                "do not decide legal liability",
            ),
            notAllowedTo = listOf("refund", "decide who is at fault"),
        ),
        Capability(
            id = Feature.CATEGORIES,
            name = "Categories",
            purpose = "Browse the marketplace by category and subcategory.",
            aiActions = listOf("getMarketplaceCategories"),
            permissions = listOf(),
            roles = setOf(Role.BUYER, Role.SELLER),
            relatedFeatures = listOf(Feature.PRODUCTS),
            safetyRules = listOf(),
            notAllowedTo = listOf(),
        ),
    )
}