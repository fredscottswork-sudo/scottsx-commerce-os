package com.scottsx.app.data.domain

/**
 * Stage 4 — Transaction agreement, delivery and receipt types.
 *
 * ScottsTechX is NOT a payment processor. The transaction agreement
 * records what both parties agreed to; the receipt records what the
 * seller marked as received. The actual payment happens outside
 * ScottsTechX between the parties.
 */
import java.util.UUID

// =================================================================
// Payment method (recorded by the seller — not processed by us)
// =================================================================

/**
 * The method used for payment. ScottsTechX does NOT process any of
 * these — they are recorded by the seller for the receipt. The
 * label on every receipt must read "Payment recorded by seller",
 * never "processed by ScottsTechX".
 */
enum class PaymentMethod(val label: String, val recordedBySeller: Boolean = true) {
    CASH("Cash"),
    MOBILE_MONEY("Mobile Money"),
    BANK_TRANSFER("Bank Transfer"),
    CARD_EXTERNAL("Card (paid externally)"),
    OTHER("Other (agreed)");
}

// =================================================================
// Delivery method
// =================================================================

enum class DeliveryMethod(val label: String) {
    BUYER_PICKUP("Buyer Pickup"),
    SELLER_DELIVERY("Seller Delivery"),
    COURIER("Courier / Third-party"),
    OTHER_AGREED("Other (agreed)");
}

// =================================================================
// Currency
// =================================================================

object Currency {
    const val DEFAULT = "UGX";
}

// =================================================================
// Transaction status
// =================================================================

/**
 * The lifecycle of a transaction agreement.
 *
 *  Draft           - one party is still editing
 *  Proposed        - seller has sent a proposal
 *  BuyerAccepted   - buyer has accepted the latest revision
 *  SellerAccepted  - seller has accepted the latest revision
 *  Confirmed       - both parties have accepted the latest revision
 *  InProgress      - buyer and seller are meeting the agreement
 *  Completed       - both parties agree the agreement has been fulfilled
 *  Cancelled       - either party cancelled before completion
 *  Disputed        - one party reported a problem
 */
