package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.navigation.Routes
import com.scottsx.app.ui.components.SectionHeader
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Buyer settings hub — 22 wired settings across account, commerce,
 * experience and help groups.
 */
@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val currentUser by SessionCache.user.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)),
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    )
                    .statusBarSpacer()
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (currentUser?.displayName ?: "S").firstOrNull()?.uppercase() ?: "S",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column {
                        Text(currentUser?.displayName ?: "Guest", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${currentUser?.email ?: "Not signed in"} · ${currentUser?.role ?: "buyer"}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    ) {
                        Icon(
                            Icons.Filled.AccountBox,
                            contentDescription = "Edit profile",
                            tint = Color.White,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }

        if (currentUser?.role == "seller") {
            item { SectionHeader("Store") }
            item { SettingsRow("Store profile", subtitle = "Name, description, logo", icon = Icons.Filled.Store, onClick = { onNavigate(Routes.sellerStoreSetting("store-profile")) }) }
            item { SettingsRow("Business info", subtitle = "Legal name, TIN, contacts", icon = Icons.Filled.Badge, onClick = { onNavigate(Routes.sellerStoreSetting("business-info")) }) }
            item { SettingsRow("Store location", icon = Icons.Filled.AddLocationAlt, onClick = { onNavigate(Routes.sellerStoreSetting("store-location")) }) }
            item { SettingsRow("Delivery", subtitle = "Fees, free threshold, COD", icon = Icons.Filled.LocalShipping, onClick = { onNavigate(Routes.sellerStoreSetting("delivery")) }) }
            item { SettingsRow("Payments", subtitle = "MoMo, bank", icon = Icons.Filled.Payments, onClick = { onNavigate(Routes.sellerStoreSetting("payments")) }) }
            item { SettingsRow("Notifications", icon = Icons.Filled.Notifications, onClick = { onNavigate(Routes.sellerStoreSetting("notifications")) }) }
            item { SettingsRow("Security", icon = Icons.Filled.Security, onClick = { onNavigate(Routes.sellerStoreSetting("security")) }) }
            item { SettingsRow("Policies", subtitle = "Returns, refunds, terms", icon = Icons.Filled.Description, onClick = { onNavigate(Routes.sellerStoreSetting("policies")) }) }
            item { SettingsRow("Help & contact", icon = Icons.Filled.HelpOutline, onClick = { onNavigate(Routes.sellerStoreSetting("help")) }) }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) }
        }

        item { SectionHeader("Account") }
        item { SettingsRow("Edit profile", icon = Icons.Filled.Person, onClick = { onNavigate(Routes.BUYER_PROFILE_SETTINGS) }) }
        item { SettingsRow("Account settings", subtitle = "Email, password, security", icon = Icons.Filled.Security, onClick = { onNavigate(Routes.ACCOUNT) }) }
        item { SettingsRow("My orders", icon = Icons.Filled.ReceiptLong, onClick = { onNavigate(Routes.ORDERS) }) }
        item { SettingsRow("Saved products", subtitle = "Wishlist", icon = Icons.Filled.Favorite, onClick = { onNavigate(Routes.SAVED_PRODUCTS) }) }
        item { SettingsRow("Become a seller", icon = Icons.Filled.Store, onClick = { onNavigate(Routes.BECOME_SELLER) }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) }

        item { SectionHeader("Commerce") }
        item { SettingsRow("Addresses", icon = Icons.Filled.AddLocationAlt, onClick = { onNavigate(Routes.ADDRESSES) }) }
        item { SettingsRow("Payment methods", subtitle = "MoMo & cards", icon = Icons.Filled.CreditCard, onClick = { onNavigate(Routes.PAYMENT_METHODS) }) }
        item { SettingsRow("Refunds", icon = Icons.Filled.ReceiptLong, onClick = { onNavigate(Routes.REFUNDS) }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) }

        item { SectionHeader("Experience") }
        item { SettingsRow("Theme", subtitle = "Light / dark / system", icon = Icons.Filled.Palette, onClick = { onNavigate(Routes.THEME) }) }
        item { SettingsRow("Notifications", icon = Icons.Filled.Notifications, onClick = { onNavigate(Routes.NOTIFICATIONS) }) }
        item { SettingsRow("AI personalisation", icon = Icons.Filled.AutoAwesome, onClick = { onNavigate(Routes.AI_PERSONALIZATION) }) }
        item { SettingsRow("Nearby sellers", icon = Icons.Filled.AddLocationAlt, onClick = { onNavigate(Routes.NEARBY) }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) }

        item { SectionHeader("About & help") }
        item { SettingsRow("Terms of service", icon = Icons.Filled.Description, onClick = { onNavigate(Routes.cms("terms")) }) }
        item { SettingsRow("Privacy policy", icon = Icons.Filled.Description, onClick = { onNavigate(Routes.cms("privacy")) }) }
        item { SettingsRow("Buyer protection", icon = Icons.Filled.Badge, onClick = { onNavigate(Routes.cms("buyer-protection")) }) }
        item { SettingsRow("About ScottsTechX", icon = Icons.Filled.Store, onClick = { onNavigate(Routes.cms("about")) }) }
        item { SettingsRow("Support & FAQs", icon = Icons.Filled.HelpOutline, onClick = { onNavigate(Routes.SUPPORT) }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) }

        item {
            SettingsRow(
                title = "Log out",
                icon = Icons.AutoMirrored.Filled.Logout,
                iconTint = ScottsTechXColors.ErrorRed,
                onClick = onLogout,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
