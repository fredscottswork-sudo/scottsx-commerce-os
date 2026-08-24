package com.scottsx.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.scottsx.app.ai.AiPersonalizationStore
import com.scottsx.app.data.domain.AgreementRevision
import com.scottsx.app.data.domain.Currency
import com.scottsx.app.data.domain.DeliveryMethod
import com.scottsx.app.data.domain.Dispute
import com.scottsx.app.data.domain.DisputeReason
import com.scottsx.app.data.domain.PaymentMethod
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.Receipt
import com.scottsx.app.data.domain.ReceiptLine
import com.scottsx.app.data.domain.ReceiptTemplate
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.TimelineEvent
import com.scottsx.app.data.domain.TimelineEventType
import com.scottsx.app.data.domain.TransactionAgreement
import com.scottsx.app.data.domain.TransactionFactory
import com.scottsx.app.data.domain.TransactionStatus
import com.scottsx.app.data.firebase.Mirror

/**
 * Stage 4 — Central store for transaction agreements, receipts,
 * disputes and timeline events.
 *
 * ScottsTechX does NOT process payments. The store records what both
 * parties agreed to and what the seller marked as received.
 *
 * All state is exposed as observable StateFlow / MutableStateList so
 * Compose can recompose when any agreement changes.
 */
object TransactionStore {

    /** All known agreements (visible only to buyer or seller in the respective role). */
    val agreements = mutableStateListOf<TransactionAgreement>()

    /** All known receipts. */
    val receipts = mutableStateListOf<Receipt>()

    /** All open + closed disputes. */
    val disputes = mutableStateListOf<Dispute>()

    /** Timeline events, indexed by transactionId for fast lookup. */
    val timelineEvents = mutableStateListOf<TimelineEvent>()

    /** Current receipt counter (used to generate unique receipt numbers). */
    private val receiptCounter = mutableStateOf(10482L)

    // =================================================================
    // Agreements
    // =================================================================

    fun createAgreement(
        buyerId: String,
        buyerDisplayName: String,
        sellerId: String,
        sellerDisplayName: String,
        productId: String,
        productName: String,
        quantity: Int,
        agreedPriceUgx: Long,
        threadId: String? = null,
        createdByRole: Role,
        paymentMethod: PaymentMethod? = null,
        deliveryMethod: DeliveryMethod? = null,
        pickupOrDeliveryLocation: String? = null,
        expectedDateLabel: String? = null,
        expectedTimeLabel: String? = null,
        variantId: String? = null,
        variantLabel: String? = null,
        additionalNotes: String? = null,
    ): TransactionAgreement {
        val firstRev = TransactionFactory.buildRevision(
            revisionNumber = 1,
            productId = productId,
            productName = productName,
            quantity = quantity,
            priceUgx = agreedPriceUgx,
            createdBy = if (createdByRole == Role.SELLER) "seller" else "buyer",
            variantId = variantId,
            variantLabel = variantLabel,
            paymentMethod = paymentMethod,
            deliveryMethod = deliveryMethod,
            pickupOrDeliveryLocation = pickupOrDeliveryLocation,
            expectedDateLabel = expectedDateLabel,
            expectedTimeLabel = expectedTimeLabel,
            additionalNotes = additionalNotes,
        )
        val ag = TransactionFactory.buildAgreement(
            buyerId = buyerId,
            buyerDisplayName = buyerDisplayName,
            sellerId = sellerId,
            sellerDisplayName = sellerDisplayName,
            productId = productId,
            firstRevision = firstRev,
            threadId = threadId,
        )
        agreements.add(ag)
        timelineEvents.add(
            TimelineEvent(
                transactionId = ag.id,
                type = TimelineEventType.AGREEMENT_PROPOSED,
                byRole = createdByRole,
            )
        )
        Mirror.transaction(ag)
        Mirror.timelineEvent(
            TimelineEvent(
                transactionId = ag.id,
                type = TimelineEventType.AGREEMENT_PROPOSED,
                byRole = createdByRole,
            ),
            ag.id,
        )
        return ag
    }

    fun agreementById(id: String): TransactionAgreement? =
        agreements.firstOrNull { it.id == id }

    fun agreementsForUser(userId: String, role: Role): List<TransactionAgreement> =
        agreements.filter {
            when (role) {
                Role.BUYER -> it.buyerId == userId
                Role.SELLER -> it.sellerId == userId
            }
        }.sortedByDescending { it.updatedAt }

