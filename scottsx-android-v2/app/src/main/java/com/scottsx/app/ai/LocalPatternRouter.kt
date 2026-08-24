package com.scottsx.app.ai

import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.AgreementRevision
import com.scottsx.app.data.domain.Currency
import com.scottsx.app.data.domain.DeliveryMethod
import com.scottsx.app.data.domain.PaymentMethod
import com.scottsx.app.data.domain.Role
import java.util.Locale

/**
 * Stage 4 — Deterministic local router for ScottsTechX AI.
 *
 * Handles the structured marketplace commands with real data and
 * zero hallucination. The free LLM is only used for genuinely free-form
 * chat; every command that maps to a specific tool runs locally.
 *
 * The router is intentionally simple — pattern match on the user's
 * input, dispatch to [AiTools], format a friendly reply. Patterns are
 * case-insensitive and tolerant of small wording variations.
 */
internal object LocalPatternRouter {

    private val reNumber: Regex = Regex("""\b(\d{1,3}(?:[,]\d{3})+|\d+)(?:[.,]\d+)?\b""")
    private val reUgxUnder = Regex("""(?i)\b(?:under|below|less than|<)\s*([\d,\.]+)(?:\s*(?:k|m|million|thousand|ugx|shs?|/-))?""")
    private val reUgxPrice = Regex("""(?i)([\d,\.]+)\s*(?:k|m|million|thousand|ugx|shs?|/-)?""")

