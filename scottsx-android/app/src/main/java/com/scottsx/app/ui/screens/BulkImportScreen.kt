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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

private data class ImportRow(
    val title: String,
    val price: Long,
    val category: String,
    val stock: Int,
    val imageUrl: String,
    var ok: Boolean? = null,
    var error: String? = null,
)

/**
 * Bulk import — parity with the web /seller/bulk-import page.
 *
 * Paste CSV (header: title,price,category,stock,image_url). Each row is
 * created through the same /api/v1/seller/products endpoint the web loops
 * over; "Stage as drafts" mirrors the web's draft switch so rows wait in
 * drafts instead of entering the review queue.
 */
@Composable
fun BulkImportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var csv by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ImportRow>>(emptyList()) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var asDraft by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }

    fun parse() {
        parseError = null
        val raw = parseCsv(csv)
        if (raw.size < 2) { parseError = "CSV needs a header row + data rows"; rows = emptyList(); return }
        val header = raw[0].map { it.trim().lowercase() }
        val data = raw.drop(1)
        rows = data.map { r ->
            val d = header.indices.associate { i -> header[i] to (r.getOrElse(i) { "" }).trim() }
            ImportRow(
                title = d["title"] ?: d["name"] ?: "",
                price = (d["price"] ?: d["price_minor"] ?: d["priceminor"] ?: "0").toLongOrNull() ?: 0L,
                category = d["category"] ?: "Other",
                stock = (d["stock"] ?: d["stock_quantity"] ?: d["stockquantity"] ?: "1").toIntOrNull() ?: 1,
                imageUrl = d["image_url"] ?: d["imageurl"] ?: "",
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.PanelLight)
            .statusBarSpacer(),
    ) {
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
                Text("Bulk import", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Many listings at once — same as web", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Icon(Icons.Filled.FileUpload, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Paste CSV — header: title,price,category,stock,image_url",
                color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = csv,
                onValueChange = {
                    csv = it
                    rows = emptyList()
                    parseError = null
                    done = false
                    summary = null
                },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = {
                    Text(
                        "title,price,category,stock,image_url\nSamsung A15,250000,Phones,10,https://…",
                        color = ScottsTechXColors.OnPanelSecondary, fontSize = 12.sp,
                    )
                },
            )
            if (parseError != null) {
                Spacer(Modifier.height(6.dp))
                Text(parseError!!, color = Color(0xFFDC2626), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Preview rows", onClick = { parse() }, enabled = csv.isNotBlank() && !busy)
                Spacer(Modifier.width(12.dp))
                Switch(checked = asDraft, onCheckedChange = { asDraft = it })
                Spacer(Modifier.width(6.dp))
                Text("Stage as drafts", color = ScottsTechXColors.OnPanelSecondary, fontSize = 12.sp)
            }

            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("${rows.size} row(s) ready", color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val tooMany = rows.size > 100
                rows.take(100).forEachIndexed { i, r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (r.ok) {
                            true -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                            false -> Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            null -> Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(ScottsTechXColors.PanelInputLight))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${r.title.ifBlank { "(no title)" }}", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("UGX ${"%,d".format(r.price)} · ${r.category} · stock ${r.stock}", color = ScottsTechXColors.OnLightSecondary, fontSize = 11.sp)
                            if (r.error != null) Text(r.error!!, color = Color(0xFFDC2626), fontSize = 10.sp)
                        }
                    }
                }
                if (tooMany) {
                    Text("Showing first 100 rows — all ${rows.size} rows are imported below.", color = ScottsTechXColors.OnPanelSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(if (busy) "Importing…" else "Import ${rows.size} product(s)", enabled = !busy, onClick = {
                    if (busy) return@PrimaryButton
                    val snapshot = rows.map { it.copy() }
                    busy = true
                    summary = null
                    scope.launch {
                        var ok = 0
                        var failed = 0
                        val results = snapshot.map { r ->
                            if (r.title.isBlank() || r.price <= 0L) {
                                failed++; r.copy(ok = false, error = "title + price required")
                            } else {
                                val id = runCatching {
                                    V2Client.createProduct(
                                        title = r.title,
                                        priceMinor = r.price,
                                        stock = r.stock,
                                        category = r.category,
                                        imageUrl = r.imageUrl.ifBlank { null },
                                        asDraft = asDraft,
                                    )
                                }.getOrNull()
                                if (id != null) { ok++; r.copy(ok = true) }
                                else { failed++; r.copy(ok = false, error = "server rejected") }
                            }
                        }
                        rows = results
                        busy = false
                        done = true
                        summary = "$ok imported · $failed failed" +
                            if (asDraft) " — saved as drafts, submit from Inventory" else " — queued for admin review"
                    }
                })
            }

            if (done && summary != null) {
                Spacer(Modifier.height(10.dp))
                Text(summary!!, color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

/** CSV splitter handling quoted cells with commas, same contract as the web. */
private fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var cur = StringBuilder()
    val row = mutableListOf<String>()
    var inQ = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (inQ) {
            if (ch == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') { cur.append('"'); i++ } else inQ = false
            } else cur.append(ch)
        } else when (ch) {
            '"' -> inQ = true
            ',' -> { row.add(cur.toString()); cur = StringBuilder() }
            '\n', '\r' -> {
                if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row.add(cur.toString()); cur = StringBuilder()
                if (row.any { it.isNotBlank() }) rows.add(row.toList())
                row.clear()
            }
            else -> cur.append(ch)
        }
        i++
    }
    row.add(cur.toString())
    if (row.any { it.isNotBlank() }) rows.add(row.toList())
    return rows
}
