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
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.components.SettingsScaffold
import com.scottsx.app.ui.components.SettingsSectionHeader
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Help Center — FAQ + quick links to support.
 */
@Composable
fun HelpCenterScreen(onBack: () -> Unit, onContact: () -> Unit, onTerms: () -> Unit, onPrivacy: () -> Unit, onReport: () -> Unit) {
    SettingsScaffold(title = "Help Center", onBack = onBack) {
        SettingsSectionHeader("Browse by topic")
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.ShoppingBag, "My Orders", "Track, return, or refund") { Unit }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.AccountBalanceWallet, "Payments", "Mobile money, cards, refunds") { Unit }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.VerifiedUser, "Buyer Protection", "Coverage & disputes") { onTerms() }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.Lock, "Account & Security", "Password, sign-in") { Unit }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.LocalShipping, "Shipping & Delivery", "Tracking, meet-ups") { Unit }
        Spacer(Modifier.height(6.dp))
        SettingsRow(Icons.Filled.Store, "Selling on ScottsTechX", "Become a seller") { Unit }

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
                .background(Color.White)
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
                        color = if (ok) ScottsTechXColors.BluePrimary else ScottsTechXColors.OnLightSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Report a problem.
 * Posts to POST /api/v1/reports.
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
                .background(Color.White)
                .padding(16.dp),
        ) {
            Column {
                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ScottsTechXColors.OnLightSecondary)
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
                                color = if (sel) Color.White else ScottsTechXColors.OnLight,
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