    /** Important fields that trigger a new revision if changed. */
    private fun isImportantChange(prev: AgreementRevision, next: AgreementRevision): Boolean {
        return prev.productId != next.productId
            || prev.agreedPriceUgx != next.agreedPriceUgx
            || prev.quantity != next.quantity
            || prev.paymentMethod != next.paymentMethod
            || prev.deliveryMethod != next.deliveryMethod
    }

    /**
     * Update an agreement by creating a NEW revision. If only the notes
     * change, do not create a new revision — just update the latest.
     */
    fun updateAgreement(
        agreementId: String,
        updatedByRole: Role,
        quantity: Int? = null,
        agreedPriceUgx: Long? = null,
        paymentMethod: PaymentMethod? = null,
        deliveryMethod: DeliveryMethod? = null,
        pickupOrDeliveryLocation: String? = null,
        expectedDateLabel: String? = null,
        expectedTimeLabel: String? = null,
        additionalNotes: String? = null,
        productId: String? = null,
        productName: String? = null,
    ): TransactionAgreement? {
        val idx = agreements.indexOfFirst { it.id == agreementId }
        if (idx < 0) return null
        val ag = agreements[idx]
        val prev = ag.latestRevision ?: return ag
        val nextRevNum = ag.currentRevision + 1
        val merged = AgreementRevision(
            revisionNumber = nextRevNum,
            productId = productId ?: prev.productId,
            productName = productName ?: prev.productName,
            variantId = prev.variantId,
            variantLabel = prev.variantLabel,
            quantity = quantity ?: prev.quantity,
            agreedPriceUgx = agreedPriceUgx ?: prev.agreedPriceUgx,
            paymentMethod = paymentMethod ?: prev.paymentMethod,
            deliveryMethod = deliveryMethod ?: prev.deliveryMethod,
            pickupOrDeliveryLocation = pickupOrDeliveryLocation ?: prev.pickupOrDeliveryLocation,
            expectedDateLabel = expectedDateLabel ?: prev.expectedDateLabel,
            expectedTimeLabel = expectedTimeLabel ?: prev.expectedTimeLabel,
            additionalNotes = additionalNotes ?: prev.additionalNotes,
            createdAt = System.currentTimeMillis(),
            createdBy = if (updatedByRole == Role.SELLER) "seller" else "buyer",
        )
        val important = isImportantChange(prev, merged)
        val newRevisions = if (important) ag.revisions + merged else ag.revisions
        val updated = ag.copy(
            currentRevision = if (important) nextRevNum else ag.currentRevision,
            revisions = newRevisions,
            status = TransactionStatus.PROPOSED, // any update re-proposes
            updatedAt = System.currentTimeMillis(),
        )
        agreements[idx] = updated
        if (important) {
            val ev = TimelineEvent(
                transactionId = updated.id,
                type = TimelineEventType.AGREEMENT_PROPOSED,
                byRole = updatedByRole,
                note = "Revision $nextRevNum",
            )
            timelineEvents.add(ev)
            Mirror.timelineEvent(ev, updated.id)
        }
        // Mirror updated agreement to Firestore
        Mirror.transaction(updated)
        return updated
    }

    /** Buyer or seller accepts the latest revision. */
    fun acceptLatest(agreementId: String, acceptingRole: Role): TransactionAgreement? {
        val idx = agreements.indexOfFirst { it.id == agreementId }
        if (idx < 0) return null
        val ag = agreements[idx]
        val rev = ag.latestRevision ?: return ag
        val now = System.currentTimeMillis()
        val updatedRev = rev.copy(
            buyerConfirmedAt = if (acceptingRole == Role.BUYER) now else rev.buyerConfirmedAt,
            sellerConfirmedAt = if (acceptingRole == Role.SELLER) now else rev.sellerConfirmedAt,
        )
        val newRevisions = ag.revisions.dropLast(1) + updatedRev
        val bothConfirmed = updatedRev.buyerConfirmedAt != null && updatedRev.sellerConfirmedAt != null
        val newStatus = when {
            bothConfirmed -> TransactionStatus.CONFIRMED
            acceptingRole == Role.BUYER -> TransactionStatus.BUYER_ACCEPTED
            acceptingRole == Role.SELLER -> TransactionStatus.SELLER_ACCEPTED
            else -> ag.status
        }
        val updated = ag.copy(
            revisions = newRevisions,
            status = newStatus,
            updatedAt = now,
        )
        agreements[idx] = updated
        if (bothConfirmed && ag.status != TransactionStatus.CONFIRMED) {
            timelineEvents.add(
                TimelineEvent(
                    transactionId = updated.id,
                    type = TimelineEventType.AGREEMENT_ACCEPTED,
                    byRole = acceptingRole,
                )
            )
        }
        Mirror.transaction(updated)
        return updated
    }