    fun respond(
        userMessage: String,
        role: Role,
        context: ScottsTechAi.Context,
    ): ScottsTechAi.Reply? {
        val m = userMessage.trim().lowercase(Locale.ENGLISH)
        if (m.isBlank()) return null

        // ------ summarize / what did we agree ------
        if (matchesAny(m, listOf("summarize", "summary", "what did we agree", "what have we agreed", "what was agreed"))) {
            val tid = context.transactionId
                ?: TransactionStore.agreementsForUser(Session.userIdOrNull() ?: "", role).firstOrNull()?.id
            if (tid != null) {
                val j = AiTools.summarizeAgreement(tid)
                val parsed = parse(j)
                val text = parsed["summary"] ?: j
                return ScottsTechAi.Reply(
                    text = "Here is the recorded agreement:\n\n$text",
                    source = ScottsTechAi.Source.LOCAL_TOOL,
                    toolCalls = listOf("summarizeAgreement"),
                    mentionedTransactionId = tid,
                    suggestedActions = listOf(
                        ScottsTechAi.SuggestedAction("Open transaction", ScottsTechAi.SuggestedAction.Kind.OPEN_TRANSACTION)
                    ),
                )
            }
        }

        // ------ readiness / missing info ------
        if (matchesAny(m, listOf("missing", "ready", "what's missing", "what is missing", "complete my agreement", "missing info"))) {
            val tid = context.transactionId
                ?: TransactionStore.agreementsForUser(Session.userIdOrNull() ?: "", role).firstOrNull()?.id
            if (tid != null) {
                val j = AiTools.transactionReadiness(tid)
                val parsed = parse(j)
                val missing = (parsed["missing"] ?: emptyList<String>()) as? List<*>
                return ScottsTechAi.Reply(
                    text = if (missing.isNullOrEmpty()) {
                        "Your agreement is complete and ready."
                    } else {
                        "Your agreement is missing: ${missing.joinToString(", ")}."
                    },
                    source = ScottsTechAi.Source.LOCAL_TOOL,
                    toolCalls = listOf("transactionReadiness"),
                    mentionedTransactionId = tid,
                )
            }
        }

        // ------ find product near me under X ------
        if (matchesAny(m, listOf("near me", "nearby", "around me", "close to me"))) {
            val cap = extractPriceUgx(m)
            // Try to extract a product name from context or first noun
            val query = extractQueryTerm(m, listOf("near me", "nearby", "around me", "close to me", "find me a", "find", "show me", "show"))
                ?: context.productId?.let { MarketplaceDataSource.productById(it)?.name }
                ?: "phone"
            val j = AiTools.findNearbyProducts(query, cap)
            val parsed = parse(j)
            val items = (parsed["results"] ?: emptyList<Any>()) as? List<*>
            if (items.isNullOrEmpty()) {
                return ScottsTechAi.Reply(
                    text = "No nearby products matched \"$query\"" + (cap?.let { " under ${TransactionStore.ugxFormat(it)}" } ?: "") + ".",
                    source = ScottsTechAi.Source.LOCAL_TOOL,
                    toolCalls = listOf("findNearbyProducts"),
                    suggestedActions = listOf(ScottsTechAi.SuggestedAction("Open Nearby", ScottsTechAi.SuggestedAction.Kind.OPEN_NEARBY)),
                )
            }
            val lines = items.take(5).mapNotNull { it as? Map<*, *> }.joinToString("\n") { row ->
                val name = row["name"] ?: "?"
                val price = row["priceUgx"]
                val dist = row["distanceKm"]
                val seller = row["sellerName"]
                "- $name — ${TransactionStore.ugxFormat((price as? Number)?.toLong() ?: 0)} • ${seller} • ~${dist} km away"
            }
            val productIds = items.take(5).mapNotNull { (it as? Map<*, *>)?.get("productId") as? String }
            val sellerIds = items.take(5).mapNotNull { (it as? Map<*, *>)?.get("sellerId") as? String }
            return ScottsTechAi.Reply(
                text = "Here are ${items.size} nearby match(es)${cap?.let { " under ${TransactionStore.ugxFormat(it)}" } ?: ""}:\n\n$lines",
                source = ScottsTechAi.Source.LOCAL_TOOL,
                toolCalls = listOf("findNearbyProducts"),
                mentionedProductIds = productIds,
                mentionedSellerIds = sellerIds,
                suggestedActions = listOf(
                    ScottsTechAi.SuggestedAction("Open Nearby", ScottsTechAi.SuggestedAction.Kind.OPEN_NEARBY)
                ),
            )
        }

        // ------ search products ------
        if (matchesAny(m, listOf("find me", "search for", "show me", "i want", "i need", "looking for"))) {
            val query = extractQueryTerm(m, listOf("find me", "search for", "show me", "i want a", "i want", "i need a", "i need", "looking for a", "looking for"))
                ?: "phone"
            val cap = extractPriceUgx(m)
            val j = AiTools.searchProducts(query, cap)
            val parsed = parse(j)
            val items = (parsed["results"] ?: emptyList<Any>()) as? List<*>
            if (items.isNullOrEmpty()) {
                return ScottsTechAi.Reply(
                    text = "I couldn't find a product matching \"$query\"" + (cap?.let { " under ${TransactionStore.ugxFormat(it)}" } ?: "") + ".",
                    source = ScottsTechAi.Source.LOCAL_TOOL,
                    toolCalls = listOf("searchProducts"),
                )
            }
            val lines = items.take(5).mapNotNull { it as? Map<*, *> }.joinToString("\n") { row ->
                val name = row["name"] ?: "?"
                val price = row["priceUgx"]
                val seller = row["sellerName"]
                val stock = row["stock"]
                "- $name — ${TransactionStore.ugxFormat((price as? Number)?.toLong() ?: 0)} • $seller • in stock: $stock"
            }
            return ScottsTechAi.Reply(
                text = "Here are ${items.size} match(es):\n\n$lines",
                source = ScottsTechAi.Source.LOCAL_TOOL,
                toolCalls = listOf("searchProducts"),
                mentionedProductIds = items.take(5).mapNotNull { (it as? Map<*, *>)?.get("productId") as? String },
                mentionedSellerIds = items.take(5).mapNotNull { (it as? Map<*, *>)?.get("sellerId") as? String },
            )
        }

        // ------ cheapest / lowest price / who has ------
        if (matchesAny(m, listOf("cheapest", "lowest price", "best price", "who has", "who sells", "where can i buy"))) {
            val query = extractQueryTerm(m, listOf("cheapest", "lowest price", "best price", "who has", "who sells", "where can i buy"))
                ?: "phone"
            val j = AiTools.searchProducts(query)
            val parsed = parse(j)
            val items = (parsed["results"] ?: emptyList<Any>()) as? List<*>
            if (items.isNullOrEmpty()) {
                return ScottsTechAi.Reply(
                    text = "I couldn't find any \"$query\" to compare.",
                    source = ScottsTechAi.Source.LOCAL_TOOL,
                    toolCalls = listOf("searchProducts"),
                )
            }
            val rows = items.mapNotNull { it as? Map<*, *> }
                .map { it to ((it["priceUgx"] as? Number)?.toLong() ?: Long.MAX_VALUE) }
                .sortedBy { it.second }
            val cheapest = rows.firstOrNull() ?: return null
            val productId = (cheapest.first["productId"] as? String) ?: ""
            val sellerId = (cheapest.first["sellerId"] as? String) ?: ""
            val name = cheapest.first["name"]
            val price = TransactionStore.ugxFormat(cheapest.second)
            val sellerName = cheapest.first["sellerName"]
            return ScottsTechAi.Reply(
                text = "The cheapest $query in ScottsTechX is **$name** at $price from $sellerName.",
                source = ScottsTechAi.Source.LOCAL_TOOL,
                toolCalls = listOf("searchProducts"),
                mentionedProductIds = listOf(productId),
                mentionedSellerIds = listOf(sellerId),
                suggestedActions = listOf(
                    ScottsTechAi.SuggestedAction("View product", ScottsTechAi.SuggestedAction.Kind.OPEN_PRODUCT),
                    ScottsTechAi.SuggestedAction("View seller", ScottsTechAi.SuggestedAction.Kind.OPEN_SELLER),
                ),
            )
        }

        // ------ create receipt draft from natural language ------
        if (matchesAny(m, listOf("create a receipt", "make a receipt", "new receipt", "receipt for"))) {
            if (role != Role.SELLER) {
                return ScottsTechAi.Reply(
                    text = "Receipts can only be created by the seller.",
                    source = ScottsTechAi.Source.LOCAL_RULE,
                )
            }
            val draft = parseReceiptDraftFromText(m)
            if (draft == null) {
                return ScottsTechAi.Reply(
                    text = "I couldn't parse the receipt details from your message. Try: \"Create a receipt for Sarah. 2 phone cases at 40000 each. Mobile Money.\"",
                    source = ScottsTechAi.Source.LOCAL_FALLBACK,
                )
            }
            val lines = draft.items.map { (productName, qty) ->
                val p = MarketplaceDataSource.allProducts.firstOrNull { it.name.equals(productName, ignoreCase = true) }
                    ?: return ScottsTechAi.Reply(
                        text = "I couldn't find a product named \"$productName\" in ScottsTechX. I never invent products.",
                        source = ScottsTechAi.Source.LOCAL_RULE,
                    )
                com.scottsx.app.data.domain.ReceiptLine(
                    productId = p.id,
                    productName = p.name,
                    quantity = qty,
                    unitPriceUgx = p.priceUgx,
                )
            }
            val receipt = TransactionStore.createAdHocReceipt(
                sellerId = Session.userIdOrNull() ?: "draft",
                sellerDisplayName = Session.displayNameOrEmpty(),
                sellerStoreName = Session.storeNameOrEmpty(),
                sellerStoreLocation = Session.locationOrEmpty(),
                buyerDisplayName = draft.buyer,
                lines = lines,
                paymentMethod = draft.payment,
                deliveryMethod = draft.delivery,
                template = com.scottsx.app.data.domain.ReceiptTemplate.MODERN,
                pickupOrDeliveryLocation = draft.location,
            )
            return ScottsTechAi.Reply(
                text = "Receipt draft prepared (${receipt.number}). Total ${TransactionStore.ugxFormat(receipt.totalUgx)}. " +
                        "Open the receipt to confirm and send to ${draft.buyer}.",
                source = ScottsTechAi.Source.LOCAL_TOOL,
                toolCalls = listOf("createAdHocReceiptDraft"),
                mentionedReceiptNumber = receipt.number,
                suggestedActions = listOf(ScottsTechAi.SuggestedAction("Open receipt", ScottsTechAi.SuggestedAction.Kind.OPEN_RECEIPT)),
            )
        }

        // ------ my transactions ------
        if (matchesAny(m, listOf("my transactions", "my orders", "my sales", "transactions list", "show transactions"))) {
            val j = AiTools.listMyTransactions()
            val parsed = parse(j)
            val list = (parsed["transactions"] ?: emptyList<Any>()) as? List<*>
            if (list.isNullOrEmpty()) {
                return ScottsTechAi.Reply(
                    text = if (role == Role.SELLER) "You have no sales yet." else "You have no transactions yet.",
                    source = ScottsTechAi.Source.LOCAL_TOOL,
                    toolCalls = listOf("listMyTransactions"),
                    suggestedActions = listOf(ScottsTechAi.SuggestedAction("Open transactions", ScottsTechAi.SuggestedAction.Kind.OPEN_TRANSACTION)),
                )
            }
            val lines = list.take(5).mapNotNull { it as? Map<*, *> }.joinToString("\n") { row ->
                "- ${row["productName"]} • ${row["statusLabel"]} • ${TransactionStore.ugxFormat(((row["totalUgx"] as? Number)?.toLong() ?: 0))}"
            }
            return ScottsTechAi.Reply(
                text = "Here are your transactions:\n\n$lines",
                source = ScottsTechAi.Source.LOCAL_TOOL,
                toolCalls = listOf("listMyTransactions"),
                mentionedTransactionId = list.firstOrNull()?.let { (it as? Map<*, *>)?.get("id") as? String },
                suggestedActions = listOf(ScottsTechAi.SuggestedAction("Open transactions", ScottsTechAi.SuggestedAction.Kind.OPEN_TRANSACTION)),
            )
        }

        // ------ my receipts ------
        if (matchesAny(m, listOf("my receipts", "list receipts", "show receipts"))) {
            val j = AiTools.listMyReceipts()
            val parsed = parse(j)
            val list = (parsed["receipts"] ?: emptyList<Any>()) as? List<*>
            if (list.isNullOrEmpty()) {
                return ScottsTechAi.Reply(
                    text = "You have no receipts yet.",
                    source = ScottsTechAi.Source.LOCAL_TOOL,
                    toolCalls = listOf("listMyReceipts"),
                )
            }
            val lines = list.take(5).mapNotNull { it as? Map<*, *> }.joinToString("\n") { row ->
                "- ${row["number"]} • ${TransactionStore.ugxFormat(((row["totalUgx"] as? Number)?.toLong() ?: 0))} • ${row["date"]}"
            }
            return ScottsTechAi.Reply(
                text = "Here are your receipts:\n\n$lines",
                source = ScottsTechAi.Source.LOCAL_TOOL,
                toolCalls = listOf("listMyReceipts"),
                mentionedReceiptNumber = (list.firstOrNull() as? Map<*, *>)?.get("number") as? String,
            )
        }

        // ------ create transaction proposal ------
        if (matchesAny(m, listOf("propose a transaction", "create agreement", "create proposal", "transaction proposal", "agree to buy", "buy for"))) {
            if (role != Role.BUYER) {
                return ScottsTechAi.Reply(
                    text = "Only buyers can initiate a transaction proposal from here. Sellers create receipts.",
                    source = ScottsTechAi.Source.LOCAL_RULE,
                )
            }
            val pid = context.productId
            if (pid == null) {
                return ScottsTechAi.Reply(
                    text = "Open a product first, then ask me to create a proposal from there.",
                    source = ScottsTechAi.Source.LOCAL_RULE,
                    suggestedActions = listOf(ScottsTechAi.SuggestedAction("Browse products", ScottsTechAi.SuggestedAction.Kind.OPEN_NEARBY)),
                )
            }
            val p = MarketplaceDataSource.productById(pid)
                ?: return ScottsTechAi.Reply(
                    text = "I couldn't find that product in ScottsTechX.",
                    source = ScottsTechAi.Source.LOCAL_RULE,
                )
            val priceUgx = extractPriceUgx(m) ?: p.priceUgx
            val qty = extractInt(m, listOf("qty", "quantity", "x"), default = 1)
            val ag = TransactionStore.createAgreement(
                buyerId = Session.userIdOrNull() ?: "draft",
                buyerDisplayName = Session.displayNameOrEmpty(),
                sellerId = p.seller.id,
                sellerDisplayName = p.seller.name,
                productId = p.id,
                productName = p.name,
                quantity = qty,
                agreedPriceUgx = priceUgx,
                createdByRole = Role.BUYER,
            )
            return ScottsTechAi.Reply(
                text = "Transaction draft created (${ag.id}) for ${qty} × ${p.name} at ${TransactionStore.ugxFormat(priceUgx)}. " +
                        "Both parties must confirm before this is final.",
                source = ScottsTechAi.Source.LOCAL_TOOL,
                toolCalls = listOf("createTransactionDraft"),
                mentionedTransactionId = ag.id,
                suggestedActions = listOf(ScottsTechAi.SuggestedAction("Open transaction", ScottsTechAi.SuggestedAction.Kind.OPEN_TRANSACTION)),
            )
        }

        // ------ personality / preferences ------
        if (matchesAny(m, listOf("clear my ai memory", "forget my preferences", "personalization", "ai memory"))) {
            AiPersonalizationStore.clearMemory()
            return ScottsTechAi.Reply(
                text = "Done. I've cleared your AI memory. Your preferences are gone.",
                source = ScottsTechAi.Source.LOCAL_RULE,
            )
        }

        return null
    }