enum class TransactionStatus(val label: String) {
    DRAFT("Draft"),
    PROPOSED("Proposed"),
    BUYER_ACCEPTED("Buyer accepted"),
    SELLER_ACCEPTED("Seller accepted"),
    CONFIRMED("Confirmed"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    DISPUTED("Disputed");
}

// =================================================================
// Receipt template
// =================================================================

enum class ReceiptTemplate(val label: String, val description: String) {
    MODERN("Modern", "Bold blue gradient header, sans-serif, glass accents"),
    CLASSIC("Classic", "Serif-style, formal invoice layout"),
    MINIMAL("Minimal", "Lots of whitespace, single accent line"),
    PROFESSIONAL("Professional", "Tabular invoice, tight borders, dense layout"),
    COMPACT("Compact", "Single page, small text, mobile-first");
}

// =================================================================
// Agreement revision
// =================================================================

/**
 * Every meaningful change to a transaction agreement creates a new
 * revision. Both parties must accept the latest revision before the
 * agreement is considered Confirmed. Revisions are immutable — once
 * created, the data on them cannot be edited; only a NEW revision
 * can be created.
 */
data class AgreementRevision(
    val revisionNumber: Int,                // 1, 2, 3...
    val productId: String,
    val productName: String,
    val variantId: String? = null,
    val variantLabel: String? = null,
    val quantity: Int,
    val agreedPriceUgx: Long,
    val currency: String = Currency.DEFAULT,
    val paymentMethod: PaymentMethod? = null,
    val deliveryMethod: DeliveryMethod? = null,
    val pickupOrDeliveryLocation: String? = null,
    val expectedDateLabel: String? = null,    // e.g. "12 August 2026"
    val expectedTimeLabel: String? = null,    // e.g. "3:00 PM"
    val additionalNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String,                    // "buyer" or "seller"
    val buyerConfirmedAt: Long? = null,
    val sellerConfirmedAt: Long? = null,
)

// =================================================================
// Transaction agreement
// =================================================================

data class TransactionAgreement(
    val id: String,
    val buyerId: String,
    val buyerDisplayName: String,
    val sellerId: String,
    val sellerDisplayName: String,
    val threadId: String? = null,             // originating message thread
    val productId: String,
    val status: TransactionStatus,
    val currentRevision: Int,                 // points to the latest revision's number
    val revisions: List<AgreementRevision>,
    val createdAt: Long,
    val updatedAt: Long,
    val buyerAcknowledgedReceipt: Boolean = false,
    val buyerAcknowledgementAt: Long? = null,
    val disputeReason: String? = null,
    val cancelledReason: String? = null,
) {
    val latestRevision: AgreementRevision? get() = revisions.maxByOrNull { it.revisionNumber }

    /**
     * Important: revisionNumber starts at 1. A new revision is created
     * whenever an important field (price, quantity, product, delivery,
     * payment method) changes. Both parties must accept the latest
     * revision for the agreement to be Confirmed.
     */
    val isBothPartiesConfirmed: Boolean
        get() {
            val r = latestRevision ?: return false
            return r.buyerConfirmedAt != null && r.sellerConfirmedAt != null
        }

    fun statusLabel(): String = when (status) {
        TransactionStatus.DRAFT -> "Draft"
        TransactionStatus.PROPOSED -> "Proposed"
        TransactionStatus.BUYER_ACCEPTED -> "Buyer accepted"
        TransactionStatus.SELLER_ACCEPTED -> "Seller accepted"
        TransactionStatus.CONFIRMED -> "Confirmed"
        TransactionStatus.IN_PROGRESS -> "In progress"
        TransactionStatus.COMPLETED -> "Completed"
        TransactionStatus.CANCELLED -> "Cancelled"
        TransactionStatus.DISPUTED -> "Disputed"
    }
}

// =================================================================
// Receipt
// =================================================================

data class ReceiptLine(
    val productId: String,
    val productName: String,
    val variantLabel: String? = null,
    val quantity: Int,
    val unitPriceUgx: Long,
) {
    val lineTotalUgx: Long get() = quantity * unitPriceUgx
}

data class Receipt(
    val number: String,                          // STX-RCPT-00010482
    val sellerId: String,
    val sellerDisplayName: String,
    val sellerStoreName: String,
    val sellerStoreLocation: String,
    val buyerId: String,
    val buyerDisplayName: String,
    val buyerEmail: String? = null,
    val issuedAtLabel: String,                   // "12 August 2026"
    val lines: List<ReceiptLine>,
    val discountUgx: Long = 0,
    val subtotalUgx: Long,                       // computed from lines
    val totalUgx: Long,                          // subtotal - discount
    val currency: String = Currency.DEFAULT,
    val paymentMethod: PaymentMethod,
    val paymentRecordedBySeller: Boolean = true,  // always true in Stage 4
    val deliveryMethod: DeliveryMethod,
    val pickupOrDeliveryLocation: String? = null,
    val expectedDateLabel: String? = null,
    val expectedTimeLabel: String? = null,
    val status: TransactionStatus,
    val notes: String? = null,
    val transactionId: String? = null,           // originating agreement (if any)
    val template: ReceiptTemplate = ReceiptTemplate.MODERN,
    val sellerSignatureLabel: String? = null,
    val buyerAcknowledgementLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val discountTotalUgx: Long = discountUgx
    val isAcknowledgedByBuyer: Boolean get() = buyerAcknowledgementLabel != null
}

// =================================================================
// Dispute
// =================================================================

enum class DisputeReason(val label: String) {
    PRODUCT_NOT_RECEIVED("Product not received"),
    WRONG_PRODUCT("Wrong product"),
    DAMAGED_PRODUCT("Damaged product"),
    PAYMENT_DISAGREEMENT("Payment disagreement"),
    DELIVERY_PROBLEM("Delivery problem"),
    AGREEMENT_DISAGREEMENT("Agreement disagreement"),
    OTHER("Other");
}

data class Dispute(
    val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val raisedBy: String,                         // buyerId or sellerId
    val raisedByRole: Role,
    val reason: DisputeReason,
    val description: String,
    val raisedAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false,
    val resolutionNote: String? = null,
) {
    companion object {
        fun newId(): String = "STX-DSP-" + UUID.randomUUID().toString().take(8).uppercase()
    }
}

// =================================================================
// Transaction timeline events
// =================================================================

enum class TimelineEventType(val label: String, val description: String) {
    PRODUCT_SELECTED("Product selected", "Buyer opened the product details page"),
    CONVERSATION_STARTED("Conversation started", "Buyer opened the chat thread"),
    AGREEMENT_PROPOSED("Agreement proposed", "Seller sent a structured transaction proposal"),
    AGREEMENT_ACCEPTED("Agreement accepted", "Buyer and seller accepted the latest revision"),
    RECEIPT_GENERATED("Receipt generated", "Seller generated and shared a receipt"),
    DELIVERY_PICKUP_AGREED("Delivery / pickup agreed", "Both parties agreed on a delivery arrangement"),
    DELIVERY_PICKUP_COMPLETED("Delivery / pickup completed", "Both parties marked the handover complete"),
    TRANSACTION_COMPLETED("Transaction completed", "The agreement has been fulfilled"),
    TRANSACTION_CANCELLED("Transaction cancelled", "The agreement was cancelled"),
    DISPUTE_OPENED("Dispute opened", "One party reported a problem"),
    DISPUTE_RESOLVED("Dispute resolved", "The dispute was resolved"),
    BUYER_ACKNOWLEDGED("Buyer acknowledged receipt", "Buyer confirmed the recorded receipt details");
}

data class TimelineEvent(
    val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val type: TimelineEventType,
    val at: Long = System.currentTimeMillis(),
    val byRole: Role,
    val note: String? = null,
)

// =================================================================
// Convenience: transaction factories
// =================================================================

object TransactionFactory {
    fun newId(): String = "STX-TRX-" + UUID.randomUUID().toString().take(8).uppercase()

