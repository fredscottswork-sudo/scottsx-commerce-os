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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Store settings — all the operations a seller needs to manage the
 * business side of their storefront. Tapping a row opens a dedicated
 * management screen (Stage 4).
 */
@Composable
fun StoreSettingsScreen(
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.BluePrimaryDark)
                .padding(start = 4.dp, end = 16.dp, top = 30.dp, bottom = 12.dp),
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Store Settings", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            SectionHeader("Store")
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Verified, "Store Profile", "Name, logo, description") { onOpenSection("store-profile") }
            Spacer(Modifier.height(8.dp))
            Item(Icons.Filled.Business, "Business Information", "Verify your business") { onOpenSection("business-info") }
            Spacer(Modifier.height(8.dp))
            Item(Icons.Filled.LocationOn, "Store Location", "Address and pickup points") { onOpenSection("store-location") }
            Spacer(Modifier.height(8.dp))
            Item(Icons.Filled.LocalShipping, "Delivery Settings", "Delivery radius, fee, options") { onOpenSection("delivery") }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Payments")
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.CreditCard, "Payment Settings", "Mobile money, bank accounts") { onOpenSection("payments") }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Preferences")
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Notifications, "Notification Settings", "Buyers, orders, marketing") { onOpenSection("notifications") }
            Spacer(Modifier.height(8.dp))
            Item(Icons.Filled.Security, "Security", "Password, 2FA") { onOpenSection("security") }
            Spacer(Modifier.height(8.dp))
            Item(Icons.Filled.Policy, "Store Policies", "Returns, refunds, terms") { onOpenSection("policies") }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Help")
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Help, "Help & Support", "FAQs, contact us") { onOpenSection("help") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        color = ScottsTechXColors.OnLightSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SettingsRow(icon = icon, title = title, subtitle = subtitle, onClick = onClick)
}