    // =================================================================
    // Helpers
    // =================================================================

    private fun matchesAny(haystack: String, needles: List<String>): Boolean {
        return needles.any { haystack.contains(it.lowercase(Locale.ENGLISH)) }
    }

    private fun extractInt(text: String, hints: List<String>, default: Int): Int {
        hints.forEach { hint ->
            val idx = text.indexOf(hint)
            if (idx >= 0) {
                val tail = text.substring(idx + hint.length)
                val n = reNumber.find(tail)?.value?.replace(",", "")?.toIntOrNull()
                if (n != null) return n
            }
        }
        return reNumber.find(text)?.value?.replace(",", "")?.toIntOrNull() ?: default
    }

    private fun extractPriceUgx(text: String): Long? {
        // under/below style first
        reUgxUnder.find(text)?.let { m ->
            val raw = m.groupValues[1].replace(",", "")
            val n = raw.toDoubleOrNull() ?: return@let
            val multiplier = when {
                text.contains("million", ignoreCase = true) -> 1_000_000.0
                text.contains("thousand", ignoreCase = true) || text.endsWith("k") -> 1_000.0
                else -> 1.0
            }
            return (n * multiplier).toLong()
        }
        // Generic "X UGX" or "X k" style
        reUgxPrice.find(text)?.let { m ->
            val raw = m.groupValues[1].replace(",", "")
            val n = raw.toDoubleOrNull() ?: return@let
            val multiplier = when {
                text.contains("million", ignoreCase = true) -> 1_000_000.0
                text.contains("thousand", ignoreCase = true) || text.endsWith("k") -> 1_000.0
                text.contains("ugx", ignoreCase = true) -> 1.0
                else -> 1.0
            }
            return (n * multiplier).toLong()
        }
        return null
    }

