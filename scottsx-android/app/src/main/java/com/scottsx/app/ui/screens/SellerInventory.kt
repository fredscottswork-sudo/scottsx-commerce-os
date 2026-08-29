package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Seller inventory — full parity with the web /seller/inventory page.
 *
 * Status tabs (all / approved / pending / draft / rejected) backed by the
 * per-status counts the list endpoint returns. Each row offers the same
 * actions the web table offers: inline stock edit, full edit sheet,
 * submit-for-review (drafts/rejected), and delete.
 */
@Composable
fun SellerInventoryScreen(
    onBack: () -> Unit,
    onAddProduct: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf("all") }
    var productList by remember { mutableStateOf<com.scottsx.app.data.domain.SellerProductList?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Product?>(null) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, reloadTick) {
        isLoading = true
        loadError = null
        try {
            productList = V2Client.fetchSellerProducts(status = tab)
        } catch (t: Throwable) {
            loadError = t.message ?: "Could not load inventory"
        }
        isLoading = false
    }

    if (toast != null) {
        LaunchedEffect(toast) {
            kotlinx.coroutines.delay(1800)
            toast = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.PanelLight)
            .statusBarSpacer(),
    ) {
        // ---- Top bar (matches the other seller screens) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.BluePrimaryDark)
                .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Inventory", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Manage listings like on the web", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            IconButton(onClick = onAddProduct) {
                Icon(Icons.Filled.Add, contentDescription = "Add product", tint = Color.White)
            }
        }

        // ---- Status tabs ----
        val counts = productList?.counts ?: emptyMap()
        val totalAll = counts["all"] ?: counts.values.sum()
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { StatusTab("All", totalAll, tab == "all") { tab = "all" } }
            item { StatusTab("Approved", counts["approved"] ?: 0, tab == "approved") { tab = "approved" } }
            item { StatusTab("Pending", counts["pending"] ?: 0, tab == "pending") { tab = "pending" } }
            item { StatusTab("Drafts", counts["draft"] ?: 0, tab == "draft") { tab = "draft" } }
            item { StatusTab("Rejected", counts["rejected"] ?: 0, tab == "rejected") { tab = "rejected" } }
        }

        when {
            isLoading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = ScottsTechXColors.BluePrimary)
            }
            loadError != null -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError!!, color = ScottsTechXColors.OnPanelSecondary)
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = { reloadTick++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = ScottsTechXColors.OnPanel) }
            }
            else -> {
                val rows = productList?.products.orEmpty()
                if (rows.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Inventory, contentDescription = null, tint = ScottsTechXColors.OnPanelSecondary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No products in this state", color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.SemiBold)
                        Text("Add a product or switch status tabs.", color = ScottsTechXColors.OnPanelSecondary, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(rows, key = { it.id }) { p ->
                            InventoryRow(
                                p,
                                onEdit = { editing = p },
                                onDelete = { deleting = p },
                                onSubmit = {
                                    scope.launch {
                                        if (V2Client.submitSellerProductForReview(p.id)) {
                                            toast = "Submitted for review"
                                            reloadTick++
                                        } else toast = "Submit failed"
                                    }
                                },
                                onSetStock = { q ->
                                    scope.launch {
                                        if (V2Client.updateSellerProduct(p.id, JSONObject().put("stockQuantity", q))) {
                                            toast = "Stock updated"
                                            reloadTick++
                                        } else toast = "Stock update failed"
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
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(toast!!, color = ScottsTechXColors.OnPanel, fontSize = 12.sp)
            }
        }
    }

    // ---- Edit sheet ----
    if (editing != null) {
        val p = editing!!
        EditProductSheet(
            product = p,
            onDismiss = { editing = null },
            onSave = { patch ->
                scope.launch {
                    if (V2Client.updateSellerProduct(p.id, patch)) {
                        toast = "Saved"
                        editing = null
                        reloadTick++
                    } else toast = "Save failed"
                }
            },
        )
    }

    // ---- Delete confirm ----
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete product?") },
            text = { Text("\"${deleting!!.name}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    val d = deleting!!
                    scope.launch {
                        if (V2Client.deleteSellerProduct(d.id)) {
                            toast = "Product deleted"
                            deleting = null
                            reloadTick++
                        } else toast = "Could not delete"
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusTab(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) ScottsTechXColors.BluePrimary else ScottsTechXColors.PanelInputLight)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text("$label ($count)", color = if (active) Color.White else ScottsTechXColors.OnPanel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InventoryRow(
    p: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onSetStock: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(ScottsTechXColors.PanelInputLight)) {
                if (p.imageUrl.isNotBlank()) {
                    AsyncImage(model = p.imageUrl, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatUgx(p.priceUgx), color = ScottsTechXColors.BluePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    StatusChip(p.status)
                    Spacer(Modifier.width(6.dp))
                    val stockColor = when {
                        p.stock == 0 -> Color(0xFFDC2626)
                        p.stock < 5 -> Color(0xFFB45309)
                        else -> ScottsTechXColors.OnLightSecondary
                    }
                    Text("stock ${p.stock}", color = stockColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // inline stock adjuster
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (p.stock > 0) onSetStock(p.stock - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = ScottsTechXColors.OnLight, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onSetStock(p.stock + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase", tint = ScottsTechXColors.OnLight, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction(Icons.Filled.Edit, "Edit", onEdit)
            if (p.status == "draft" || p.status == "rejected") {
                MiniAction(Icons.Filled.Publish, "Submit for review", onSubmit)
            }
            if (p.status == "approved") {
                MiniAction(Icons.Filled.CheckCircle, "Live", {})
            }
            if (p.status == "rejected") {
                MiniAction(Icons.Filled.Warning, "Rejected", {})
            }
            MiniAction(Icons.Filled.Delete, "Delete", onDelete, danger = true)
        }
    }
}

@Composable
private fun MiniAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) Color(0xFFDC2626) else ScottsTechXColors.BluePrimary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusChip(status: String) {
    val (bg, fg) = when (status) {
        "approved" -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
        "pending" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
        "draft" -> Pair(Color(0xFFE2E8F0), Color(0xFF475569))
        "rejected" -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
        else -> Pair(Color(0xFFE2E8F0), Color(0xFF475569))
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(status, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EditProductSheet(
    product: Product,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    var title by remember { mutableStateOf(product.name) }
    var description by remember { mutableStateOf(product.description) }
    var priceText by remember { mutableStateOf(product.priceUgx.toString()) }
    var oldPriceText by remember { mutableStateOf(product.oldPriceUgx?.toString() ?: "") }
    var stockText by remember { mutableStateOf(product.stock.toString()) }
    var locationText by remember { mutableStateOf(product.location) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit product", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = priceText, onValueChange = { priceText = it.filter { c -> c.isDigit() } }, label = { Text("Price (UGX)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = oldPriceText, onValueChange = { oldPriceText = it.filter { c -> c.isDigit() } }, label = { Text("Old price (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = stockText, onValueChange = { stockText = it.filter { c -> c.isDigit() } }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = locationText, onValueChange = { locationText = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val patch = JSONObject().put("title", title.trim())
                    .put("description", description.trim())
                    .put("location", locationText.trim())
                priceText.toLongOrNull()?.let { patch.put("priceMinor", it) }
                if (oldPriceText.isBlank()) patch.put("oldPriceMinor", JSONObject.NULL)
                else oldPriceText.toLongOrNull()?.let { patch.put("oldPriceMinor", it) }
                stockText.toIntOrNull()?.let { patch.put("stockQuantity", it) }
                onSave(patch)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
