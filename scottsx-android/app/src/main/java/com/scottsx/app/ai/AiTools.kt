package com.scottsx.app.ai

import com.scottsx.app.data.CartStore
import com.scottsx.app.data.ChatCache
import com.scottsx.app.data.LiveMarketplace
import com.scottsx.app.data.SellerLive
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.AgreementRevision
import com.scottsx.app.data.domain.Currency
import com.scottsx.app.data.domain.PaymentMethod
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.TimelineEventType
import com.scottsx.app.data.domain.TransactionStatus

/**
 * Stage 4 — Secure AI tool layer.
 *
 * Every method here is a single, narrow read/update that:
 *  - checks the caller is authenticated (Session.userId)
 *  - checks the caller has the required role for the data
 *  - returns ONLY data the caller is authorized to see
 *  - never invents missing fields; returns nothing if unknown
 *
 * The AI calls these via [AiToolRegistry]. Authorization is enforced
 * HERE — the AI's system prompt can be manipulated; this code cannot.
 */
object AiTools {

    data class CallerContext(val userId: String, val role: Role) {
        companion object {
            fun currentOrNull(): CallerContext? {
                val u = Session.userIdOrNull() ?: return null
                val r = Session.roleOrNull() ?: return null
                return CallerContext(u, r)
            }
        }
    }

    fun require(): CallerContext =
        CallerContext.currentOrNull() ?: error("AI tool called without an authenticated caller")

    fun requireRole(role: Role): CallerContext {
        val ctx = require()
        require(ctx.role == role) { "AI tool requires role=$role, caller is ${ctx.role}" }
        return ctx
    }

    // =================================================================
    // Catalogue tools (buyer + seller can read)
    // =================================================================

    fun searchProducts(query: String, maxPriceUgx: Long? = null, categoryName: String? = null): String {
        val ctx = require()
        val q = query.trim().lowercase()
        val results = LiveMarketplace.products.value.filter { p ->
            q.isBlank() || (p.name + " " + p.shortDescription + " " + p.brand.name + " " + p.category.displayName)
                .lowercase().contains(q)
        }
            .let { base ->
                if (maxPriceUgx != null) base.filter { it.priceUgx <= maxPriceUgx } else base
            }
            .let { base ->
                if (categoryName != null) base.filter { it.category.displayName.equals(categoryName, ignoreCase = true) } else base
            }
        AiPersonalizationStore.recordSearch(query)
        results.firstOrNull()?.category?.let { AiPersonalizationStore.recordCategory(it) }
        results.forEach { AiPersonalizationStore.recordPrice(it.priceUgx) }
        if (results.isEmpty()) {
            return "{\"results\": [], \"note\": \"No products in ScottsTechX matched '$query'.\"}"
        }
        val items = results.take(8).map {
            "{\"id\":\"${it.id}\",\"name\":\"${escape(it.name)}\",\"priceUgx\":${it.priceUgx},\"category\":\"${escape(it.category.displayName)}\",\"rating\":${it.rating},\"stock\":${it.stock},\"sellerId\":\"${it.seller.id}\",\"sellerName\":\"${escape(it.seller.name)}\",\"location\":\"${escape(it.location)}\"}"
        }
        return "{\"results\": [${items.joinToString(",")}], \"count\": ${results.size}}"
    }

    fun getProduct(productId: String): String {
        require()
        val p = LiveMarketplace.byId(productId)
            ?: return "{\"error\":\"Product $productId not found.\"}"
        return "{\"id\":\"${p.id}\",\"name\":\"${escape(p.name)}\",\"priceUgx\":${p.priceUgx},\"oldPriceUgx\":${p.oldPriceUgx ?: "null"},\"category\":\"${escape(p.category.displayName)}\",\"sellerId\":\"${p.seller.id}\",\"sellerName\":\"${escape(p.seller.name)}\",\"rating\":${p.rating},\"ratingCount\":${p.ratingCount},\"stock\":${p.stock},\"location\":\"${escape(p.location)}\",\"description\":\"${escape(p.shortDescription)}\"}"
    }