    fun markInProgress(agreementId: String): TransactionAgreement? {
        val idx = agreements.indexOfFirst { it.id == agreementId }
        if (idx < 0) return null
        val ag = agreements[idx]
        if (ag.status != TransactionStatus.CONFIRMED && ag.status != TransactionStatus.BUYER_ACCEPTED && ag.status != TransactionStatus.SELLER_ACCEPTED) return ag
        val updated = ag.copy(status = TransactionStatus.IN_PROGRESS, updatedAt = System.currentTimeMillis())
        agreements[idx] = updated
        timelineEvents.add(
            TimelineEvent(
                transactionId = updated.id,
                type = TimelineEventType.DELIVERY_PICKUP_AGREED,
                byRole = Role.SELLER,
            )
        )
        Mirror.transaction(updated)
        return updated
    }

    fun markCompleted(agreementId: String): TransactionAgreement? {
        val idx = agreements.indexOfFirst { it.id == agreementId }
        if (idx < 0) return null
        val ag = agreements[idx]
        val updated = ag.copy(status = TransactionStatus.COMPLETED, updatedAt = System.currentTimeMillis())
        agreements[idx] = updated
        timelineEvents.add(
            TimelineEvent(
                transactionId = updated.id,
                type = TimelineEventType.TRANSACTION_COMPLETED,
                byRole = Role.SELLER,
            )
        )
        Mirror.transaction(updated)
        return updated
    }

    fun cancel(agreementId: String, reason: String?): TransactionAgreement? {
        val idx = agreements.indexOfFirst { it.id == agreementId }
        if (idx < 0) return null
        val ag = agreements[idx]
        val updated = ag.copy(
            status = TransactionStatus.CANCELLED,
            cancelledReason = reason,
            updatedAt = System.currentTimeMillis(),
        )
        agreements[idx] = updated
        timelineEvents.add(
            TimelineEvent(
                transactionId = updated.id,
                type = TimelineEventType.TRANSACTION_CANCELLED,
                byRole = Role.BUYER,
                note = reason,
            )
        )
        Mirror.transaction(updated)
        return updated
    }

    // =================================================================
    // Receipts
    // =================================================================

    /**
     * Build a receipt from an agreement. The receipt number is unique
     * and increments the global counter. The receipt references the
     * agreement's latest revision.
     */
    fun generateReceiptFromAgreement(
        agreementId: String,
        template: ReceiptTemplate = ReceiptTemplate.MODERN,
        sellerStoreName: String,
        sellerStoreLocation: String,
        buyerEmail: String? = null,
        notes: String? = null,
    ): Receipt? {
        val ag = agreementById(agreementId) ?: return null
        val rev = ag.latestRevision ?: return null
        val number = TransactionFactory.receiptNumber(receiptCounter.value)
        receiptCounter.value = receiptCounter.value + 1
        val subtotal = rev.quantity * rev.agreedPriceUgx
        val r = Receipt(
            number = number,
            sellerId = ag.sellerId,
            sellerDisplayName = ag.sellerDisplayName,
            sellerStoreName = sellerStoreName,
            sellerStoreLocation = sellerStoreLocation,
            buyerId = ag.buyerId,
            buyerDisplayName = ag.buyerDisplayName,
            buyerEmail = buyerEmail,
            issuedAtLabel = formatTodayLabel(),
            lines = listOf(
                ReceiptLine(
                    productId = rev.productId,
                    productName = rev.productName,
                    variantLabel = rev.variantLabel,
                    quantity = rev.quantity,
                    unitPriceUgx = rev.agreedPriceUgx,
                )
            ),
            subtotalUgx = subtotal,
            totalUgx = subtotal,
            paymentMethod = rev.paymentMethod ?: PaymentMethod.CASH,
            deliveryMethod = rev.deliveryMethod ?: DeliveryMethod.BUYER_PICKUP,
            pickupOrDeliveryLocation = rev.pickupOrDeliveryLocation,
            expectedDateLabel = rev.expectedDateLabel,
            expectedTimeLabel = rev.expectedTimeLabel,
            status = ag.status,
            notes = notes ?: rev.additionalNotes,
            transactionId = ag.id,
            template = template,
            sellerSignatureLabel = ag.sellerDisplayName,
        )
        receipts.add(r)
        timelineEvents.add(
            TimelineEvent(
                transactionId = ag.id,
                type = TimelineEventType.RECEIPT_GENERATED,
                byRole = Role.SELLER,
                note = r.number,
            )
        )
        Mirror.receipt(r)
        return r
    }

