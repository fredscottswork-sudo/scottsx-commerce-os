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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.scottsx.app.data.Session
import com.scottsx.app.data.domain.Role
import com.scottsx.app.data.domain.SessionCache
import com.scottsx.app.data.preferences.ThemeMode
import com.scottsx.app.data.preferences.ThemePreference
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Stage 5.x — Become-a-Seller upgrade CTA.
 *
 * When the buyer taps this screen we promote their account to the
 * SELLER role on the backend, refresh [SessionCache], and bounce
 * them to the seller dashboard. The actual store setup (name, logo,
 * payout details) happens on the first visit to StoreSettingsScreen.
 */
@Composable
fun BecomeSellerScreen(
    onBack: () -> Unit,
    onUpgraded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val themePref = remember(ctx) { ThemePreference.get(ctx) }
    val themeMode = themePref.themeFlow.collectAsState().value
    val isDark = themeMode == ThemeMode.DARK
    var isUpgrading by remember { mutableStateOf(false) }
    var upgradeError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) ScottsTechXColors.BackgroundDark else ScottsTechXColors.BackgroundLight)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ScottsTechXColors.BluePrimaryDark,
                            ScottsTechXColors.BluePrimary,
                        ),
                    ),
                )
                .padding(top = 36.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Become a Seller",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
        }

        // Hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
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
                Icon(Icons.Filled.Storefront, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Start selling on ScottsTechX",
                color = if (isDark) ScottsTechXColors.OnDark else ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text("Reach thousands of buyers across Uganda. No monthly fee, no listing limits.",
                color = if (isDark) ScottsTechXColors.OnDarkSecondary else ScottsTechXColors.OnLightSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        // Feature list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            FeatureRow("List unlimited products", Icons.Filled.Bolt)
            FeatureRow("Real-time chat with buyers", Icons.Filled.Storefront)
            FeatureRow("Built-in delivery agreements", Icons.Filled.Bolt)
            FeatureRow("Smart analytics dashboard", Icons.Filled.Bolt)
        }

        Spacer(Modifier.height(24.dp))

        // Upgrade button
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (upgradeError != null) {
                Text(upgradeError!!,
                    color = Color(0xFF991B1B),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
            PrimaryButton(
                text = if (isUpgrading) "Upgrading…" else "Upgrade my account",
                enabled = !isUpgrading,
                loading = isUpgrading,
                onClick = {
                    scope.launch {
                        isUpgrading = true
                        upgradeError = null
                        try {
                            val ok = V2Client.upgradeToSeller()
                            if (ok) {
                                SessionCache.role = Role.SELLER
                                android.widget.Toast.makeText(
                                    ctx, "Welcome to the seller side!",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                onUpgraded()
                            } else {
                                upgradeError = "Couldn't upgrade right now. Try again later."
                            }
                        } catch (t: Throwable) {
                            upgradeError = "Network error: ${t.message}"
                        } finally {
                            isUpgrading = false
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "By upgrading you agree to the seller terms. You can switch back to buyer mode anytime in Settings.",
                color = if (isDark) ScottsTechXColors.OnDarkSecondary else ScottsTechXColors.OnLightSecondary,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null,
                tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = ScottsTechXColors.OnLight,
            fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
