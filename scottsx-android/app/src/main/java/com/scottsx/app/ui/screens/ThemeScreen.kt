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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.preferences.UserPrefs
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Theme picker. Three options:
 *   - System  (follows Android theme)
 *   - Light
 *   - Dark
 *
 * The selection is persisted to [UserPrefs] and is read by the
 * root Composable at app launch. The change is applied immediately
 * via the global color scheme switch in [ScottsTechXTheme].
 */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val themePref = remember { com.scottsx.app.data.preferences.ThemePreference.get(ctx) }
    var selected by remember { mutableStateOf(UserPrefs.get(ctx).themeMode()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.PanelLight)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar
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
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("Theme", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Choose how ScottsTechX looks",
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))

            ThemeOption(
                icon = Icons.Filled.SettingsBrightness,
                title = "System",
                subtitle = "Follows your device theme",
                selected = selected == "system",
                onClick = {
                    selected = "system"
                    UserPrefs.get(ctx).setThemeMode("system")
                    themePref.set(com.scottsx.app.data.preferences.ThemeMode.SYSTEM)
                },
            )
            Spacer(Modifier.height(8.dp))

            ThemeOption(
                icon = Icons.Filled.LightMode,
                title = "Light",
                subtitle = "Bright background, easy on the eyes during the day",
                selected = selected == "light",
                onClick = {
                    selected = "light"
                    UserPrefs.get(ctx).setThemeMode("light")
                    themePref.set(com.scottsx.app.data.preferences.ThemeMode.LIGHT)
                },
            )
            Spacer(Modifier.height(8.dp))

            ThemeOption(
                icon = Icons.Filled.DarkMode,
                title = "Dark",
                subtitle = "Dark background, saves battery at night",
                selected = selected == "dark",
                onClick = {
                    selected = "dark"
                    UserPrefs.get(ctx).setThemeMode("dark")
                    themePref.set(com.scottsx.app.data.preferences.ThemeMode.DARK)
                },
            )
        }
    }
}

@Composable
private fun ThemeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE3F2FD) else Color.White,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) ScottsTechXColors.BluePrimary else ScottsTechXColors.PanelLight,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else ScottsTechXColors.OnPanelSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ScottsTechXColors.OnLight,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = ScottsTechXColors.OnLightSecondary,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ScottsTechXColors.BluePrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "OK",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