    fun getProductReviews(productId: String): String {
        require()
        // Aggregates come from the live catalogue row; per-review text
        // renders in-app on the PDP — never fabricated here.
        val p = LiveMarketplace.byId(productId)
            ?: return "{\"error\":\"Product $productId not found.\"}"
        return "{\"productId\":\"${p.id}\",\"average\":${p.rating},\"count\":${p.ratingCount},\"note\":\"live catalogue aggregate\"}"
    }

    fun getProductAvailability(productId: String): String {
        require()
        val p = LiveMarketplace.byId(productId)
            ?: return "{\"error\":\"Product $productId not found.\"}"
        val status = when {
            p.stock <= 0 -> "OutOfStock"
            p.stock <= 3 -> "LowStock"
            else -> "InStock"
        }
        val msg = if (p.stock in 1..5) "Only ${p.stock} left" else ""
        return "{\"productId\":\"${p.id}\",\"name\":\"${escape(p.name)}\",\"stock\":${p.stock},\"status\":\"$status\",\"message\":\"${escape(msg)}\"}"
    }

    fun getMarketplaceCategories(): String {
        require()
        val cats = com.scottsx.app.data.domain.ProductCategory.values()
        val list = cats.joinToString(",") { "\"${it.displayName}\"" }
        return "{\"categories\":[$list]}"
    }

    // =================================================================
    // Seller-side tools
    // =================================================================

    private fun sellerFacts(sellerId: String): List<com.scottsx.app.data.domain.Product> =
        LiveMarketplace.products.value.filter { it.seller.id == sellerId }

    fun getSeller(sellerId: String): String {
        require()
        val list = sellerFacts(sellerId)
        if (list.isEmpty()) return "{\"error\":\"Seller $sellerId not found in the live catalogue.\"}"
        val s = list.first().seller
        return "{\"id\":\"${s.id}\",\"name\":\"${escape(s.name)}\",\"verified\":${s.verified},\"rating\":${s.rating},\"productCount\":${list.size},\"location\":\"${escape(s.location)}\"}"
    }

    fun getSellerReviews(sellerId: String): String {
        require()
        val list = sellerFacts(sellerId)
        if (list.isEmpty()) return "{\"sellerId\":\"$sellerId\",\"reviews\":[]}"
        val rated = list.filter { it.ratingCount > 0 }
        val agg = if (rated.isEmpty()) 0.0
            else rated.sumOf { it.rating.toDouble() * it.ratingCount } / rated.sumOf { it.ratingCount }
        return "{\"sellerId\":\"$sellerId\",\"average\":${"%.2f".format(agg)},\"ratingCount\":${rated.sumOf { it.ratingCount }},\"note\":\"aggregate over live catalogue rows\"}"
    }

    fun getSellerStore(sellerId: String): String {
        require()
        val list = sellerFacts(sellerId)
        if (list.isEmpty()) return "{\"error\":\"Seller $sellerId not found in the live catalogue.\"}"
        val s = list.first().seller
        val cats = list.groupBy { it.category }.toList()
            .sortedByDescending { (_, v) -> v.size }
            .joinToString(",") { (cat, v) -> "\"${escape(cat.displayName)}\":${v.size}" }
        return "{\"id\":\"${s.id}\",\"name\":\"${escape(s.name)}\",\"verified\":${s.verified},\"rating\":${s.rating},\"productCount\":${list.size},\"categoryCounts\":{$cats}}"
    }

    fun getSellerAnalytics(): String {
        val ctx = requireRole(Role.SELLER)
        SellerLive.warm()
        val d = SellerLive.dashboard
            ?: return "{\"status\":\"refreshing\",\"note\":\"live stats are being fetched; ask again in a moment\"}"
        val s = d.stats
        return "{\"revenueUgx\":${s.revenueUgx},\"revenue30Ugx\":${s.revenue30Ugx},\"orders\":${s.orders},\"orders30\":${s.orders30},\"avgOrderValueUgx\":${s.avgOrderValueUgx},\"totalProducts\":${s.totalProducts},\"lowStock\":${s.lowStock},\"outOfStock\":${s.outOfStock},\"followers\":${s.followers},\"totalViews\":${s.totalViews},\"unreadMessages\":${s.unreadMessages}}"
    }

