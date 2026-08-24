package com.scottsx.app.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.scottsx.app.data.domain.AgreementRevision
import com.scottsx.app.data.domain.Dispute
import com.scottsx.app.data.domain.Receipt
import com.scottsx.app.data.domain.TimelineEvent
import com.scottsx.app.data.domain.TransactionAgreement
import com.scottsx.app.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Stage 5 — Firestore mirror for Stage 4 in-memory state.
 *
 * The app's transactions, receipts, disputes, timeline, and AI
 * memory live in-memory (TransactionStore, AiPersonalizationStore).
 * On every mutation, [Mirror] is called which enqueues a write
 * to Firestore. Writes are best-effort; failures are logged but
 * never block the UI.
 *
 * Layout in Firestore:
 *   /users/{uid}/transactions/{txId}             — TransactionAgreement
 *   /users/{uid}/receipts/{receiptNumber}       — Receipt
 *   /users/{uid}/disputes/{disputeId}           — Dispute
 *   /users/{uid}/timeline/{eventId}             — TimelineEvent
 *   /users/{uid}/ai_memory/main                  — AI memory doc
 *
 * The same write is also mirrored to the OTHER party's tree (so
 * both buyer and seller see the same transaction).
 */
object Mirror {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentLinkedQueue<suspend () -> Unit>()

    fun enqueue(block: suspend () -> Unit) {
        pending.add(block)
        scope.launch {
            val next = pending.poll() ?: return@launch
            try { next() } catch (_: Throwable) { /* best-effort */ }
        }
    }

    fun transaction(ag: TransactionAgreement) {
        val myUid = Session.userIdOrNull() ?: return
        val otherUid = if (ag.buyerId == myUid) ag.sellerId else ag.buyerId
        val data = agToMap(ag)
        // Mirror to both sides' trees.
        for (uid in listOfNotNull(myUid, otherUid.takeIf { it.isNotBlank() && it != myUid })) {
            enqueue { write("transactions", ag.id, data, uid) }
        }
    }

    fun receipt(r: Receipt) {
        val myUid = Session.userIdOrNull() ?: return
        val otherUid = if (r.buyerId == myUid) r.sellerId else r.buyerId
        val data = receiptToMap(r)
        for (uid in listOfNotNull(myUid, otherUid.takeIf { it.isNotBlank() && it != myUid })) {
            enqueue { write("receipts", r.number, data, uid) }
        }
    }

    fun dispute(d: Dispute) {
        val myUid = Session.userIdOrNull() ?: return
        val ag = com.scottsx.app.data.TransactionStore.agreementById(d.transactionId)
        val otherUid = ag?.let { if (it.buyerId == myUid) it.sellerId else it.buyerId }
        val data = mapOf(
            "id" to d.id,
            "transactionId" to d.transactionId,
            "raisedBy" to d.raisedBy,
            "raisedByRole" to d.raisedByRole.name,
            "reason" to d.reason.label,
            "description" to d.description,
            "raisedAt" to d.raisedAt,
            "resolved" to d.resolved,
            "resolutionNote" to d.resolutionNote,
        )
        for (uid in listOfNotNull(myUid, otherUid?.takeIf { it.isNotBlank() && it != myUid })) {
            enqueue { write("disputes", d.id, data, uid) }
        }
    }

    fun timelineEvent(e: TimelineEvent, transactionId: String) {
        val myUid = Session.userIdOrNull() ?: return
        val ag = com.scottsx.app.data.TransactionStore.agreementById(transactionId)
        val otherUid = ag?.let { if (it.buyerId == myUid) it.sellerId else it.buyerId }
        val data = mapOf(
            "id" to e.id,
            "transactionId" to e.transactionId,
            "type" to e.type.name,
            "typeLabel" to e.type.label,
            "description" to e.type.description,
            "at" to e.at,
            "byRole" to e.byRole.name,
            "note" to e.note,
        )
        for (uid in listOfNotNull(myUid, otherUid?.takeIf { it.isNotBlank() && it != myUid })) {
            enqueue { write("timeline", e.id, data, uid) }
        }
    }

