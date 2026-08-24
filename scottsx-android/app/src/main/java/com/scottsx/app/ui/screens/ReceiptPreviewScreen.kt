package com.scottsx.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.Receipt
import com.scottsx.app.data.domain.ReceiptTemplate
import com.scottsx.app.data.domain.Role

/**
 * Stage 4 — Receipt preview.
 *
 * Shows the receipt in one of five templates. The seller taps
 * "Confirm" if the buyer hasn't already acknowledged, or the buyer
 * can acknowledge here directly.
 *
 * Share/Print/Download buttons render the receipt to a Bitmap and
 * start a system intent. ScottsTechX does NOT process the payment
 * — the receipt always shows "Payment recorded by seller".
 */
@Composable
fun ReceiptPreviewScreen(
    receiptNumber: String,
    onBack: () -> Unit = {},
    onAcknowledge: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val role = Session.roleOrNull() ?: Role.BUYER
    val receipt = remember(receiptNumber) { TransactionStore.receiptByNumber(receiptNumber) }
    var ackConfirmed by remember(receiptNumber) { mutableStateOf(receipt?.isAcknowledgedByBuyer == true) }
    var showShareDialog by remember { mutableStateOf(false) }

    if (receipt == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text("Receipt", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text("Receipt $receiptNumber not found.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Receipt", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Text(receipt.number, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
                IconButton(onClick = { showShareDialog = true }) {
                    Icon(Icons.Filled.Share, "Share")
                }
                IconButton(onClick = {
                    renderAndShare(ctx, receipt, "Print")
                }) {
                    Icon(Icons.Filled.Print, "Print")
                }
                IconButton(onClick = {
                    renderAndShare(ctx, receipt, "Download")
                }) {
                    Icon(Icons.Filled.FileDownload, "Download")
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(14.dp))
                        .padding(2.dp),
                ) {
                    ReceiptBody(receipt = receipt)
                }

                Spacer(Modifier.height(16.dp))

                if (role == Role.BUYER && !ackConfirmed) {
                    Button(
                        onClick = {
                            val label = Session.displayNameOrEmpty().ifBlank { "Buyer" }
                            TransactionStore.acknowledgeReceiptByBuyer(receipt.number, label)
                            ackConfirmed = true
                            onAcknowledge(receipt.number)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6), contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Acknowledge receipt details")
                    }
                } else if (ackConfirmed) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.10f),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF10B981))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Receipt acknowledged", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF10B981))
                                Text(
                                    "Acknowledgement does NOT mean ScottsTechX processed payment. It only means the buyer confirmed the recorded details.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { onBack() },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Close")
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Send receipt via ScottsTechX") },
            text = {
                Text(
                    "The receipt will be sent to the conversation with ${receipt.buyerDisplayName} as a card they can tap to open here. The buyer can then acknowledge it."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        renderAndShare(ctx, receipt, "Send")
                        showShareDialog = false
                    }
                ) { Text("Send") }
            },
            dismissButton = { OutlinedButton(onClick = { showShareDialog = false }) { Text("Cancel") } },
        )
    }
}

/**
 * The receipt body — rendered as a Compose Canvas. We pick the
 * template at draw time so all five templates share one rendering
 * function.
 */
@Composable
private fun ReceiptBody(receipt: Receipt) {
    val template = receipt.template
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        when (template) {
            ReceiptTemplate.MODERN -> ModernHeader(receipt)
            ReceiptTemplate.CLASSIC -> ClassicHeader(receipt)
            ReceiptTemplate.MINIMAL -> MinimalHeader(receipt)
            ReceiptTemplate.PROFESSIONAL -> ProfessionalHeader(receipt)
            ReceiptTemplate.COMPACT -> CompactHeader(receipt)
        }
        Spacer(Modifier.height(16.dp))
        ReceiptParties(receipt)
        Spacer(Modifier.height(16.dp))
        ReceiptLineTable(receipt)
        Spacer(Modifier.height(12.dp))
        ReceiptTotals(receipt)
        Spacer(Modifier.height(16.dp))
        ReceiptFooter(receipt)
    }
}

