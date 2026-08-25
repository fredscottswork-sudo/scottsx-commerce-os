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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.scottsx.app.ai.AiPersonalizationStore

/**
 * Stage 4 — AI Personalization settings screen.
 *
 * The user can:
 *  - Toggle personalization on/off
 *  - See every signal the AI has stored
 *  - Clear AI memory entirely
 *
 * ScottsTechX does not infer sensitive characteristics and does not
 * create hidden profiles.
 */
@Composable
fun AiPersonalizationScreen(onBack: () -> Unit = {}) {
    var confirmClear by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Text("AI Personalization", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "ScottsTechX AI learns from your interactions so it can adapt suggestions to you. " +
                                "No sensitive characteristics are inferred. You can disable personalization or clear memory at any time.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            item {
                SettingRow(
                    title = "Personalization",
                    subtitle = if (AiPersonalizationStore.enabled.value) "Enabled" else "Disabled",
                    trailing = {
                        Switch(
                            checked = AiPersonalizationStore.enabled.value,
                            onCheckedChange = { AiPersonalizationStore.setEnabled(it) },
                        )
                    },
                )
            }

            // Categories
            item {
                SignalSection(
                    title = "Frequent categories",
                    subtitle = "Categories you've browsed recently.",
                )
            }
            item {
                if (AiPersonalizationStore.frequentCategories.isEmpty()) {
                    EmptySignal("No categories recorded yet.")
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(AiPersonalizationStore.frequentCategories) { c ->
                            SignalChip(text = c.displayName)
                        }
                    }
                }
            }

            // Recent searches
            item { SignalSection(title = "Recent searches", subtitle = "Products you've searched for.") }
            item {
                if (AiPersonalizationStore.recentSearches.isEmpty()) {
                    EmptySignal("No recent searches recorded.")
                } else {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        AiPersonalizationStore.recentSearches.forEach { s ->
                            Text("• $s", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }

            // Price range
            item { SignalSection(title = "Price range of interest", subtitle = "Lowest and highest prices you've recently browsed.") }
            item {
                val lo = AiPersonalizationStore.preferredPriceLowUgx.value
                val hi = AiPersonalizationStore.preferredPriceHighUgx.value
                if (lo != null && hi != null) {
                    Text(
                        "UGX ${lo} – UGX ${hi}",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    EmptySignal("No price range recorded.")
                }
            }

            // Quick actions
            item { SignalSection(title = "Quick action usage", subtitle = "Which shortcuts you use most.") }
            item {
                val map = AiPersonalizationStore.quickActionCounts.value
                if (map.isEmpty()) {
                    EmptySignal("No quick action usage recorded.")
                } else {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        map.entries.sortedByDescending { it.value }.forEach { e ->
                            Text("• ${e.key} ×${e.value}", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
            item {
                Button(
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear AI memory")
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear AI memory?") },
            text = { Text("This removes all personalization signals ScottsTechX AI has recorded about your browsing and search history. You can keep using the AI — it will just start fresh.") },
            confirmButton = {
                Button(
                    onClick = {
                        AiPersonalizationStore.clearMemory()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) { Text("Clear") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            trailing()
        }
    }
}

@Composable
private fun SignalSection(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun EmptySignal(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
}

@Composable
private fun SignalChip(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF3B82F6).copy(alpha = 0.10f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = Color(0xFF3B82F6),
            fontWeight = FontWeight.SemiBold,
        )
    }
}