    fun getSellerInventory(): String {
        val ctx = requireRole(Role.SELLER)
        SellerLive.warm()
        val items = SellerLive.products?.products
            ?: return "{\"status\":\"refreshing\",\"note\":\"live inventory is being fetched; ask again in a moment\"}"
        if (items.isEmpty()) return "{\"products\":[]}"
        val list = items.take(20).map {
            "{\"id\":\"${it.id}\",\"name\":\"${escape(it.name)}\",\"priceUgx\":${it.priceUgx},\"stock\":${it.stock},\"category\":\"${escape(it.category.displayName)}\"}"
        }
        return "{\"count\":${items.size},\"products\":[${list.joinToString(",")}]}"
    }

    fun getSellerOrders(): String {
        val ctx = requireRole(Role.SELLER)
        SellerLive.warm()
        val orders = SellerLive.orders
            ?: return "{\"status\":\"refreshing\",\"note\":\"live orders are being fetched; ask again in a moment\"}"
        val list = orders.take(10).map {
            "{\"id\":\"${it.id}\",\"buyer\":\"${escape(it.buyerName)}\",\"product\":\"${escape(it.title)}\",\"quantity\":${it.quantity},\"totalUgx\":${it.amount * it.quantity},\"status\":\"${it.status}\",\"placed\":\"${escape(it.createdAt.take(16))}\"}"
        }
        return "{\"count\":${orders.size},\"orders\":[${list.joinToString(",")}]}"
    }

    // =================================================================
    // Nearby tools (buyer side)
    // =================================================================

    fun findNearbyProducts(query: String, maxPriceUgx: Long? = null): String {
        val ctx = requireRole(Role.BUYER)
        val nq = query.trim().lowercase()
        val pool = LiveMarketplace.products.value.filter { p ->
            (nq.isBlank() || (p.name + " " + p.shortDescription + " " + p.brand.name).lowercase().contains(nq)) &&
                (maxPriceUgx == null || p.priceUgx <= maxPriceUgx)
        }
        val results = pool.take(10).map { p ->
            "{\"productId\":\"${p.id}\",\"name\":\"${escape(p.name)}\",\"priceUgx\":${p.priceUgx},\"sellerId\":\"${p.seller.id}\",\"sellerName\":\"${escape(p.seller.name)}\",\"sellerLocation\":\"${escape(p.seller.location)}\",\"inStock\":${(p.stock > 0)}}"
        }
        return "{\"results\":[${results.joinToString(",")}],\"count\":${results.size}}"
    }

    fun findNearbySellers(categoryName: String? = null): String {
        val ctx = requireRole(Role.BUYER)
        val sellers = LiveMarketplace.products.value
            .groupBy { it.seller.id }
            .map { (_, list) -> list.first().seller to list }
            .let { base ->
                if (categoryName != null) base.filter { (_, list) ->
                    list.any { it.category.displayName.equals(categoryName, ignoreCase = true) }
                } else base
            }
        if (sellers.isEmpty()) return "{\"results\":[]}"
        val items = sellers.take(10).map { (s, list) ->
            "{\"sellerId\":\"${s.id}\",\"name\":\"${escape(s.name)}\",\"verified\":${s.verified},\"rating\":${s.rating},\"productCount\":${list.size},\"location\":\"${escape(s.location)}\"}"
        }
        return "{\"count\":${sellers.size},\"sellers\":[${items.joinToString(",")}]}"
    }

    // =================================================================
    // Messaging tool (buyer + seller)
    // =================================================================

    fun getConversationContext(threadId: String): String {
        val ctx = require()
        ChatCache.warm()
        val conv = ChatCache.conversation(threadId)
            ?: return "{\"error\":\"Thread $threadId not found.\"}"
        val messages = ChatCache.messagesFor(threadId)
        val items = messages.takeLast(20).map { m ->
            val mine = m.senderUid == ctx.userId
            "{\"mine\":$mine,\"sender\":\"${escape(if (mine) "me" else conv.otherPartyDisplayName)}\",\"text\":\"${escape(m.content.take(160))}\",\"time\":\"${escape(m.createdAt.take(16))}\"}"
        }
        return "{\"threadId\":\"${conv.conversationId}\",\"otherParty\":\"${escape(conv.otherPartyDisplayName)}\",\"productId\":\"${conv.productId ?: "null"}\",\"messages\":[${items.joinToString(",")}]}"
    }

