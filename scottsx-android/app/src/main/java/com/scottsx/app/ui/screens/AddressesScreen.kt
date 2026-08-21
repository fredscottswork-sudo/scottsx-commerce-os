package com.scottsx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Address
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Saved addresses CRUD. */
@Composable
fun AddressesScreen(onBack: () -> Unit) {
    var addresses by remember { mutableStateOf<List<Address>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("Home") }
    var line1 by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Kampala") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            addresses = V2Client.fetchAddresses()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Addresses", onBack = onBack)
        if (loading) {
            LoadingRow()
        } else if (addresses.isEmpty() && !showForm) {
            EmptyState("🏠", "No saved addresses", "Add an address for faster checkout.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(addresses) { address ->
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
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(address.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    if (address.isDefault) Text("✓", color = ScottsTechXColors.SuccessGreen, fontWeight = FontWeight.Bold)
                                }
                                Text("${address.line1} · ${address.city}, ${address.country}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = ScottsTechXColors.ErrorRed,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        scope.launch {
                                            V2Client.deleteAddress(address.id)
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
                            TextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                            Spacer(Modifier.height(8.dp))
                            TextField(value = line1, onValueChange = { line1 = it }, label = { Text("Street / area") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                            Spacer(Modifier.height(8.dp))
                            TextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Save address",
                                enabled = line1.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        V2Client.createAddress(Address(label = label, line1 = line1, city = city))
                                        showForm = false
                                        line1 = ""
                                        reload()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Floating add button
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Surface(
                color = ScottsTechXColors.BluePrimary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable { showForm = !showForm },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Text(if (showForm) "Close form" else "Add address", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = ScottsTechXColors.BluePrimary,
    unfocusedIndicatorColor = Color.Transparent,
)

/** Shared gradient header for settings sub-screens. */
@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(ScottsTechXColors.BlueHeroColors),
                RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onBack)
                    .padding(4.dp)
                    .size(32.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}
