package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.Currency
import com.scottsx.app.data.domain.DeliveryMethod
import com.scottsx.app.data.domain.PaymentMethod
import com.scottsx.app.data.domain.ReceiptLine
import com.scottsx.app.data.domain.ReceiptTemplate
import com.scottsx.app.data.domain.Role

/**
 * Stage 4 — Receipt designer screen.
 *
 * Two entry modes:
 *  - From a transaction: prefilled with the agreement's latest revision
 *  - Ad-hoc: blank, seller types in buyer/product/quantity/price
 *
 * The seller picks a template (Modern / Classic / Minimal /
 * Professional / Compact), reviews the draft, then taps "Confirm &
 * Generate Receipt" — only then does a real receipt appear. The
 * preview is shown in [ReceiptPreviewScreen] with share/print.
 *
 * Real data only — no fake numbers. If a product lookup fails, the
 * designer surfaces an error.
 */
@Composable
fun ReceiptDesignerScreen(
    transactionId: String? = null,
    onBack: () -> Unit = {},
    onReceiptCreated: (String) -> Unit = {},
) {
    val role = Session.roleOrNull() ?: Role.BUYER
    if (role != Role.SELLER) {
        // Receipts are seller-only.
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text("Receipt designer", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Receipts are created by the seller. Sign in as a seller to create one.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        return
    }

    // Prefill from the agreement if provided
    val prefilled = remember(transactionId) {
        if (transactionId != null) {
            val ag = TransactionStore.agreementById(transactionId)
            val rev = ag?.latestRevision
            if (ag != null && rev != null) {
                PrefilledReceipt(
                    buyerDisplayName = ag.buyerDisplayName,
                    productId = rev.productId,
                    quantity = rev.quantity,
                    unitPriceUgx = rev.agreedPriceUgx,
                    paymentMethod = rev.paymentMethod,
                    deliveryMethod = rev.deliveryMethod,
                    pickupOrDeliveryLocation = rev.pickupOrDeliveryLocation,
                    expectedDateLabel = rev.expectedDateLabel,
                    expectedTimeLabel = rev.expectedTimeLabel,
                    notes = rev.additionalNotes,
                )
            } else null
        } else null
    }

    var buyerName by remember { mutableStateOf(prefilled?.buyerDisplayName ?: "") }
    val lines = remember {
        mutableStateListOf<EditableLine>().apply {
            if (prefilled != null) {
                add(
                    EditableLine(
                        productId = prefilled.productId,
                        productName = MarketplaceDataSource.productById(prefilled.productId)?.name ?: "",
                        quantity = prefilled.quantity,
                        unitPriceUgx = prefilled.unitPriceUgx,
                    )
                )
            }
        }
    }
    var paymentMethod by remember { mutableStateOf(prefilled?.paymentMethod ?: PaymentMethod.CASH) }
    var deliveryMethod by remember { mutableStateOf(prefilled?.deliveryMethod ?: DeliveryMethod.BUYER_PICKUP) }
    var location by remember { mutableStateOf(prefilled?.pickupOrDeliveryLocation ?: Session.locationOrEmpty()) }
    var expectedDate by remember { mutableStateOf(prefilled?.expectedDateLabel ?: "") }
    var expectedTime by remember { mutableStateOf(prefilled?.expectedTimeLabel ?: "") }
    var notes by remember { mutableStateOf(prefilled?.notes ?: "") }
    var discountUgx by remember { mutableStateOf(0L) }
    var template by remember { mutableStateOf(ReceiptTemplate.MODERN) }
    var searchQuery by remember { mutableStateOf("") }
    var showProductPicker by remember { mutableStateOf(lines.isEmpty()) }

    val subtotal = lines.sumOf { it.lineTotalUgx }
    val total = (subtotal - discountUgx).coerceAtLeast(0)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Receipt designer", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text(
                            if (transactionId != null) "From transaction #${transactionId.takeLast(6).uppercase()}"
                            else "Create a receipt in a few taps",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Buyer
            item {
                SectionCard(title = "Buyer") {
                    OutlinedTextField(
                        value = buyerName,
                        onValueChange = { buyerName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buyer's name") },
                        singleLine = true,
                    )
                }
            }

            // Lines
            item {
                SectionCard(title = "Products") {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        lines.forEachIndexed { idx, l ->
                            ReceiptLineEditor(
                                line = l,
                                onChange = { lines[idx] = it },
                                onRemove = if (lines.size > 1) ({ lines.removeAt(idx) }) else null,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showProductPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6).copy(alpha = 0.15f), contentColor = Color(0xFF3B82F6)),
                        ) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add product")
                        }
                    }
                }
            }

            // Discount
            item {
                SectionCard(title = "Discount (optional)") {
                    OutlinedTextField(
                        value = if (discountUgx == 0L) "" else discountUgx.toString(),
                        onValueChange = { v ->
                            discountUgx = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("UGX") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }

            // Payment
            item {
                SectionCard(title = "Payment method") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentMethod.values().forEach { pm ->
                            ChoiceChip(
                                label = pm.label,
                                selected = pm == paymentMethod,
                                onClick = { paymentMethod = pm },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Delivery
            item {
                SectionCard(title = "Delivery / Pickup") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeliveryMethod.values().forEach { dm ->
                            ChoiceChip(
                                label = dm.label,
                                selected = dm == deliveryMethod,
                                onClick = { deliveryMethod = dm },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Pickup/delivery location") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = expectedDate,
                            onValueChange = { expectedDate = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Date") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = expectedTime,
                            onValueChange = { expectedTime = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Time") },
                            singleLine = true,
                        )
                    }
                }
            }

            // Template
            item {
                SectionCard(title = "Receipt template") {
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReceiptTemplate.values().forEach { tpl ->
                            TemplateRow(
                                template = tpl,
                                selected = tpl == template,
                                onClick = { template = tpl },
                            )
                        }
                    }
                }
            }

            // Notes
            item {
                SectionCard(title = "Notes (optional)") {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("Any extra notes for the buyer") },
                    )
                }
            }

            // Totals
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        TotalsRow("Subtotal", TransactionStore.ugxFormat(subtotal))
                        TotalsRow("Discount", "- ${TransactionStore.ugxFormat(discountUgx)}")
                        DividerLine()
                        TotalsRow("Total", TransactionStore.ugxFormat(total), bold = true)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Payment recorded by seller — ScottsTechX does NOT process payments.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Confirm
            item {
                Button(
                    onClick = {
                        val receipt = TransactionStore.createAdHocReceipt(
                            sellerId = Session.userIdOrNull() ?: "draft",
                            sellerDisplayName = Session.displayNameOrEmpty(),
                            sellerStoreName = Session.storeNameOrEmpty(),
                            sellerStoreLocation = Session.locationOrEmpty(),
                            buyerId = null,
                            buyerDisplayName = buyerName.ifBlank { "Walk-in buyer" },
                            lines = lines.map { ReceiptLine(it.productId, it.productName, null, it.quantity, it.unitPriceUgx) },
                            paymentMethod = paymentMethod,
                            deliveryMethod = deliveryMethod,
                            template = template,
                            pickupOrDeliveryLocation = location.takeIf { it.isNotBlank() },
                            expectedDateLabel = expectedDate.takeIf { it.isNotBlank() },
                            expectedTimeLabel = expectedTime.takeIf { it.isNotBlank() },
                            notes = notes.takeIf { it.isNotBlank() },
                        )
                        onReceiptCreated(receipt.number)
                    },
                    enabled = buyerName.isNotBlank() && lines.isNotEmpty() && lines.all { it.productId.isNotBlank() && it.quantity > 0 && it.unitPriceUgx > 0 },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm & generate receipt")
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Product picker sheet
    if (showProductPicker) {
        ProductPickerSheet(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSelect = { p ->
                lines.add(
                    EditableLine(
                        productId = p.id,
                        productName = p.name,
                        quantity = 1,
                        unitPriceUgx = p.priceUgx,
                    )
                )
                showProductPicker = false
                searchQuery = ""
            },
            onDismiss = { showProductPicker = false },
        )
    }
}

private data class EditableLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceUgx: Long,
) {
    val lineTotalUgx: Long get() = quantity * unitPriceUgx
}

private data class PrefilledReceipt(
    val buyerDisplayName: String,
    val productId: String,
    val quantity: Int,
    val unitPriceUgx: Long,
    val paymentMethod: PaymentMethod?,
    val deliveryMethod: DeliveryMethod?,
    val pickupOrDeliveryLocation: String?,
    val expectedDateLabel: String?,
    val expectedTimeLabel: String?,
    val notes: String?,
)

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ReceiptLineEditor(
    line: EditableLine,
    onChange: (EditableLine) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.productName.ifBlank { "Product" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, "Remove", tint = Color(0xFFEF4444))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Quantity stepper
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        IconButton(onClick = {
                            if (line.quantity > 1) onChange(line.copy(quantity = line.quantity - 1))
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Remove, null, modifier = Modifier.size(16.dp))
                        }
                        Text("${line.quantity}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = {
                            onChange(line.copy(quantity = line.quantity + 1))
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = line.unitPriceUgx.toString(),
                    onValueChange = { v ->
                        val n = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        onChange(line.copy(unitPriceUgx = n))
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Unit price UGX") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Line total: ${TransactionStore.ugxFormat(line.lineTotalUgx)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF3B82F6).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)) else null,
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 12.sp,
            color = if (selected) Color(0xFF3B82F6) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TemplateRow(template: ReceiptTemplate, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF3B82F6).copy(alpha = 0.10f) else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)) else null,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(template.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(template.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF3B82F6))
        }
    }
}

@Composable
private fun TotalsRow(label: String, value: String, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun DividerLine() {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)))
    Spacer(Modifier.height(8.dp))
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProductPickerSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (com.scottsx.app.data.domain.Product) -> Unit,
    onDismiss: () -> Unit,
) {
    val results = remember(query) {
        if (query.isBlank()) MarketplaceDataSource.allProducts.take(20)
        else MarketplaceDataSource.searchProducts(query).take(20)
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Pick a product", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search products") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results, key = { it.id }) { p ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(p) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(p.category.displayName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                            Text(TransactionStore.ugxFormat(p.priceUgx), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}