    fun summarizeConversation(threadId: String): String {
        val ctx = require()
        ChatCache.warm()
        val conv = ChatCache.conversation(threadId)
            ?: return "{\"error\":\"Thread $threadId not found.\"}"
        val messages = ChatCache.messagesFor(threadId)
        val parts = mutableListOf<String>()
        parts += "Thread between ${if (ctx.role == Role.BUYER) "you (buyer) and ${conv.otherPartyDisplayName} (seller)" else "${conv.otherPartyDisplayName} (buyer) and you (seller)"}."
        parts += "${messages.size} message(s) exchanged."
        val product = conv.productId?.let { LiveMarketplace.byId(it) }
        if (product != null) parts += "Discussing: ${product.name} (UGX ${product.priceUgx})."
        val agreements = TransactionStore.agreements.filter { it.threadId == threadId }
        if (agreements.isNotEmpty()) {
            val ag = agreements.last()
            val rev = ag.latestRevision
            if (rev != null) {
                parts += "Latest agreement: ${rev.productName} × ${rev.quantity} at UGX ${rev.agreedPriceUgx}, status ${ag.statusLabel()}."
            }
        }
        return "{\"summary\":\"${escape(parts.joinToString(" "))}\"}"
    }

    // =================================================================
    // Transaction tools (buyer + seller — must be a party)
    // =================================================================

    fun listMyTransactions(): String {
        val ctx = require()
        val ags = TransactionStore.agreementsForUser(ctx.userId, ctx.role)
        if (ags.isEmpty()) return "{\"transactions\":[],\"note\":\"No transactions yet.\"}"
        val list = ags.take(10).map {
            val rev = it.latestRevision
            "{\"id\":\"${it.id}\",\"status\":\"${it.status.name}\",\"statusLabel\":\"${escape(it.statusLabel())}\",\"productName\":\"${escape(rev?.productName ?: "")}\",\"quantity\":${rev?.quantity ?: 0},\"totalUgx\":${(rev?.quantity ?: 0) * (rev?.agreedPriceUgx ?: 0)},\"updatedAt\":${it.updatedAt}}"
        }
        return "{\"count\":${ags.size},\"transactions\":[${list.joinToString(",")}]}"
    }

    fun getTransactionAgreement(transactionId: String): String {
        val ctx = require()
        val ag = TransactionStore.agreementById(transactionId)
            ?: return "{\"error\":\"Transaction $transactionId not found.\"}"
        if (ag.buyerId != ctx.userId && ag.sellerId != ctx.userId) {
            return "{\"error\":\"Caller is not a party to this transaction.\"}"
        }
        val rev = ag.latestRevision ?: return "{\"error\":\"No revision.\"}"
        return "{\"id\":\"${ag.id}\",\"status\":\"${ag.status.name}\",\"statusLabel\":\"${escape(ag.statusLabel())}\",\"currentRevision\":${ag.currentRevision},\"buyerConfirmed\":${rev.buyerConfirmedAt != null},\"sellerConfirmed\":${rev.sellerConfirmedAt != null},\"revision\":${revisionJson(rev)}}"
    }

    fun summarizeAgreement(transactionId: String): String {
        val ctx = require()
        val ag = TransactionStore.agreementById(transactionId)
            ?: return "{\"error\":\"Transaction not found.\"}"
        if (ag.buyerId != ctx.userId && ag.sellerId != ctx.userId) return "{\"error\":\"Not your transaction.\"}"
        val rev = ag.latestRevision ?: return "{\"error\":\"No revision.\"}"
        val parts = mutableListOf<String>()
        parts += "Status: ${ag.statusLabel()}. Revision ${ag.currentRevision}."
        parts += "Product: ${rev.productName} × ${rev.quantity}."
        parts += "Agreed price: ${TransactionStore.ugxFormat(rev.agreedPriceUgx)}."
        if (rev.paymentMethod != null) parts += "Payment: ${rev.paymentMethod.label}."
        if (rev.deliveryMethod != null) parts += "Delivery: ${rev.deliveryMethod.label}."
        if (!rev.pickupOrDeliveryLocation.isNullOrBlank()) parts += "Location: ${rev.pickupOrDeliveryLocation}."
        if (!rev.expectedDateLabel.isNullOrBlank()) parts += "Date: ${rev.expectedDateLabel} ${rev.expectedTimeLabel ?: ""}."
        parts += "Buyer confirmed: ${if (rev.buyerConfirmedAt != null) "yes" else "no"}. Seller confirmed: ${if (rev.sellerConfirmedAt != null) "yes" else "no"}."
        return "{\"summary\":\"${escape(parts.joinToString(" "))}\"}"
    }

