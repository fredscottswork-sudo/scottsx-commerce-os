package com.scottsx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.PaymentMethod
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Payment methods (Mobile Money / card). */
@Composable
fun PaymentMethodsScreen(onBack: () -> Unit) {
    var methods by remember { mutableStateOf<List<PaymentMethod>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("momo") }
    var phone by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("MTN MoMo") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            methods = V2Client.fetchPaymentMethods()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Payment methods", onBack = onBack)
        if (loading) {
            LoadingRow()
        } else if (methods.isEmpty() && !showForm) {
            EmptyState("💳", "No payment methods", "Add Mobile Money or a card.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(methods) { method ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (method.type == "momo") "📲" else "💳", fontSize = 22.sp)
                            Spacer(Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(method.label.ifBlank { method.type.uppercase() }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    if (method.type == "momo") method.phone else "•••• ${method.last4}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (method.isDefault) Text("Default", color = ScottsTechXColors.SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = ScottsTechXColors.ErrorRed,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        scope.launch {
                                            V2Client.deletePaymentMethod(method.id)
                                            reload()
                                        }
                                    }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
                if (showForm) {
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TypePill("📲 MoMo", type == "momo") { type = "momo"; label = "MTN MoMo" }
                                TypePill("💳 Card", type == "card") { type = "card"; label = "Visa / Mastercard" }
                            }
                            Spacer(Modifier.height(10.dp))
                            InputField(value = label, onValueChange = { label = it }, label = "Label", placeholder = "MTN MoMo")
                            Spacer(Modifier.height(10.dp))
                            InputField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = if (type == "momo") "MoMo number" else "Last 4 digits",
                                placeholder = if (type == "momo") "07xx xxx xxx" else "1234",
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Save",
                                enabled = phone.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        V2Client.createPaymentMethod(
                                            PaymentMethod(
                                                type = type,
                                                label = label,
                                                phone = if (type == "momo") phone else "",
                                                last4 = if (type == "card") phone else "",
                                                isDefault = methods.isEmpty(),
                                            ),
                                        )
                                        showForm = false
                                        phone = ""
                                        reload()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                color = ScottsTechXColors.BluePrimary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.clickable { showForm = !showForm },
            ) {
                Text(
                    if (showForm) "Close form" else "+ Add payment method",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TypePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
