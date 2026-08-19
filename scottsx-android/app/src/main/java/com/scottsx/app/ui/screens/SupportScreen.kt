package com.scottsx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Faq
import com.scottsx.app.data.domain.SupportTicket
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.ListDivider
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.StatusChip
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Support screen: FAQs + create a support ticket + ticket history. */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    var faqs by remember { mutableStateOf<List<Faq>>(emptyList()) }
    var tickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            faqs = V2Client.fetchFaqs()
            tickets = V2Client.fetchSupportTickets()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Support & FAQs", onBack = onBack)

        if (loading) {
            LoadingRow()
        } else {
            Text("Frequently asked questions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            faqs.forEach { faq ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(faq.question, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(faq.answer, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ListDivider()
            }

            Spacer(Modifier.height(8.dp))
            Text("Your tickets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (tickets.isEmpty() && !showForm) {
                EmptyState("🎫", "No support tickets", "We're here to help — open a ticket below.")
            } else {
                tickets.forEach { ticket ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row {
                                Text(ticket.subject, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                StatusChip(ticket.status)
                            }
                            Text(ticket.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (showForm) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InputField(value = subject, onValueChange = { subject = it }, label = "Subject", placeholder = "What's the issue?")
                    Spacer(Modifier.height(10.dp))
                    InputField(value = message, onValueChange = { message = it }, label = "Message", placeholder = "Describe the problem…")
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Open ticket",
                        enabled = subject.isNotBlank() && message.isNotBlank(),
                        onClick = {
                            scope.launch {
                                V2Client.createSupportTicket(subject, message)
                                showForm = false
                                subject = ""
                                message = ""
                                reload()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                color = ScottsTechXColors.BluePrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { showForm = !showForm },
            ) {
                Text(
                    if (showForm) "Close form" else "+ Open a support ticket",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