    fun transactionReadiness(transactionId: String): String {
        val ctx = require()
        val ag = TransactionStore.agreementById(transactionId)
            ?: return "{\"error\":\"Transaction not found.\"}"
        if (ag.buyerId != ctx.userId && ag.sellerId != ctx.userId) return "{\"error\":\"Not your transaction.\"}"
        val missing = TransactionStore.readinessFor(transactionId)
        return "{\"ready\":${missing.isEmpty()},\"missing\":[${missing.joinToString(",") { "\"$it\"" }}]}"
    }

    /** Draft ONLY — does not persist until user confirms. */
    fun createTransactionDraft(
        sellerId: String,
        productId: String,
        quantity: Int,
        priceUgx: Long,
        paymentMethod: String? = null,
        deliveryMethod: String? = null,
        pickupOrDeliveryLocation: String? = null,
        expectedDateLabel: String? = null,
        expectedTimeLabel: String? = null,
        additionalNotes: String? = null,
    ): String {
        val ctx = require()
        val product = LiveMarketplace.byId(productId)
            ?: return "{\"error\":\"Product $productId not found.\"}"
        val seller = sellerFacts(sellerId).firstOrNull()?.seller
        val (buyerId, buyerDisplayName) = if (ctx.role == Role.BUYER) ctx.userId to Session.displayNameOrEmpty() else "draft" to "Draft"
        val sellerIdSafe = if (ctx.role == Role.SELLER) ctx.userId else sellerId
        val sellerName = if (ctx.role == Role.SELLER) Session.displayNameOrEmpty() else seller?.name ?: "Seller"
        val draft = TransactionStore.createAgreement(
            buyerId = buyerId,
            buyerDisplayName = buyerDisplayName,
            sellerId = sellerIdSafe,
            sellerDisplayName = sellerName,
            productId = product.id,
            productName = product.name,
            quantity = quantity,
            agreedPriceUgx = priceUgx,
            createdByRole = ctx.role,
            paymentMethod = paymentMethod?.let { runCatching { PaymentMethod.valueOf(it.uppercase()) }.getOrNull() },
            deliveryMethod = deliveryMethod?.let { runCatching { com.scottsx.app.data.domain.DeliveryMethod.valueOf(it.uppercase()) }.getOrNull() },
            pickupOrDeliveryLocation = pickupOrDeliveryLocation,
            expectedDateLabel = expectedDateLabel,
            expectedTimeLabel = expectedTimeLabel,
            additionalNotes = additionalNotes,
        )
        return "{\"draftId\":\"${draft.id}\",\"status\":\"${draft.status.name}\",\"message\":\"Draft created. The user must confirm both parties before this transaction is final.\"}"
    }

    // =================================================================
    // Receipt tools
    // =================================================================

    fun listMyReceipts(): String {
        val ctx = require()
        val rs = TransactionStore.receiptsForUser(ctx.userId, ctx.role)
        if (rs.isEmpty()) return "{\"receipts\":[],\"note\":\"No receipts yet.\"}"
        val list = rs.take(10).map {
            "{\"number\":\"${it.number}\",\"totalUgx\":${it.totalUgx},\"payment\":\"${escape(it.paymentMethod.label)}\",\"date\":\"${escape(it.issuedAtLabel)}\",\"buyer\":\"${escape(it.buyerDisplayName)}\",\"status\":\"${it.status.name}\"}"
        }
        return "{\"count\":${rs.size},\"receipts\":[${list.joinToString(",")}]}"
    }

    fun getReceipt(receiptNumber: String): String {
        val ctx = require()
        val r = TransactionStore.receiptByNumber(receiptNumber)
            ?: return "{\"error\":\"Receipt $receiptNumber not found.\"}"
        if (r.buyerId != ctx.userId && r.sellerId != ctx.userId) return "{\"error\":\"Not your receipt.\"}"
        return "{\"number\":\"${r.number}\",\"seller\":\"${escape(r.sellerDisplayName)}\",\"buyer\":\"${escape(r.buyerDisplayName)}\",\"totalUgx\":${r.totalUgx},\"payment\":\"${escape(r.paymentMethod.label)}\",\"paymentRecordedBySeller\":${r.paymentRecordedBySeller},\"date\":\"${escape(r.issuedAtLabel)}\",\"status\":\"${r.status.name}\"}"
    }

    /** Draft a receipt from a transaction; requires seller confirmation before final. */
    fun generateReceiptDraft(transactionId: String, template: String = "MODERN"): String {
        val ctx = requireRole(Role.SELLER)
        val templateEnum = runCatching { com.scottsx.app.data.domain.ReceiptTemplate.valueOf(template.uppercase()) }.getOrNull()
            ?: com.scottsx.app.data.domain.ReceiptTemplate.MODERN
        val ag = TransactionStore.agreementById(transactionId)
            ?: return "{\"error\":\"Transaction not found.\"}"
        if (ag.sellerId != ctx.userId) return "{\"error\":\"Not your transaction.\"}"
        val draft = TransactionStore.generateReceiptFromAgreement(
            agreementId = transactionId,
            template = templateEnum,
            sellerStoreName = Session.storeNameOrEmpty(),
            sellerStoreLocation = Session.locationOrEmpty(),
            notes = ag.latestRevision?.additionalNotes,
        )
        return "{\"draftNumber\":\"${draft?.number ?: "draft"}\",\"status\":\"draft\",\"message\":\"Receipt draft generated. The seller must confirm before it becomes a real receipt.\"}"
    }

    /** Ad-hoc receipt from the seller's own line items (no transaction reference). */
    fun generateAdHocReceiptDraft(
        buyerDisplayName: String,
        lines: List<Pair<String, Long>>,
        paymentMethod: String,
        deliveryMethod: String,
        notes: String? = null,
    ): String {
        val ctx = requireRole(Role.SELLER)
        val pm = runCatching { PaymentMethod.valueOf(paymentMethod.uppercase()) }.getOrNull()
            ?: return "{\"error\":\"Unknown payment method: $paymentMethod\"}"
        val dm = runCatching { com.scottsx.app.data.domain.DeliveryMethod.valueOf(deliveryMethod.uppercase()) }.getOrNull()
            ?: return "{\"error\":\"Unknown delivery method: $deliveryMethod\"}"
        val receiptLines = lines.map { (productId, qty) ->
            val p = LiveMarketplace.byId(productId)
                ?: return "{\"error\":\"Product $productId not found.\"}"
            com.scottsx.app.data.domain.ReceiptLine(
                productId = p.id,
                productName = p.name,
                quantity = qty.toInt(),
                unitPriceUgx = p.priceUgx,
            )
        }
        val draft = TransactionStore.createAdHocReceipt(
            sellerId = ctx.userId,
            sellerDisplayName = Session.displayNameOrEmpty(),
            sellerStoreName = Session.storeNameOrEmpty(),
            sellerStoreLocation = Session.locationOrEmpty(),
            buyerDisplayName = buyerDisplayName,
            lines = receiptLines,
            paymentMethod = pm,
            deliveryMethod = dm,
            template = com.scottsx.app.data.domain.ReceiptTemplate.MODERN,
            notes = notes,
        )
        return "{\"draftNumber\":\"${draft.number}\",\"status\":\"draft\",\"totalUgx\":${draft.totalUgx},\"message\":\"Draft created. The seller must confirm before finalizing.\"}"
    }

    // =================================================================
    // Personalization
    // =================================================================

    fun getUserPreferences(): String {
        require()
        return "{\"preferences\":\"${escape(AiPersonalizationStore.summaryForRole(if (Session.roleOrNull() == Role.SELLER) Role.SELLER else Role.BUYER))}\"}"
    }

    fun clearUserMemory(): String {
        require()
        AiPersonalizationStore.clearMemory()
        return "{\"cleared\":true}"
    }

    fun setPersonalizationEnabled(enabled: Boolean): String {
        require()
        AiPersonalizationStore.setEnabled(enabled)
        return "{\"enabled\":$enabled}"
    }

    fun getCart(): String {
        val ctx = requireRole(Role.BUYER)
        val items = CartStore.items.value.map {
            val p = LiveMarketplace.byId(it.productId)
            "{\"productId\":\"${it.productId}\",\"name\":\"${escape(p?.name ?: it.productId)}\",\"quantity\":${it.quantity},\"unitPriceUgx\":${p?.priceUgx ?: 0},\"variantId\":\"${it.variantId ?: ""}\"}"
        }
        return "{\"items\":[${items.joinToString(",")}]}"
    }

    fun getWishlist(): String {
        val ctx = requireRole(Role.BUYER)
        val ids = com.scottsx.app.data.WishlistStore.ids.value.toList().take(10)
        return "{\"items\":[${ids.joinToString(",") { "{\"productId\":\"$it\"}" }}]}"
    }

    fun getMyNotifications(): String {
        val ctx = require()
        ChatCache.warm()
        val conv = ChatCache.conversations
        return if (conv == null) "{\"count\":null,\"note\":\"live inbox is refreshing\"}"
            else "{\"count\":${conv.sumOf { it.unreadCount }}}"
    }

    fun summarizeDispute(disputeId: String): String {
        val ctx = require()
        val d = TransactionStore.disputes.firstOrNull { it.id == disputeId }
            ?: return "{\"error\":\"Dispute $disputeId not found.\"}"
        val ag = TransactionStore.agreementById(d.transactionId)
            ?: return "{\"error\":\"Related transaction missing.\"}"
        if (ag.buyerId != ctx.userId && ag.sellerId != ctx.userId) return "{\"error\":\"Not your dispute.\"}"
        val rev = ag.latestRevision
        val receipt = ag.let { TransactionStore.receipts.firstOrNull { r -> r.transactionId == ag.id } }
        val parts = mutableListOf<String>()
        parts += "Dispute: ${d.reason.label}."
        parts += "Raised by ${if (d.raisedByRole == Role.BUYER) "buyer" else "seller"}: ${d.description}."
        if (rev != null) parts += "Recorded agreement: ${rev.productName} × ${rev.quantity} at UGX ${rev.agreedPriceUgx}. Payment: ${rev.paymentMethod?.label ?: "not recorded"}. Delivery: ${rev.deliveryMethod?.label ?: "not recorded"}."
        if (receipt != null) parts += "Receipt on file: ${receipt.number} for UGX ${receipt.totalUgx}."
        parts += "ScottsTechX does NOT decide legal liability. The buyer and seller should resolve this directly, using the recorded agreement and receipt as evidence."
        return "{\"summary\":\"${escape(parts.joinToString(" "))}\"}"
    }

    // =================================================================
    // JSON helpers
    // =================================================================

    private fun revisionJson(r: AgreementRevision): String {
        val pm = r.paymentMethod?.let { "\"${it.name}\"" } ?: "null"
        val dm = r.deliveryMethod?.let { "\"${it.name}\"" } ?: "null"
        return "{\"revisionNumber\":${r.revisionNumber},\"productId\":\"${r.productId}\",\"productName\":\"${escape(r.productName)}\",\"quantity\":${r.quantity},\"agreedPriceUgx\":${r.agreedPriceUgx},\"paymentMethod\":$pm,\"deliveryMethod\":$dm,\"pickupOrDeliveryLocation\":\"${escape(r.pickupOrDeliveryLocation ?: "")}\",\"expectedDateLabel\":\"${escape(r.expectedDateLabel ?: "")}\",\"expectedTimeLabel\":\"${escape(r.expectedTimeLabel ?: "")}\",\"buyerConfirmedAt\":${r.buyerConfirmedAt ?: "null"},\"sellerConfirmedAt\":${r.sellerConfirmedAt ?: "null"}}"
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ").trim()
}