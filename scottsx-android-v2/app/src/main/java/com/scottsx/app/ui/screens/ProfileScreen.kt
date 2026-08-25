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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.BuyerProfile
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.theme.ScottsTechXColors

@Composable
fun ProfileScreen(
    profile: BuyerProfile,
    onBack: () -> Unit,
    onTabSelect: (BottomTab) -> Unit,
    onSignOut: () -> Unit,
    onSwitchAccount: () -> Unit = {},
    onOpenSection: (String) -> Unit = {},
    onEditProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var bottomTab by remember { mutableStateOf(BottomTab.Profile) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundLight),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary),
                            ),
                        )
                        .padding(top = 36.dp, bottom = 22.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (profile.displayName.firstOrNull()?.uppercase() ?: "U").toString(),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                            )
                            Text(
                                text = profile.email.ifBlank { "buyer@scottsx.app" },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Edit",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // ACCOUNT
            item {
                Spacer(Modifier.size(8.dp))
                SectionGroup(title = "ACCOUNT") {
                    Setting(Icons.Filled.Settings, "Personal Information", "Name, email, phone") { onOpenSection("account") }
                    Setting(Icons.Filled.LocationOn, "Addresses", "Manage delivery addresses") { onOpenSection("addresses") }
                    Setting(Icons.Filled.Home, "Saved Locations", "Home, work, other") { onOpenSection("saved-locations") }
                    Setting(Icons.Filled.CreditCard, "Payment Methods", "Mobile money, cards") { onOpenSection("payments") }
                }
            }

            // SHOPPING
            item {
                SectionGroup(title = "SHOPPING") {
                    Setting(Icons.Filled.Receipt, "My Orders", "Track and view orders") { onOpenSection("orders") }
                    Setting(Icons.Filled.ShoppingBag, "Track Orders", "Live tracking") { onOpenSection("track-orders") }
                    Setting(Icons.Filled.Refresh, "Returns & Refunds", "Recent returns") { onOpenSection("refunds") }
                    Setting(Icons.Filled.Favorite, "Saved Products", "Your favorites") { onOpenSection("saved-products") }
                    Setting(Icons.Filled.Star, "Favorite Sellers", "Sellers you follow") { onOpenSection("saved-sellers") }
                }
            }

            // MARKETPLACE
            item {
                SectionGroup(title = "MARKETPLACE") {
                    Setting(Icons.Filled.LocationOn, "Nearby", "Find products near you") { onOpenSection("nearby") }
                    Setting(Icons.Filled.SmartToy, "AI Assistant", "Smart recommendations") { onOpenSection("ai") }
                    Setting(Icons.Filled.Notifications, "Notifications", "Manage push alerts") { onOpenSection("notifications") }
                    Setting(Icons.Filled.Receipt, "Seller Messages", "Your conversations") { onOpenSection("messages") }
                    Setting(Icons.Filled.PrivacyTip, "Buyer Protection", "Shop safely") { onOpenSection("buyer-protection") }
                }
            }

            // APP
            item {
                SectionGroup(title = "APP") {
                    Setting(Icons.Filled.DarkMode, "Appearance", "Light / Dark mode") { onOpenSection("theme") }
                    Setting(Icons.Filled.Settings, "Language", "English (Uganda)") { onOpenSection("language") }
                    Setting(Icons.Filled.History, "Currency", "UGX — Uganda Shilling") { onOpenSection("currency") }
                    Setting(Icons.Filled.Notifications, "Notification Settings", "Sounds, alerts") { onOpenSection("notifications") }
                }
            }

            // SUPPORT
            item {
                SectionGroup(title = "SUPPORT") {
                    Setting(Icons.Filled.Help, "Help Center", "FAQs and guides") { onOpenSection("help") }
                    Setting(Icons.Filled.SupportAgent, "Contact ScottsTechX", "Reach support") { onOpenSection("contact") }
                    Setting(Icons.Filled.Lock, "Report a Problem", "Send a bug report") { onOpenSection("report") }
                    Setting(Icons.Filled.Description, "Terms of Service", "Read terms") { onOpenSection("terms") }
                    Setting(Icons.Filled.PrivacyTip, "Privacy Policy", "Read policy") { onOpenSection("privacy-policy") }
                }
            }

            // ACCOUNT ACTIONS — Switch account + About. Visible so the
            // user can pick a different email without having to fully
            // sign out (helps QA test multi-account flows fast).
            item {
                SectionGroup(title = "ACCOUNT ACTIONS") {
                    Setting(
                        Icons.Filled.SwitchAccount,
                        "Switch account",
                        "Sign in with a different email",
                        onClick = onSwitchAccount,
                    )
                    Setting(
                        Icons.Filled.Info,
                        "About ScottsTechX",
                        "Version 0.20.0 — build 2026.08.10",
                    )
                }
            }

            // Logout
            item {
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFEE2E2))
                            .clickable { onSignOut() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Logout,
                                contentDescription = null,
                                tint = Color(0xFFB91C1C),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Log Out",
                                color = Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            ScottsTechXBottomBar(
                selected = bottomTab,
                onSelect = { tab ->
                    bottomTab = tab
                    onTabSelect(tab)
                },
            )
        }
    }
}

@Composable
private fun SectionGroup(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = ScottsTechXColors.OnLightSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
        ) {
            content()
        }
    }
}

@Composable
private fun Setting(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
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
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = subtitle,
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 11.sp,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = ScottsTechXColors.OnLightSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