    private fun extractQueryTerm(text: String, prefixes: List<String>): String? {
        prefixes.forEach { p ->
            val idx = text.indexOf(p)
            if (idx >= 0) {
                val tail = text.substring(idx + p.length).trim()
                val cleaned = tail.split(".", "?", "!", "\n").first().trim()
                // Remove trailing "under X", "near me", "in Kampala" etc.
                val cleaned2 = cleaned
                    .replace(Regex("(?i)under [\\d,.]+ ?(ugx|shs|k|m|million|thousand)?\\b.*"), "")
                    .replace(Regex("(?i)below [\\d,.]+ ?(ugx|shs|k|m|million|thousand)?\\b.*"), "")
                    .replace(Regex("(?i)less than [\\d,.]+ ?(ugx|shs|k|m|million|thousand)?\\b.*"), "")
                    .replace(Regex("(?i)near me\\b.*"), "")
                    .replace(Regex("(?i)in kampala\\b.*"), "")
                    .trim()
                if (cleaned2.isNotBlank()) return cleaned2
            }
        }
        return null
    }

    private data class ReceiptDraft(
        val buyer: String,
        val items: List<Pair<String, Int>>,
        val payment: PaymentMethod,
        val delivery: DeliveryMethod,
        val location: String?,
    )

    private fun parseReceiptDraftFromText(text: String): ReceiptDraft? {
        // Buyer: "receipt for Sarah", "for John"
        val buyerMatch = Regex("""(?i)(?:receipt\s+for|for)\s+([A-Z][a-zA-Z]+)""").find(text)
            ?: return null
        val buyer = buyerMatch.groupValues[1]

        // Items: "2 phone cases at 40000 each" OR "3 shirts at 25000"
        val itemPattern = Regex("""(\d+)\s+([a-zA-Z][a-zA-Z ]+?)\s+at\s+([\d,]+)""")
        val items = itemPattern.findAll(text).map { m ->
            val qty = m.groupValues[1].toIntOrNull() ?: 1
            val name = m.groupValues[2].trim()
            qty to name
        }.toList()
        if (items.isEmpty()) return null

        // Payment
        val payment = when {
            text.contains("cash", ignoreCase = true) -> PaymentMethod.CASH
            text.contains("mobile money", ignoreCase = true) -> PaymentMethod.MOBILE_MONEY
            text.contains("bank", ignoreCase = true) -> PaymentMethod.BANK_TRANSFER
            text.contains("card", ignoreCase = true) -> PaymentMethod.CARD_EXTERNAL
            else -> PaymentMethod.CASH
        }

        // Delivery
        val delivery = when {
            text.contains("pickup", ignoreCase = true) -> DeliveryMethod.BUYER_PICKUP
            text.contains("delivery", ignoreCase = true) -> DeliveryMethod.SELLER_DELIVERY
            text.contains("courier", ignoreCase = true) -> DeliveryMethod.COURIER
            else -> DeliveryMethod.BUYER_PICKUP
        }

        val locMatch = Regex("""(?i)(?:in|at)\s+([A-Z][a-zA-Z ]+?)(?:\.|$|[\n])""").find(text)
        return ReceiptDraft(buyer, items.map { (q, n) -> n to q }, payment, delivery, locMatch?.groupValues?.get(1)?.trim())
    }