@Composable
private fun ModernHeader(r: Receipt) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))))
            .padding(20.dp),
    ) {
        Column {
            Text("ScottsTechX", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Receipt", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Receipt #", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    Text(r.number, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column {
                    Text("Date", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    Text(r.issuedAtLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ClassicHeader(r: Receipt) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("ScottsTechX", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF1F2937))
        Text("ScottsTechX Marketplace · Kampala", fontSize = 11.sp, color = Color(0xFF6B7280))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF1F2937)))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("INVOICE / RECEIPT", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Column {
                Text("No. ${r.number}", fontSize = 12.sp)
                Text("Date: ${r.issuedAtLabel}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MinimalHeader(r: Receipt) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row {
            Text("ScottsTechX", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF111827), modifier = Modifier.weight(1f))
            Text(r.number, fontSize = 11.sp, color = Color(0xFF6B7280))
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E7EB)))
    }
}

@Composable
private fun ProfessionalHeader(r: Receipt) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("ScottsTechX Marketplace", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E3A8A))
                Text(r.sellerStoreName, fontSize = 12.sp)
                Text(r.sellerStoreLocation, fontSize = 11.sp, color = Color(0xFF6B7280))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("RECEIPT", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1E3A8A))
                Text(r.number, fontSize = 12.sp)
                Text(r.issuedAtLabel, fontSize = 11.sp, color = Color(0xFF6B7280))
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E3A8A)))
    }
}

@Composable
private fun CompactHeader(r: Receipt) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("ScottsTechX · ${r.number}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(r.issuedAtLabel, fontSize = 11.sp, color = Color(0xFF6B7280))
    }
}

@Composable
private fun ReceiptParties(r: Receipt) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("FROM", fontSize = 9.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(r.sellerStoreName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(r.sellerDisplayName, fontSize = 11.sp, color = Color(0xFF374151))
            Text(r.sellerStoreLocation, fontSize = 10.sp, color = Color(0xFF6B7280))
        }
        Column(Modifier.weight(1f)) {
            Text("BILL TO", fontSize = 9.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(r.buyerDisplayName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            r.buyerEmail?.let { Text(it, fontSize = 10.sp, color = Color(0xFF6B7280)) }
        }
    }
}

@Composable
private fun ReceiptLineTable(r: Receipt) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Item", fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(2f))
            Text("Qty", fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("Price", fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("Total", fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E7EB)))
        r.lines.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(2f)) {
                    Text(l.productName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    l.variantLabel?.let { Text(it, fontSize = 10.sp, color = Color(0xFF6B7280)) }
                }
                Text("${l.quantity}", fontSize = 12.sp, modifier = Modifier.weight(0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Text(TransactionStore.ugxFormat(l.unitPriceUgx), fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Text(TransactionStore.ugxFormat(l.lineTotalUgx), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF3F4F6)))
        }
    }
}

