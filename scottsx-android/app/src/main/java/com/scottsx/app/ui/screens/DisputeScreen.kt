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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ai.AiTools
import com.scottsx.app.data.Session
import com.scottsx.app.data.TransactionStore
import com.scottsx.app.data.domain.DisputeReason
import com.scottsx.app.data.domain.Role

/**
 * Stage 4 — Dispute screen.
 * The user picks a reason, types a description, and submits. The AI
 * then summarizes the case from the actual recorded agreement and
 * receipt. The AI does NOT decide legal liability.
 */
@Composable
fun DisputeScreen(
    transactionId: String,
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val role = Session.roleOrNull() ?: Role.BUYER
    val ag = remember(transactionId) { TransactionStore.agreementById(transactionId) }
    var reason by remember { mutableStateOf<DisputeReason?>(null) }
    var description by remember { mutableStateOf("") }
    var aiSummary by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Text("Report a problem", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
            }

            if (ag == null) {
                item { Text("Transaction not found.", modifier = Modifier.padding(16.dp)) }
                return@LazyColumn
            }

            item {
                Text(
                    "Transaction #${ag.id.takeLast(6).uppercase()} · ${ag.statusLabel()}",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                Text(
                    "What went wrong?",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
            }

            items(DisputeReason.values()) { r ->
                val selected = reason == r
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { reason = r },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) Color(0xFFEF4444).copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.label, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(if (selected) Color(0xFFEF4444) else Color.Transparent, CircleShape)
                                .border(2.dp, if (selected) Color(0xFFEF4444) else Color(0xFFD1D5DB), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(120.dp),
                    placeholder = { Text("Describe the issue (this will be saved with the dispute).") },
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                if (submitted && aiSummary != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.08f),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Flag, null, tint = Color(0xFFF59E0B))
                                Spacer(Modifier.width(8.dp))
                                Text("AI case summary", fontWeight = FontWeight.SemiBold, color = Color(0xFFF59E0B))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(aiSummary!!, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "ScottsTechX does NOT decide legal liability. The buyer and seller should resolve this directly, using the recorded agreement and receipt as evidence.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            reason?.let { r ->
                                val d = TransactionStore.raiseDispute(
                                    transactionId = transactionId,
                                    raisedBy = Session.userIdOrNull() ?: "anonymous",
                                    raisedByRole = role,
                                    reason = r,
                                    description = description.ifBlank { r.label },
                                )
                                aiSummary = parseSummary(AiTools.summarizeDispute(d.id))
                                submitted = true
                            }
                        },
                        enabled = reason != null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.Flag, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Submit dispute")
                    }
                }
                if (submitted) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Done") }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

private fun parseSummary(json: String): String {
    val key = "\"summary\":\""
    val i = json.indexOf(key)
    if (i < 0) return json
    val j = json.indexOf("\"", i + key.length)
    return if (j < 0) json else json.substring(i + key.length, j)
        .replace("\\n", " ")
        .replace("\\\"", "\"")
}