    /**
     * Tiny lenient JSON parser for the simple flat-json we produce.
     * Avoids the dependency on org.json.* and is sufficient for our
     * tool output shape. Returns Map<String, Any?> where values may be
     * Long, Double, Boolean, String, List<Any?>, or null.
     */
    fun parse(json: String): Map<String, Any?> {
        val trimmed = json.trim().removePrefix("{").removeSuffix("}").trim()
        if (trimmed.isBlank()) return emptyMap()
        val tokens = tokenize(trimmed)
        return parseObjectBody(tokens)
    }

    private sealed class Token {
        data class Str(val v: String) : Token()
        data class Num(val v: String) : Token()
        data class KW(val v: String) : Token()        // true, false, null
        data class Punc(val v: Char) : Token()
        data class Bracket(val open: Boolean) : Token()
    }

    private fun tokenize(s: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '"' -> {
                    val sb = StringBuilder()
                    i++
                    while (i < s.length && s[i] != '"') {
                        if (s[i] == '\\' && i + 1 < s.length) {
                            sb.append(s[i + 1])
                            i += 2
                        } else {
                            sb.append(s[i])
                            i++
                        }
                    }
                    i++ // closing "
                    out.add(Token.Str(sb.toString()))
                }
                c.isDigit() || (c == '-' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val sb = StringBuilder()
                    while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == '-' || s[i] == 'e' || s[i] == 'E' || s[i] == '+')) {
                        sb.append(s[i])
                        i++
                    }
                    out.add(Token.Num(sb.toString()))
                }
                c.isLetter() -> {
                    val sb = StringBuilder()
                    while (i < s.length && (s[i].isLetter() || s[i] == '_')) {
                        sb.append(s[i])
                        i++
                    }
                    out.add(Token.KW(sb.toString()))
                }
                c == '[' || c == ']' -> {
                    out.add(Token.Bracket(c == '['))
                    i++
                }
                c == '{' || c == '}' -> {
                    out.add(Token.Bracket(c == '['))
                    out.add(Token.Bracket(false))
                    i++
                }
                c == ',' || c == ':' -> {
                    out.add(Token.Punc(c))
                    i++
                }
                else -> i++
            }
        }
        return out
    }

    private fun parseObjectBody(tokens: List<Token>): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        var i = 0
        while (i < tokens.size) {
            if (i >= tokens.size) break
            val k = (tokens.getOrNull(i) as? Token.Str)?.v ?: break
            i++
            if (tokens.getOrNull(i) is Token.Punc && (tokens[i] as Token.Punc).v == ':') i++
            val (v, next) = readValue(tokens, i)
            out[k] = v
            i = next
            if (tokens.getOrNull(i) is Token.Punc && (tokens[i] as Token.Punc).v == ',') i++
        }
        return out
    }

    private fun readValue(tokens: List<Token>, start: Int): Pair<Any?, Int> {
        val t = tokens.getOrNull(start) ?: return null to start
        return when (t) {
            is Token.Str -> t.v to (start + 1)
            is Token.Num -> {
                val n = t.v.toLongOrNull() ?: (t.v.toDoubleOrNull() ?: 0.0)
                n to (start + 1)
            }
            is Token.KW -> when (t.v) {
                "true" -> true to (start + 1)
                "false" -> false to (start + 1)
                "null" -> null to (start + 1)
                else -> t.v to (start + 1)
            }
            is Token.Bracket -> {
                if (t.open) {
                    // array
                    val arr = mutableListOf<Any?>()
                    var i = start + 1
                    while (i < tokens.size) {
                        if (tokens[i] is Token.Bracket && !(tokens[i] as Token.Bracket).open) {
                            return arr to (i + 1)
                        }
                        val (v, ni) = readValue(tokens, i)
                        if (v != null || ni > i) arr.add(v)
                        i = ni
                        if (tokens.getOrNull(i) is Token.Punc && (tokens[i] as Token.Punc).v == ',') i++
                    }
                    arr to i
                } else {
                    null to (start + 1)
                }
            }
            else -> null to (start + 1)
        }
    }
}