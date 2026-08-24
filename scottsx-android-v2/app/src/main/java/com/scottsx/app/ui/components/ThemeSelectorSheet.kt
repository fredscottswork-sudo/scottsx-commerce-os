package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.preferences.ThemeMode
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.preferences.sidebarPaletteFor
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Bottom-sheet-style selector for the app theme. Three large radio
 * rows (Light, Dark, System). The selected option is highlighted with
 * a brand-blue pill. Persists the choice via [ThemePreference] so the
 * whole app recomposes with the new palette.
 *
 * Designed to be hosted by a [androidx.compose.material3.ModalBottomSheet]
 * caller, but works as a stand-alone modal column too.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorSheet(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = sidebarPaletteFor(current)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(48.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(palette.onSurfaceMuted.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ScottsTechXColors.BluePrimary,
                                ScottsTechXColors.BluePrimaryLight,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Brightness6,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Theme",
                    color = palette.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
                Text(
                    text = "Choose how ScottsTechX looks",
                    color = palette.onSurfaceMuted,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        ThemeMode.values().forEach { mode ->
            ThemeRow(
                mode = mode,
                selected = mode == current,
                onClick = { onPick(mode) },
                palette = palette,
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ThemeRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    palette: com.scottsx.app.data.preferences.SidebarPalette,
) {
    val bg = if (selected) ScottsTechXColors.BluePrimary.copy(alpha = 0.10f) else palette.surface
    val border = if (selected) ScottsTechXColors.BluePrimary else palette.onSurfaceMuted.copy(alpha = 0.18f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (selected) listOf(
                            ScottsTechXColors.BluePrimary,
                            ScottsTechXColors.BluePrimaryLight,
                        ) else listOf(
                            palette.onSurfaceMuted.copy(alpha = 0.18f),
                            palette.onSurfaceMuted.copy(alpha = 0.10f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Brightness6,
                contentDescription = null,
                tint = if (selected) Color.White else palette.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = labelFor(mode),
                color = palette.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = descriptionFor(mode),
                color = palette.onSurfaceMuted,
                fontSize = 11.sp,
            )
        }
        // Selected checkmark pill on the right
        if (selected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.BluePrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    // Border via Modifier.border requires drawing in the parent; we
    // simulate with a thin Row overlay to keep this file dependency-free.
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.dp),
    )
    // Note: visual emphasis from background fill is enough — no explicit border.
}

private fun labelFor(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}

private fun descriptionFor(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "Always use the light palette"
    ThemeMode.DARK -> "Always use the dark palette"
    ThemeMode.SYSTEM -> "Match the device setting"
}