    /**
     * Build an ad-hoc receipt from user-provided line items
     * (no agreement reference). Used by the AI Receipt flow.
     */
    fun createAdHocReceipt(
        sellerId: String,
        sellerDisplayName: String,
        sellerStoreName: String,
        sellerStoreLocation: String,
        buyerId: String? = null,
        buyerDisplayName: String,
        lines: List<ReceiptLine>,
        paymentMethod: PaymentMethod,
        deliveryMethod: DeliveryMethod,
        template: ReceiptTemplate,
        pickupOrDeliveryLocation: String? = null,
        expectedDateLabel: String? = null,
        expectedTimeLabel: String? = null,
        notes: String? = null,
    ): Receipt {
        val number = TransactionFactory.receiptNumber(receiptCounter.value)
        receiptCounter.value = receiptCounter.value + 1
        val subtotal = lines.sumOf { it.lineTotalUgx }
        val r = Receipt(
            number = number,
            sellerId = sellerId,
            sellerDisplayName = sellerDisplayName,
            sellerStoreName = sellerStoreName,
            sellerStoreLocation = sellerStoreLocation,
            buyerId = buyerId ?: "walk-in",
            buyerDisplayName = buyerDisplayName,
            issuedAtLabel = formatTodayLabel(),
            lines = lines,
            subtotalUgx = subtotal,
            totalUgx = subtotal,
            paymentMethod = paymentMethod,
            deliveryMethod = deliveryMethod,
            pickupOrDeliveryLocation = pickupOrDeliveryLocation,
            expectedDateLabel = expectedDateLabel,
            expectedTimeLabel = expectedTimeLabel,
            status = TransactionStatus.CONFIRMED,
            notes = notes,
            template = template,
            sellerSignatureLabel = sellerDisplayName,
        )
        receipts.add(r)
        // Mirror to Firestore
        Mirror.receipt(r)
        return r
    }

    fun receiptByNumber(number: String): Receipt? =
        receipts.firstOrNull { it.number == number }

    fun receiptsForUser(userId: String, role: Role): List<Receipt> =
        receipts.filter {
            when (role) {
                Role.BUYER -> it.buyerId == userId
                Role.SELLER -> it.sellerId == userId
            }
        }.sortedByDescending { it.createdAt }

    fun acknowledgeReceiptByBuyer(receiptNumber: String, ackLabel: String): Receipt? {
        val idx = receipts.indexOfFirst { it.number == receiptNumber }
        if (idx < 0) return null
        val r = receipts[idx]
        val updated = r.copy(buyerAcknowledgementLabel = ackLabel)
        receipts[idx] = updated
        // Find the associated transaction if any
        val txId = r.transactionId
        if (txId != null) {
            val agIdx = agreements.indexOfFirst { it.id == txId }
            if (agIdx >= 0) {
                val ag = agreements[agIdx]
                agreements[agIdx] = ag.copy(
                    buyerAcknowledgedReceipt = true,
                    buyerAcknowledgementAt = System.currentTimeMillis(),
                )
                timelineEvents.add(
                    TimelineEvent(
                        transactionId = txId,
                        type = TimelineEventType.BUYER_ACKNOWLEDGED,
                        byRole = Role.BUYER,
                        note = r.number,
                    )
                )
            }
        }
        Mirror.receipt(updated)
        return updated
    }

    fun duplicateReceiptAsTemplate(receiptNumber: String): Receipt? {
        val r = receiptByNumber(receiptNumber) ?: return null
        val newNumber = TransactionFactory.receiptNumber(receiptCounter.value)
        receiptCounter.value = receiptCounter.value + 1
        val dup = r.copy(
            number = newNumber,
            issuedAtLabel = formatTodayLabel(),
            status = TransactionStatus.CONFIRMED,
            buyerAcknowledgementLabel = null,
            createdAt = System.currentTimeMillis(),
        )
        receipts.add(dup)
        Mirror.receipt(dup)
        return dup
    }

    // =================================================================
    // Disputes
    // =================================================================

