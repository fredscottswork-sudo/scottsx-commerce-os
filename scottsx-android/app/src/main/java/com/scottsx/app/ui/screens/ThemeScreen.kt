package com.scottsx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.UserPrefs
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Theme switcher — saves to UserPrefs and to the backend preferences. */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    var mode by remember { mutableStateOf(UserPrefs.themeMode) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Theme", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        ThemeOption("light", "☀️ Light", "Bright panels, dark text", mode) {
            mode = it
            UserPrefs.themeMode = it
            scope.launch { V2Client.saveSettings(V2Client.fetchUserSettings().copy(theme = it)) }
        }
        ThemeOption("dark", "🌙 Dark", "Low-light friendly", mode) {
            mode = it
            UserPrefs.themeMode = it
            scope.launch { V2Client.saveSettings(V2Client.fetchUserSettings().copy(theme = it)) }
        }
        ThemeOption("system", "🖥️ System", "Follow the device setting", mode) {
            mode = it
            UserPrefs.themeMode = it
            scope.launch { V2Client.saveSettings(V2Client.fetchUserSettings().copy(theme = it)) }
        }
    }
}

@Composable
private fun ThemeOption(
    value: String,
    title: String,
    subtitle: String,
    selectedMode: String,
    onSelect: (String) -> Unit,
) {
    Surface(
        color = if (selectedMode == value) ScottsTechXColors.BluePrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onSelect(value) },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title.substringAfter(" "), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selectedMode == value, onClick = { onSelect(value) })
        }
    }
}