    fun receiptNumber(seq: Long): String =
        "STX-RCPT-" + seq.toString().padStart(8, '0')

    fun buildRevision(
        revisionNumber: Int,
        productId: String,
        productName: String,
        quantity: Int = 1,
        priceUgx: Long = 0,
        createdBy: String,
        variantId: String? = null,
        variantLabel: String? = null,
        paymentMethod: PaymentMethod? = null,
        deliveryMethod: DeliveryMethod? = null,
        pickupOrDeliveryLocation: String? = null,
        expectedDateLabel: String? = null,
        expectedTimeLabel: String? = null,
        additionalNotes: String? = null,
    ): AgreementRevision = AgreementRevision(
        revisionNumber = revisionNumber,
        productId = productId,
        productName = productName,
        variantId = variantId,
        variantLabel = variantLabel,
        quantity = quantity,
        agreedPriceUgx = priceUgx,
        paymentMethod = paymentMethod,
        deliveryMethod = deliveryMethod,
        pickupOrDeliveryLocation = pickupOrDeliveryLocation,
        expectedDateLabel = expectedDateLabel,
        expectedTimeLabel = expectedTimeLabel,
        additionalNotes = additionalNotes,
        createdBy = createdBy,
    )

    fun buildAgreement(
        buyerId: String,
        buyerDisplayName: String,
        sellerId: String,
        sellerDisplayName: String,
        productId: String,
        firstRevision: AgreementRevision,
        threadId: String? = null,
    ): TransactionAgreement = TransactionAgreement(
        id = newId(),
        buyerId = buyerId,
        buyerDisplayName = buyerDisplayName,
        sellerId = sellerId,
        sellerDisplayName = sellerDisplayName,
        threadId = threadId,
        productId = productId,
        status = TransactionStatus.PROPOSED,
        currentRevision = firstRevision.revisionNumber,
        revisions = listOf(firstRevision),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )
}