    fun aiMemory(
        recentSearches: List<String>,
        topCategories: List<String>,
        followedSellers: List<String>,
        priceLowUgx: Long?,
        priceHighUgx: Long?,
        aiOpenCount: Int,
    ) {
        val myUid = Session.userIdOrNull() ?: return
        val data = mapOf(
            "recentSearches" to recentSearches,
            "topCategories" to topCategories,
            "followedSellers" to followedSellers,
            "priceLowUgx" to priceLowUgx,
            "priceHighUgx" to priceHighUgx,
            "aiOpenCount" to aiOpenCount,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        enqueue { write("ai_memory", "main", data, myUid) }
    }

    private suspend fun write(
        collection: String,
        docId: String,
        data: Map<String, Any?>,
        uid: String,
    ) {
        try {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection(collection).document(docId)
                .set(data, SetOptions.merge())
        } catch (_: Throwable) { /* best-effort */ }
    }

    private fun agToMap(ag: TransactionAgreement): Map<String, Any?> {
        val rev = ag.latestRevision
        return mapOf(
            "id" to ag.id,
            "buyerId" to ag.buyerId,
            "buyerDisplayName" to ag.buyerDisplayName,
            "sellerId" to ag.sellerId,
            "sellerDisplayName" to ag.sellerDisplayName,
            "threadId" to ag.threadId,
            "productId" to ag.productId,
            "status" to ag.status.name,
            "statusLabel" to ag.statusLabel(),
            "currentRevision" to ag.currentRevision,
            "latestRevision" to rev?.let { revisionToMap(it) },
            "createdAt" to ag.createdAt,
            "updatedAt" to ag.updatedAt,
            "buyerAcknowledgedReceipt" to ag.buyerAcknowledgedReceipt,
            "buyerAcknowledgementAt" to ag.buyerAcknowledgementAt,
            "disputeReason" to ag.disputeReason,
            "cancelledReason" to ag.cancelledReason,
        )
    }

    private fun revisionToMap(r: AgreementRevision): Map<String, Any?> = mapOf(
        "revisionNumber" to r.revisionNumber,
        "productId" to r.productId,
        "productName" to r.productName,
        "variantId" to r.variantId,
        "variantLabel" to r.variantLabel,
        "quantity" to r.quantity,
        "agreedPriceUgx" to r.agreedPriceUgx,
        "currency" to r.currency,
        "paymentMethod" to r.paymentMethod?.name,
        "deliveryMethod" to r.deliveryMethod?.name,
        "pickupOrDeliveryLocation" to r.pickupOrDeliveryLocation,
        "expectedDateLabel" to r.expectedDateLabel,
        "expectedTimeLabel" to r.expectedTimeLabel,
        "additionalNotes" to r.additionalNotes,
        "createdAt" to r.createdAt,
        "createdBy" to r.createdBy,
        "buyerConfirmedAt" to r.buyerConfirmedAt,
        "sellerConfirmedAt" to r.sellerConfirmedAt,
    )

    private fun receiptToMap(r: Receipt): Map<String, Any?> = mapOf(
        "number" to r.number,
        "sellerId" to r.sellerId,
        "sellerDisplayName" to r.sellerDisplayName,
        "sellerStoreName" to r.sellerStoreName,
        "sellerStoreLocation" to r.sellerStoreLocation,
        "buyerId" to r.buyerId,
        "buyerDisplayName" to r.buyerDisplayName,
        "buyerEmail" to r.buyerEmail,
        "issuedAtLabel" to r.issuedAtLabel,
        "lines" to r.lines.map { mapOf("productId" to it.productId, "productName" to it.productName, "variantLabel" to it.variantLabel, "quantity" to it.quantity, "unitPriceUgx" to it.unitPriceUgx) },
        "subtotalUgx" to r.subtotalUgx,
        "totalUgx" to r.totalUgx,
        "currency" to r.currency,
        "paymentMethod" to r.paymentMethod.name,
        "paymentRecordedBySeller" to r.paymentRecordedBySeller,
        "deliveryMethod" to r.deliveryMethod.name,
        "pickupOrDeliveryLocation" to r.pickupOrDeliveryLocation,
        "expectedDateLabel" to r.expectedDateLabel,
        "expectedTimeLabel" to r.expectedTimeLabel,
        "status" to r.status.name,
        "transactionId" to r.transactionId,
        "template" to r.template.name,
        "sellerSignatureLabel" to r.sellerSignatureLabel,
        "buyerAcknowledgementLabel" to r.buyerAcknowledgementLabel,
        "createdAt" to r.createdAt,
    )
}