    fun raiseDispute(
        transactionId: String,
        raisedBy: String,
        raisedByRole: Role,
        reason: DisputeReason,
        description: String,
    ): Dispute {
        val d = Dispute(
            transactionId = transactionId,
            raisedBy = raisedBy,
            raisedByRole = raisedByRole,
            reason = reason,
            description = description,
        )
        disputes.add(d)
        val agIdx = agreements.indexOfFirst { it.id == transactionId }
        if (agIdx >= 0) {
            val ag = agreements[agIdx]
            agreements[agIdx] = ag.copy(
                status = TransactionStatus.DISPUTED,
                disputeReason = "${reason.label} — ${description.take(120)}",
                updatedAt = System.currentTimeMillis(),
            )
        }
        timelineEvents.add(
            TimelineEvent(
                transactionId = transactionId,
                type = TimelineEventType.DISPUTE_OPENED,
                byRole = raisedByRole,
                note = reason.label,
            )
        )
        Mirror.dispute(d)
        return d
    }

    fun resolveDispute(disputeId: String, resolutionNote: String): Dispute? {
        val idx = disputes.indexOfFirst { it.id == disputeId }
        if (idx < 0) return null
        val d = disputes[idx]
        val updated = d.copy(resolved = true, resolutionNote = resolutionNote)
        disputes[idx] = updated
        timelineEvents.add(
            TimelineEvent(
                transactionId = d.transactionId,
                type = TimelineEventType.DISPUTE_RESOLVED,
                byRole = if (d.raisedByRole == Role.BUYER) Role.SELLER else Role.BUYER,
                note = resolutionNote,
            )
        )
        // Restore transaction to Confirmed/InProgress once resolved
        val agIdx = agreements.indexOfFirst { it.id == d.transactionId }
        if (agIdx >= 0) {
            val ag = agreements[agIdx]
            val restoredStatus = when (ag.status) {
                TransactionStatus.DISPUTED -> TransactionStatus.CONFIRMED
                else -> ag.status
            }
            agreements[agIdx] = ag.copy(
                status = restoredStatus,
                updatedAt = System.currentTimeMillis(),
            )
        }
        Mirror.dispute(updated)
        return updated
    }

    fun disputesForUser(userId: String, role: Role): List<Dispute> {
        return disputes.filter { d ->
            val ag = agreementById(d.transactionId)
            when (role) {
                Role.BUYER -> ag?.buyerId == userId
                Role.SELLER -> ag?.sellerId == userId
            }
        }.sortedByDescending { it.raisedAt }
    }

    // =================================================================
    // Timeline
    // =================================================================

    fun timelineFor(transactionId: String): List<TimelineEvent> =
        timelineEvents.filter { it.transactionId == transactionId }.sortedBy { it.at }

    fun recordTimelineEvent(
        transactionId: String,
        type: TimelineEventType,
        byRole: Role,
        note: String? = null,
    ): TimelineEvent {
        val e = TimelineEvent(
            transactionId = transactionId,
            type = type,
            byRole = byRole,
            note = note,
        )
        timelineEvents.add(e)
        // Mirror to Firestore
        Mirror.timelineEvent(e, transactionId)
        return e
    }

    // =================================================================
    // AI helpers — readiness check for the AI assistant
    // =================================================================

    /**
     * Returns the fields that are still missing from the agreement,
     * so the AI can say e.g. "Your agreement is missing a delivery
     * method and a pickup location."
     */
    fun readinessFor(agreementId: String): List<String> {
        val ag = agreementById(agreementId) ?: return emptyList()
        val rev = ag.latestRevision ?: return emptyList()
        val missing = mutableListOf<String>()
        if (rev.productId.isBlank()) missing += "Product"
        if (rev.buyerConfirmedAt == null) missing += "Buyer confirmation"
        if (rev.sellerConfirmedAt == null) missing += "Seller confirmation"
        if (rev.paymentMethod == null) missing += "Payment method"
        if (rev.deliveryMethod == null) missing += "Delivery method"
        if (rev.pickupOrDeliveryLocation.isNullOrBlank()) missing += "Pickup/delivery location"
        return missing
    }

    fun isReady(agreementId: String): Boolean = readinessFor(agreementId).isEmpty()

    // =================================================================
    // Misc
    // =================================================================

    private fun formatTodayLabel(): String {
        val cal = java.util.Calendar.getInstance()
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val month = cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale.ENGLISH)
        val year = cal.get(java.util.Calendar.YEAR)
        return "$day $month $year"
    }

    fun ugxFormat(amount: Long): String {
        val s = "%,d".format(amount)
        return "$s ${Currency.DEFAULT}"
    }

    /** Convenience: list products the user has agreed to buy/sell. */
    fun productIdsForUser(userId: String, role: Role): Set<String> =
        agreementsForUser(userId, role).map { it.productId }.toSet()
}