package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Profile settings hub. Every row routes to a real destination via
 * [onOpenSection]. The 11 sections cover:
 *  - Account       (avatar, name, email, phone, bio, gender, DoB)
 *  - Security      (password, 2FA) -- routes to SecurityScreen
 *  - Addresses     (saved delivery addresses)
 *  - Payments      (saved payment methods)
 *  - Notifications (notification settings)
 *  - Privacy       (privacy settings)
 *  - Language      (language picker)
 *  - Theme         (theme picker)
 *  - Help & Support(help center)
 *  - Legal         (terms, privacy policy)
 *  - Delete Account(permanent deletion)
 */
@Composable
fun ProfileSettingsScreen(
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.PanelLight)
            .verticalScroll(rememberScrollState()),
    ) {
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                    tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Profile Settings", color = Color.White,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            SectionHeader("Account")
            Item(Icons.Filled.Person, "Account", "Personal info, email, phone") { onOpenSection("account") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Lock, "Security", "Password, 2FA") { onOpenSection("security") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.LocationOn, "Saved Addresses", "Manage delivery addresses") { onOpenSection("addresses") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.CreditCard, "Payment Methods", "Mobile money, cards") { onOpenSection("payments") }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Preferences")
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Notifications, "Notifications", "Push, email, SMS") { onOpenSection("notifications") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.PrivacyTip, "Privacy", "Data & sharing") { onOpenSection("privacy") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.SwipeRight, "Language", "English (UK)") { onOpenSection("language") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.SettingsBrightness, "Theme", "Light / Dark / System") { onOpenSection("theme") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.AttachMoney, "Currency", "UGX") { onOpenSection("currency") }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Orders")
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.ShoppingBag, "My Orders", "Track, return, or refund") { onOpenSection("orders") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Favorite, "Saved Products", "Items you saved for later") { onOpenSection("saved-products") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Store, "Favorite Sellers", "Shops you follow") { onOpenSection("saved-sellers") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.VerifiedUser, "Buyer Protection", "Refunds & returns policy") { onOpenSection("buyer-protection") }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Help")
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Help, "Help Center", "FAQ & how-tos") { onOpenSection("help") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.ContactSupport, "Contact Us", "Email, phone, office") { onOpenSection("contact") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.BugReport, "Report a Problem", "Tell us what's broken") { onOpenSection("report") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.List, "Account Activity", "Recent sign-ins & changes") { onOpenSection("audit") }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Legal")
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Policy, "Terms of Service", "Read our terms") { onOpenSection("terms") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.PrivacyTip, "Privacy Policy", "How we handle your data") { onOpenSection("privacy-policy") }
            Spacer(Modifier.height(6.dp))
            Item(Icons.Filled.Info, "About ScottsTechX", "Our story, founded by Kato Fred") { onOpenSection("about") }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Account Management")
            Spacer(Modifier.height(6.dp))
            Item(
                icon = Icons.Filled.DeleteForever,
                title = "Delete Account",
                subtitle = "Permanently delete your account",
                titleColor = Color(0xFFB91C1C),
                onClick = { onOpenSection("delete-account") },
            )
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
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun Item(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = ScottsTechXColors.OnLight,
    onClick: () -> Unit,
) {
    SettingsRow(icon = icon, title = title, subtitle = subtitle, titleColor = titleColor, onClick = onClick)
}