@Composable
private fun ReceiptTotals(r: Receipt) {
    Column(Modifier.fillMaxWidth().padding(start = 80.dp, top = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("Subtotal", fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(TransactionStore.ugxFormat(r.subtotalUgx), fontSize = 12.sp)
        }
        if (r.discountTotalUgx > 0L) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Discount", fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("- ${TransactionStore.ugxFormat(r.discountTotalUgx)}", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("Total", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(TransactionStore.ugxFormat(r.totalUgx), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("Payment", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
            Text("${r.paymentMethod.label} · recorded by seller", fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("Delivery", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
            Text(r.deliveryMethod.label + (r.pickupOrDeliveryLocation?.let { " · $it" } ?: ""), fontSize = 11.sp)
        }
        if (!r.expectedDateLabel.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Expected", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
                Text("${r.expectedDateLabel} ${r.expectedTimeLabel ?: ""}".trim(), fontSize = 11.sp)
            }
        }
        if (!r.notes.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("Notes", fontSize = 10.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
            Text(r.notes, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ReceiptFooter(r: Receipt) {
    Column(Modifier.fillMaxWidth()) {
        if (!r.sellerSignatureLabel.isNullOrBlank()) {
            Text("Signed", fontSize = 9.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
            Text(r.sellerSignatureLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!r.buyerAcknowledgementLabel.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("Acknowledged by buyer", fontSize = 9.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
            Text(r.buyerAcknowledgementLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E7EB)))
        Spacer(Modifier.height(6.dp))
        Text(
            "ScottsTechX is not a payment processor. Payment was recorded by the seller and was not processed by ScottsTechX.",
            fontSize = 9.sp,
            color = Color(0xFF9CA3AF),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun renderAndShare(ctx: android.content.Context, receipt: Receipt, kind: String) {
    val bmp = renderReceiptToBitmap(receipt)
    val cacheDir = ctx.cacheDir
    val file = java.io.File(cacheDir, "receipt-${receipt.number}.png")
    file.outputStream().use { os -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os) }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        ctx,
        ctx.packageName + ".fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "ScottsTechX Receipt ${receipt.number} — ${TransactionStore.ugxFormat(receipt.totalUgx)}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "$kind receipt")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(chooser) }
}

/**
 * Render the receipt to a Bitmap using Canvas so we can attach it to
 * an Intent.ACTION_SEND as a PNG image. This is a snapshot of the
 * receipt content — it is NOT a "fake invoice" being generated
 * without the seller's knowledge; the seller confirms the receipt in
 * the app first.
 */
internal fun renderReceiptToBitmap(receipt: Receipt): Bitmap {
    val w = 1080
    val rowH = 64
    val headerH = 220
    val partiesH = 200
    val tableH = 80 + receipt.lines.size * 96
    val totalsH = 280 + (if (!receipt.notes.isNullOrBlank()) 80 else 0)
    val footerH = 200
    val h = headerH + partiesH + tableH + totalsH + footerH
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)

    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 42f
        isFakeBoldText = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6B7280.toInt()
        textSize = 28f
    }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 32f
    }
    val bodyBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 32f
        isFakeBoldText = true
    }

    var y = 60f
    // Header
    when (receipt.template) {
        ReceiptTemplate.MODERN -> {
            val paint = Paint()
            paint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), headerH.toFloat(), paint)
            title.color = android.graphics.Color.WHITE
            canvas.drawText("ScottsTechX", 40f, y + 50f, title)
            val sub = Paint(title).apply { textSize = 30f; color = 0xCCFFFFFF.toInt(); isFakeBoldText = false }
            canvas.drawText("Receipt", 40f, y + 90f, sub)
            y += headerH
        }
        ReceiptTemplate.CLASSIC -> {
            canvas.drawText("ScottsTechX", 40f, y + 30f, title)
            canvas.drawText("ScottsTechX Marketplace · Kampala", 40f, y + 70f, label)
            canvas.drawRect(40f, y + 90f, w - 40f, y + 96f, Paint().apply { color = 0xFF1F2937.toInt() })
            canvas.drawText("INVOICE / RECEIPT", 40f, y + 140f, title)
            canvas.drawText("No. ${receipt.number}", w - 360f, y + 130f, bodyBold)
            canvas.drawText("Date: ${receipt.issuedAtLabel}", w - 360f, y + 170f, body)
            y += headerH
        }
        ReceiptTemplate.MINIMAL -> {
            canvas.drawText("ScottsTechX", 40f, y + 30f, title)
            canvas.drawText(receipt.number, w - 360f, y + 30f, label)
            canvas.drawRect(40f, y + 60f, w - 40f, y + 62f, Paint().apply { color = 0xFFE5E7EB.toInt() })
            y += headerH
        }
        ReceiptTemplate.PROFESSIONAL -> {
            canvas.drawText("ScottsTechX Marketplace", 40f, y + 30f, title)
            canvas.drawText(receipt.sellerStoreName, 40f, y + 70f, body)
            canvas.drawText(receipt.sellerStoreLocation, 40f, y + 110f, label)
            title.textSize = 50f
            canvas.drawText("RECEIPT", w - 280f, y + 50f, title)
            title.textSize = 42f
            canvas.drawText(receipt.number, w - 280f, y + 100f, bodyBold)
            canvas.drawText(receipt.issuedAtLabel, w - 280f, y + 140f, label)
            canvas.drawRect(40f, y + 170f, w - 40f, y + 174f, Paint().apply { color = 0xFF1E3A8A.toInt() })
            y += headerH
        }
        ReceiptTemplate.COMPACT -> {
            canvas.drawText("ScottsTechX · ${receipt.number}", 40f, y + 20f, bodyBold)
            canvas.drawText(receipt.issuedAtLabel, w - 280f, y + 20f, label)
            y += headerH
        }
    }

    // Parties
    canvas.drawText("FROM", 40f, y + 30f, label)
    canvas.drawText("BILL TO", w / 2 + 20f, y + 30f, label)
    canvas.drawText(receipt.sellerStoreName, 40f, y + 70f, bodyBold)
    canvas.drawText(receipt.sellerDisplayName, 40f, y + 110f, body)
    canvas.drawText(receipt.sellerStoreLocation, 40f, y + 150f, label)
    canvas.drawText(receipt.buyerDisplayName, w / 2 + 20f, y + 70f, bodyBold)
    receipt.buyerEmail?.let { canvas.drawText(it, w / 2 + 20f, y + 110f, label) }
    y += partiesH

    // Table header
    canvas.drawText("Item", 40f, y + 30f, label)
    canvas.drawText("Qty", w - 540f, y + 30f, label)
    canvas.drawText("Price", w - 380f, y + 30f, label)
    canvas.drawText("Total", w - 180f, y + 30f, label)
    canvas.drawRect(40f, y + 50f, w - 40f, y + 52f, Paint().apply { color = 0xFFE5E7EB.toInt() })
    receipt.lines.forEachIndexed { idx, l ->
        val yLine = y + 90f + idx * 96f
        canvas.drawText(l.productName, 40f, yLine, bodyBold)
        canvas.drawText("${l.quantity}", w - 540f, yLine, body)
        canvas.drawText(TransactionStore.ugxFormat(l.unitPriceUgx), w - 380f, yLine, body)
        canvas.drawText(TransactionStore.ugxFormat(l.lineTotalUgx), w - 180f, yLine, bodyBold)
        canvas.drawRect(40f, yLine + 16f, w - 40f, yLine + 18f, Paint().apply { color = 0xFFF3F4F6.toInt() })
    }
    y += tableH

    // Totals
    val labelX = w / 2 + 20f
    val valueX = w - 180f
    canvas.drawText("Subtotal", labelX, y + 30f, body)
    canvas.drawText(TransactionStore.ugxFormat(receipt.subtotalUgx), valueX, y + 30f, body)
    if (receipt.discountTotalUgx > 0L) {
        canvas.drawText("Discount", labelX, y + 70f, body)
        canvas.drawText("- ${TransactionStore.ugxFormat(receipt.discountTotalUgx)}", valueX, y + 70f, body)
    }
    canvas.drawText("Total", labelX, y + 130f, bodyBold)
    canvas.drawText(TransactionStore.ugxFormat(receipt.totalUgx), valueX, y + 130f, bodyBold)
    canvas.drawText("Payment: ${receipt.paymentMethod.label} · recorded by seller", 40f, y + 180f, label)
    canvas.drawText("Delivery: ${receipt.deliveryMethod.label}", 40f, y + 220f, label)
    receipt.pickupOrDeliveryLocation?.let { canvas.drawText("Location: $it", 40f, y + 260f, label) }
    if (!receipt.notes.isNullOrBlank()) {
        canvas.drawText("Notes:", 40f, y + 300f, label)
        canvas.drawText(receipt.notes.take(120), 40f, y + 340f, body)
    }
    y += totalsH

    // Footer
    canvas.drawRect(40f, y + 30f, w - 40f, y + 32f, Paint().apply { color = 0xFFE5E7EB.toInt() })
    val foot = Paint(label).apply { textSize = 22f; color = 0xFF9CA3AF.toInt() }
    canvas.drawText(
        "ScottsTechX is not a payment processor. Payment was recorded by the seller and was not processed by ScottsTechX.",
        40f, y + 80f, foot,
    )
    return bmp
}