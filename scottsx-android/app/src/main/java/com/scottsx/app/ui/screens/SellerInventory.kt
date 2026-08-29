package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.SellerProductList
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Seller inventory — parity with web/src/pages/seller/Inventory.tsx.
 * Status filter (all/approved/pending/draft/rejected with live counts),
 * per-row edit (PATCH), delete (DELETE), submit-for-review (draft/rejected),
 * and the web's low/zero-stock badges. All rows come from
 * /api/v1/seller/products through the same V2Client class as the web
 * service layer.
 */
@Composable
fun SellerInventoryScreen(
    onBack: () -> Unit,
    onAddProduct: () -> Unit = {},
    onBulkImport: () -> Unit = {},
) {
    var status by remember { mutableStateOf<String>("all") }
    var page by remember { mutableStateOf<SellerProductList?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // dialogs/sheets
    var editing by remember { mutableStateOf<Product?>(null) }
    var deleting by remember { mutableStateOf<Product?>(null) }

    fun reload() { reloadTick++ }

    LaunchedEffect(status, reloadTick) {
        loading = true
        error = null
        page = V2Client.fetchSellerProducts(status = status)
        if (page == null) error = "Couldn't load your products — check your connection."
        loading = false
    }
    LaunchedEffect(toast) {
        if (toast != null) { kotlinx.coroutines.delay(2200); toast = null }
    }

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight).statusBarSpacer()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.BluePrimaryDark)
                .padding(start = 4.dp, end = 16.dp, top = 30.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Inventory", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "Every listing — same rows as the web inventory",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onAddProduct)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Status filter strip + counts, from the same page object the web uses
        val counts = page?.counts ?: emptyMap()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "all" to "All",
                "approved" to "Approved",
                "pending" to "Pending",
                "draft" to "Drafts",
                "rejected" to "Rejected",
            ).forEach { (key, label) ->
                val count = if (key == "all") counts.values.sum() else counts[key] ?: 0
                InventoryTab("$label ($count)", selected = status == key) { status = key }
            }
        }

        when {
            loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator(color = ScottsTechXColors.BluePrimary) }
            error != null -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error!!, color = ScottsTechXColors.OnPanel)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ScottsTechXColors.BluePrimary)
                        .clickable { reload() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Retry", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            else -> {
                val products = page?.products ?: emptyList()
                if (products.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.Inventory, null,
                            tint = ScottsTechXColors.OnPanelSecondary,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (status == "all") "No products yet" else "Nothing in \"$status\"",
                            color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        )
                        Text(
                            if (status == "all") "Add your first product — buyers browse approved listings instantly."
                            else "Switch the filter or add a product.",
                            color = ScottsTechXColors.OnPanelSecondary, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ScottsTechXColors.BluePrimary)
                                .clickable(onClick = onAddProduct)
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) { Text("Add product", color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Or bulk import a CSV like the web tool",
                            color = ScottsTechXColors.BluePrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable(onClick = onBulkImport),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(products, key = { it.id }) { p ->
                            InventoryRow(
                                p = p,
                                onEdit = { editing = p },
                                onDelete = { deleting = p },
                                onSubmitForReview = {
                                    scope.launch {
                                        val ok = V2Client.submitSellerProductForReview(p.id)
                                        toast = if (ok) "Submitted for review" else "Couldn't submit"
                                        if (ok) reload()
                                    }
                                },
                                onSetStock = { newStock ->
                                    scope.launch {
                                        val ok = V2Client.updateSellerProduct(
                                            p.id,
                                            JSONObject().put("stockQuantity", newStock),
                                        )
                                        toast = if (ok) "Stock updated" else "Couldn't update stock"
                                        if (ok) reload()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (toast != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScottsTechXColors.BluePrimaryDark)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(toast!!, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }

    // ---- Edit sheet (web PATCH /seller/products/:id) ----------------------
    editing?.let { p ->
        InventoryEditSheet(
            product = p,
            onDismiss = { editing = null },
            onSave = { patch ->
                scope.launch {
                    val ok = V2Client.updateSellerProduct(p.id, patch)
                    toast = if (ok) "Saved — visible on the web too" else "Couldn't save"
                    editing = null
                    if (ok) reload()
                }
            },
        )
    }

    // ---- Delete confirm (web DELETE /seller/products/:id) -----------------
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${p.name}\"?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes the listing from your store and the catalogue. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = V2Client.deleteSellerProduct(p.id)
                        toast = if (ok) "Deleted" else "Couldn't delete"
                        deleting = null
                        if (ok) reload()
                    }
                }) { Text("Delete", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}


@Composable
private fun InventoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ScottsTechXColors.BluePrimary else ScottsTechXColors.CardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else ScottsTechXColors.OnCard,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InventoryRow(
    p: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSubmitForReview: () -> Unit,
    onSetStock: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.CardSurface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ScottsTechXColors.CardSurfaceAlt),
            ) {
                if (p.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = p.imageUrl,
                        contentDescription = p.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = ScottsTechXColors.OnCard, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1)
                Text(
                    formatUgx(p.priceUgx) + if (p.brand.name.isNotBlank()) " · ${p.brand.name}" else "",
                    color = ScottsTechXColors.OnCardSecondary, fontSize = 11.5.sp, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(p.status)
                    Spacer(Modifier.width(6.dp))
                    StockBadge(p.stock)
                }
            }
            // stock stepper — same backend update as a web stock edit
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (p.stock > 0) onSetStock(p.stock - 1) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Filled.Remove, null, tint = ScottsTechXColors.OnCard, modifier = Modifier.size(14.dp))
                    }
                    Text("${p.stock}", color = ScottsTechXColors.OnCard, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { onSetStock(p.stock + 1) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Filled.Add, null, tint = ScottsTechXColors.OnCard, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InventoryAction(Icons.Filled.Edit, "Edit", onEdit)
            if (p.status == "draft" || p.status == "rejected") {
                InventoryAction(Icons.Filled.Send, "Submit for review", onSubmitForReview)
            } else if (p.status == "pending") {
                InventoryHint(Icons.Filled.Info, "Awaiting admin approval")
            } else if (p.status == "approved") {
                InventoryHint(Icons.Filled.CheckCircle, "Live in the catalogue", good = true)
            }
            InventoryAction(Icons.Filled.Delete, "Delete", onDelete, danger = true)
        }
        if (!p.rejectionReason.isNullOrBlank() && p.status == "rejected") {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1FDC2626))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Warning, null, tint = Color(0xFFF87171), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(p.rejectionReason!!, color = Color(0xFFF87171), fontSize = 10.5.sp)
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (bg, fg, label) = when (status) {
        "approved" -> Triple(Color(0x2622C55E), Color(0xFF4ADE80), "Live")
        "pending" -> Triple(Color(0x26F59E0B), Color(0xFFFBBF24), "Pending review")
        "draft" -> Triple(Color(0x1F94A3C4), ScottsTechXColors.OnCardSecondary, "Draft")
        "rejected" -> Triple(Color(0x26EF4444), Color(0xFFF87171), "Rejected")
        else -> Triple(Color(0x1F94A3C4), ScottsTechXColors.OnCardSecondary, status)
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(label, color = fg, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
}

/** Web parity: 0 stock = red, <5 = amber, else green (badges in Inventory.tsx). */
@Composable
private fun StockBadge(stock: Int) {
    val (bg, fg) = when {
        stock == 0 -> Color(0x26EF4444) to Color(0xFFF87171)
        stock < 5 -> Color(0x26F59E0B) to Color(0xFFFBBF24)
        else -> Color(0x2622C55E) to Color(0xFF4ADE80)
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text("stock $stock", color = fg, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun InventoryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (danger) Color(0x1FDC2626) else ScottsTechXColors.BluePrimary.copy(alpha = 0.12f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = if (danger) Color(0xFFF87171) else ScottsTechXColors.BluePrimary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = if (danger) Color(0xFFF87171) else ScottsTechXColors.BluePrimary,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InventoryHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    good: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (good) Color(0x2622C55E) else Color(0x26F59E0B))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (good) Color(0xFF4ADE80) else Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (good) Color(0xFF4ADE80) else Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Edit sheet — same fields as the web inventory edit row: title, brand,
 * price, old price, stock, description. PATCHes only what changed — the
 * backend's sparse-update rule is honoured, and content edits on an
 * approved listing knock it back to the review queue (same as the web).
 */
@Composable
private fun InventoryEditSheet(
    product: Product,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    var title by remember { mutableStateOf(product.name) }
    var brand by remember { mutableStateOf(product.brand.name) }
    var price by remember { mutableStateOf(product.priceUgx.toString()) }
    var oldPrice by remember { mutableStateOf(product.oldPriceUgx?.toString() ?: "") }
    var stock by remember { mutableStateOf(product.stock.toString()) }
    var description by remember { mutableStateOf(product.description) }
    var saving by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit listing", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().androidx.compose.foundation.verticalScroll(
                    androidx.compose.foundation.rememberScrollState(),
                ),
            ) {
                InvField("Title", title, enabled = !saving) { title = it }
                InvField("Brand", brand, enabled = !saving) { brand = it }
                InvField("Price (UGX)", price, numeric = true, enabled = !saving) { price = it.filter { c -> c.isDigit() } }
                InvField("Old price (UGX, for strikethrough)", oldPrice, numeric = true, enabled = !saving) { oldPrice = it.filter { c -> c.isDigit() } }
                InvField("Stock", stock, numeric = true, enabled = !saving) { stock = it.filter { c -> c.isDigit() } }
                InvField("Description", description, lines = 4, enabled = !saving) { description = it }
                if (product.status == "approved") {
                    Text(
                        "Content edits send this listing back through admin review — same rule as the web.",
                        color = ScottsTechXColors.OnCardSecondary, fontSize = 10.5.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saving = true
                    val patch = JSONObject()
                    if (title != product.name) patch.put("title", title.trim())
                    if (brand != product.brand.name) patch.put("brand", brand.trim())
                    price.toLongOrNull()?.let { if (it != product.priceUgx) patch.put("priceMinor", it) }
                    val newOld = oldPrice.toLongOrNull()
                    if (newOld != product.oldPriceUgx) {
                        if (newOld == null || newOld <= 0) patch.put("oldPriceMinor", JSONObject.NULL) else patch.put("oldPriceMinor", newOld)
                    }
                    stock.toIntOrNull()?.let { if (it != product.stock) patch.put("stockQuantity", it) }
                    if (description != product.description) patch.put("description", description.trim())
                    onSave(patch)
                },
                enabled = !saving && title.isNotBlank() && (price.toLongOrNull() ?: 0L) > 0L,
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

@Composable
private fun InvField(
    label: String,
    value: String,
    numeric: Boolean = false,
    lines: Int = 1,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        enabled = enabled,
        minLines = lines,
        maxLines = lines,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        keyboardOptions = if (numeric) androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ) else androidx.compose.foundation.text.KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}

