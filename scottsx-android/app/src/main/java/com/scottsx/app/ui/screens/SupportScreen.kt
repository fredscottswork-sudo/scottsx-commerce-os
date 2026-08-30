package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.Session
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.components.SettingsScaffold
import com.scottsx.app.ui.components.SettingsSectionHeader
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Help Center — web-parity Support page:
 * GET /me/faqs + GET /me/support/tickets + open-ticket dialog.
 */
@Composable
fun HelpCenterScreen(
    onBack: () -> Unit,
    onContact: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onReport: () -> Unit,
    onOpenOrders: () -> Unit = {},
    onOpenPayments: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
) {
    var faqs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tickets by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var ticketsLoading by remember { mutableStateOf(Session.tokenOrNull() != null) }
    var showTicketDialog by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // FAQs are public — fetch them even signed out.
        val fArr = V2Client.fetchFaqs()
        faqs = buildList { for (i in 0 until (fArr?.length() ?: 0)) fArr?.optJSONObject(i)?.let { add(it) } }
        if (Session.tokenOrNull() != null) {
            ticketsLoading = true
            val tArr = V2Client.fetchTickets()
            tickets = buildList { for (i in 0 until (tArr?.length() ?: 0)) tArr?.optJSONObject(i)?.let { add(it) } }
            ticketsLoading = false
        }
    }

    SettingsScaffold(title = "Support", onBack = onBack) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ScottsTechXColors.BluePrimary)
                .clickable(enabled = Session.tokenOrNull() != null) { showTicketDialog = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Open a support ticket", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(
                    if (Session.tokenOrNull() != null) "Lands in your ticket list below — same inbox as the web."
                    else "Sign in to open tickets.",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── FAQs — the same list the web Support page shows ─────────────
        SettingsSectionHeader("Frequently asked questions")
        Spacer(Modifier.height(6.dp))
        if (faqs.isEmpty()) {
            Text(
                "Loading FAQs…",
                color = ScottsTechXColors.OnCardSecondary, fontSize = 12.5.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            faqs.forEach { f ->
                FaqCard(f.optString("question"), f.optString("answer"))
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── Your tickets — real status badges like the web ──────────────
        if (Session.tokenOrNull() != null) {
            Spacer(Modifier.height(16.dp))
            SettingsSectionHeader("Your tickets")
            Spacer(Modifier.height(6.dp))
            if (ticketsLoading) {
                Text("Loading…", color = ScottsTechXColors.OnCardSecondary, fontSize = 12.5.sp)
            } else if (tickets.isEmpty()) {
                Text(
                    "No tickets yet — anything you send lands here.",
                    color = ScottsTechXColors.OnCardSecondary, fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                tickets.forEach { t ->
                    TicketCard(t)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionHeader("Browse by topic")
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.ShoppingBag, "My Orders", "Track, return, or refund") { onOpenOrders() }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.AccountBalanceWallet, "Payments", "Mobile money, cards, refunds") { onOpenPayments() }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.VerifiedUser, "Buyer Protection", "Coverage & disputes") { onTerms() }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.Lock, "Account & Security", "Password, sign-in") { onOpenAccount() }

        Spacer(Modifier.height(16.dp))
        SettingsSectionHeader("Get in touch")
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.ContactSupport, "Contact support", "Email, phone, office hours") { onContact() }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.BugReport, "Report a problem", "Tell us what's broken") { onReport() }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.Policy, "Terms of Service", null) { onTerms() }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.PrivacyTip, "Privacy Policy", null) { onPrivacy() }

        toast?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = ScottsTechXColors.BluePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showTicketDialog) {
        var subject by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!sending) showTicketDialog = false },
            title = { Text("Open a support ticket", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject", fontSize = 11.sp) },
                        singleLine = true,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Describe the problem…", fontSize = 11.sp) },
                        minLines = 4,
                        maxLines = 6,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        sending = true
                        scope.launch {
                            val id = V2Client.createTicket("general", subject.trim(), message.trim())
                            sending = false
                            if (id != null) {
                                showTicketDialog = false
                                toast = "Ticket opened."
                                val tArr = V2Client.fetchTickets()
                                tickets = buildList { for (i in 0 until (tArr?.length() ?: 0)) tArr?.optJSONObject(i)?.let { add(it) } }
                            } else {
                                toast = "Couldn't send — try again."
                            }
                        }
                    },
                    enabled = !sending && subject.isNotBlank() && message.isNotBlank(),
                ) { Text(if (sending) "Opening…" else "Open ticket") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTicketDialog = false }, enabled = !sending) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun FaqCard(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScottsTechXColors.CardSurface)
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                question, color = ScottsTechXColors.OnCard,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = ScottsTechXColors.OnCardSecondary, modifier = Modifier.size(18.dp),
            )
        }
        if (expanded && answer.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(answer, color = ScottsTechXColors.OnCardSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TicketCard(t: JSONObject) {
    val status = t.optString("status", "open").lowercase()
    val (bg, fg) = when (status) {
        "closed", "resolved" -> Color(0xFFECFDF5) to Color(0xFF047857)
        "pending", "in_progress" -> Color(0xFFFFFBEB) to Color(0xFFB45309)
        else -> Color(0xFFEFF6FF) to Color(0xFF1D4ED8)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScottsTechXColors.CardSurface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SupportAgent, null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                t.optString("subject"), color = ScottsTechXColors.OnCard,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    status.replaceFirstChar { it.uppercase() },
                    color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        val msg = t.optString("message")
        if (msg.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(msg, color = ScottsTechXColors.OnCardSecondary, fontSize = 11.5.sp, maxLines = 3)
        }
    }
}

/**
 * Contact — opens a form to send an email to support.
 * Posts to POST /api/v1/support/tickets with category="contact".
 */
@Composable
fun ContactScreen(onBack: () -> Unit) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    SettingsScaffold(title = "Contact Us", onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ScottsTechXColors.CardSurface)
                .padding(16.dp),
        ) {
            Column {
                Text("ScottsTechX Support", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("Email: support@scottsx.app", fontSize = 13.sp)
                Text("Phone: +256 700 000000", fontSize = 13.sp)
                Text("Office: Kampala, Uganda", fontSize = 13.sp)
                Text("Hours: Mon-Fri 8am - 6pm EAT", fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Text("Send us a message", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                FieldRow("Subject", subject) { subject = it }
                FieldRow("Message", message, lines = 4) { message = it }
                Spacer(Modifier.height(12.dp))
                SaveButton(saving = status == "sending", onSave = {
                    status = "sending"
                    scope.launch {
                        val id = V2Client.createTicket("contact", subject, message)
                        status = if (id != null) "sent" else "failed"
                        if (status == "sent") { subject = ""; message = "" }
                    }
                })
                status?.let {
                    val ok = it == "sent"
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (ok) "Message sent. We'll respond within 24 hours."
                        else if (it == "failed") "Failed to send. Try again."
                        else "Sending...",
                        color = if (ok) ScottsTechXColors.BluePrimary else ScottsTechXColors.OnCardSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Report a problem. Delivers the report as a support ticket
 * (POST /api/v1/me/support/tickets) — the inbox staff actually read.
 */
@Composable
fun ReportProblemScreen(onBack: () -> Unit) {
    var resourceTypeIndex by remember { mutableStateOf(0) }
    var resourceId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val resourceTypes = listOf("product", "seller", "user", "message")

    SettingsScaffold(title = "Report a Problem", onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ScottsTechXColors.CardSurface)
                .padding(16.dp),
        ) {
            Column {
                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ScottsTechXColors.OnCardSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    resourceTypes.forEachIndexed { i, t ->
                        val sel = resourceTypeIndex == i
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) ScottsTechXColors.BluePrimary else Color(0xFFE5E7EB))
                                .clickable { resourceTypeIndex = i }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                t.replaceFirstChar { it.uppercase() },
                                color = if (sel) Color.White else ScottsTechXColors.OnCard,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldRow("Resource ID", resourceId, hint = "Paste the ID of the offending item") { resourceId = it }
                FieldRow("Reason", reason, lines = 2, hint = "Spam, scam, fraud, etc.") { reason = it }
                FieldRow("Details", description, lines = 3) { description = it }
                Spacer(Modifier.height(12.dp))
                SaveButton(saving = status == "sending", onSave = {
                    if (resourceId.isBlank() || reason.isBlank()) {
                        status = "missing"
                        return@SaveButton
                    }
                    status = "sending"
                    scope.launch {
                        val id = V2Client.createReport(
                            resourceTypes[resourceTypeIndex],
                            resourceId,
                            reason,
                            description.takeIf { it.isNotBlank() },
                        )
                        status = if (id != null) "sent" else "failed"
                        if (status == "sent") {
                            resourceId = ""; reason = ""; description = ""
                        }
                    }
                })
                status?.let {
                    val ok = it == "sent"
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (it) {
                            "sent" -> "Thank you - we'll review this report within 24 hours."
                            "failed" -> "Failed to send - please try again later."
                            "missing" -> "Resource ID and reason are both required."
                            else -> "Sending..."
                        },
                        color = if (ok) ScottsTechXColors.BluePrimary else Color(0xFFB91C1C),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
