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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ai.AiPersonalizationStore
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.theme.ScottsTechXColors

/** AI personalisation — name/city context and on/off toggle. */
@Composable
fun AiPersonalizationScreen(onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(AiPersonalizationStore.enabled) }
    var name by remember { mutableStateOf(AiPersonalizationStore.name) }
    var city by remember { mutableStateOf(AiPersonalizationStore.city) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("AI personalisation", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Let the assistant greet you by name and tailor answers to your city.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    enabled = !enabled
                    AiPersonalizationStore.enabled = enabled
                },
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Personalise answers", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Adds your name + city to every prompt", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    AiPersonalizationStore.enabled = it
                },
            )
        }
        Spacer(Modifier.height(18.dp))

        InputField(
            value = name,
            onValueChange = {
                name = it
                AiPersonalizationStore.name = it
            },
            label = "Your name",
            placeholder = "e.g. Kato",
            enabled = enabled,
        )
        Spacer(Modifier.height(12.dp))
        InputField(
            value = city,
            onValueChange = {
                city = it
                AiPersonalizationStore.city = it
            },
            label = "Your city",
            placeholder = "e.g. Kampala",
            enabled = enabled,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Preview: ${if (enabled) "Hi $name from $city 👋" else "Personalisation is off"}",
            color = if (enabled) ScottsTechXColors.SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Changes save automatically.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
