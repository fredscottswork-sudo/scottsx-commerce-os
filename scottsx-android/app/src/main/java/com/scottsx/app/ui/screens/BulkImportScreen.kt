package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.formatUgx
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

private data class CsvRow(
    val title: String,
    val priceUgx: Long,
    val category: String,
    val stock: Int,
    val imageUrl: String,
    val description: String,
    var ok: Boolean? = null,
    var error: String? = null,
)

/** CSV splitter that mirrors the web's quoted-cell handling exactly. */
private fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val cur = StringBuilder()
    val row = mutableListOf<String>()
    var inQ = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (inQ) {
            if (ch == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') { cur.append('"'); i++ } else inQ = false
            } else cur.append(ch)
        } else if (ch == '"') inQ = true
        else if (ch == ',') { row.add(cur.toString()); cur.setLength(0) }
        else if (ch == '\n' || ch == '\r') {
            if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
            row.add(cur.toString()); cur.setLength(0)
            if (row.any { it.isNotBlank() }) rows.add(row.toList())
            row.clear()
        } else cur.append(ch)
        i++
    }
    row.add(cur.toString())
    if (row.any { it.isNotBlank() }) rows.add(row.toList())
    return rows
}

/**
 * Seller bulk import — parity with web/src/pages/seller/BulkImport.tsx.
 * Paste CSV (title,price,category,stock,image_url,description), preview,
 * and each row lands through POST /api/v1/seller/products — the same
 * per-row endpoint the web loops over. Rows join the approval queue, or
 * arrive as drafts when the seller flips the draft toggle.
 */
@Composable
fun BulkImportScreen(onBack: () -> Unit, onInventory: () -> Unit = {}) {
    var csv by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<CsvRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var asDraft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val preview = remember { mutableStateListOf<CsvRow>() }

    fun parse() {
        error = null
        val raw = parseCsv(csv)
        if (raw.size < 2) { error = "CSV needs a header row + data rows"; rows = emptyList(); preview.clear(); return }
        val header = raw[0].map { it.trim().lowercase() }
        val dataRows = raw.drop(1).map { r -> header.withIndex().associate { (i, h) -> h to (r.getOrElse(i) { "" }).trim() } }
        val parsed = dataRows.map { d ->
            CsvRow(
                title = d["title"] ?: d["name"] ?: "",
                priceUgx = (d["price"] ?: d["price_minor"] ?: d["priceminor"] ?: "0").toLongOrNull() ?: 0L,
                category = d["category"] ?: "Other",
                stock = (d["stock"] ?: d["stock_quantity"] ?: d["stockquantity"] ?: "1").toIntOrNull() ?: 1,
                imageUrl = d["image_url"] ?: d["imageurl"] ?: "",
                description = d["description"] ?: d["desc"] ?: "",
            )
        }
        if (parsed.any { it.title.isBlank() }) error = "Some rows are missing a title — the web rejects those too."
        rows = parsed
        preview.clear()
        preview.addAll(parsed.take(5))
    }

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight).statusBarSpacer()) {
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
            ) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Bulk import", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("CSV → listings, exactly like the web tool", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }
            Icon(Icons.Filled.FileUpload, null, tint = Color.White)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                "Paste your CSV below. Columns: title,price,category,stock,image_url,description. " +
                    "Header row required — same parser as the website.",
                color = ScottsTechXColors.OnPanelSecondary, fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = csv,
                onValueChange = { csv = it; rows = emptyList(); preview.clear(); error = null; done = false; summary = null },
                placeholder = {
                    Text(
                        "title,price,category,stock,image_url\n\"Anker Power Bank 20K\",350000,Phones,15,https://…",
                        color = ScottsTechXColors.OnCardSecondary, fontSize = 11.5.sp,
                    )
                },
                minLines = 5,
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ScottsTechXColors.OnCard,
                    unfocusedTextColor = ScottsTechXColors.OnCard,
                    focusedContainerColor = ScottsTechXColors.CardSurface,
                    unfocusedContainerColor = ScottsTechXColors.CardSurface,
                    focusedBorderColor = ScottsTechXColors.BluePrimary,
                    unfocusedBorderColor = ScottsTechXColors.CardSurfaceAlt,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error!!, color = Color(0xFFF87171), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (csv.isNotBlank()) ScottsTechXColors.BluePrimary else ScottsTechXColors.CardSurfaceAlt)
                        .clickable(enabled = csv.isNotBlank() && !busy) { parse() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Preview", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(12.dp))
                // Draft staging toggle — exists on the web tool
                androidx.compose.material3.Switch(
                    checked = asDraft,
                    onCheckedChange = { asDraft = it },
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (asDraft) "Import as drafts" else "Import for review",
                        color = ScottsTechXColors.OnCard, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (asDraft) "Stock stays private until submitted."
                        else "Rows go straight into the admin approval queue.",
                        color = ScottsTechXColors.OnCardSecondary, fontSize = 10.sp,
                    )
                }
            }

            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Preview — showing ${preview.size} of ${rows.size} rows",
                    color = ScottsTechXColors.OnCard, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                preview.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ScottsTechXColors.CardSurface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = ScottsTechXColors.OnCard, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                "${formatUgx(r.priceUgx)} · ${r.category} · stock ${r.stock}",
                                color = ScottsTechXColors.OnCardSecondary, fontSize = 10.5.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ScottsTechXColors.BluePrimary)
                            .clickable(enabled = !busy && !done) {
                                if (busy) return@clickable
                                val toImport = rows.map { it.copy() }
                                busy = true
                                summary = null
                                scope.launch {
                                    var ok = 0
                                    var failed = 0
                                    toImport.forEachIndexed { idx, r ->
                                        val id = V2Client.createProduct(
                                            title = r.title,
                                            priceMinor = r.priceUgx,
                                            description = r.description.ifBlank { null },
                                            stock = r.stock,
                                            category = r.category,
                                            imageUrl = r.imageUrl.ifBlank { null },
                                            asDraft = asDraft,
                                        )
                                        if (id != null) { toImport[idx] = r.copy(ok = true); ok++ }
                                        else { toImport[idx] = r.copy(ok = false, error = "create failed"); failed++ }
                                    }
                                    preview.clear()
                                    preview.addAll(toImport.take(5))
                                    rows = toImport
                                    done = true
                                    busy = false
                                    summary = "$ok imported, $failed failed" +
                                        if (asDraft) " — land in your Drafts tab" else " — queued for admin approval"
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Importing…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else Text("Import ${rows.size} listings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (done) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp))
                    }
                }
                if (summary != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(summary!!, color = ScottsTechXColors.OnCard, fontSize = 11.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Open inventory",
                        color = ScottsTechXColors.BluePrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onInventory),
                    )
                }
            }
        }
    }
}
