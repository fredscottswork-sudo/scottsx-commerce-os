package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.ViewInAr
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
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Marketplace tools — promotes, offers, flash sales, bundles, coupons,
 * featured product. Each tool is a tile that leads to a dedicated
 * creation flow (Stage 4).
 */
@Composable
fun MarketplaceToolsScreen(
    onBack: () -> Unit,
    onCreateOffer: () -> Unit = {},
    onCreateDiscount: () -> Unit = {},
    onCreateFlashSale: () -> Unit = {},
    onCreateBundle: () -> Unit = {},
    onCreateCoupon: () -> Unit = {},
    onFeatureProduct: () -> Unit = {},
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
            Text("Marketplace Tools", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row {
                ToolTile(
                    icon = Icons.Filled.LocalOffer,
                    title = "Create Offer",
                    subtitle = "Set a discount on any product",
                    modifier = Modifier.weight(1f),
                    onClick = onCreateOffer,
                )
                Spacer(Modifier.width(8.dp))
                ToolTile(
                    icon = Icons.Filled.FlashOn,
                    title = "Flash Sale",
                    subtitle = "Limited-time price drop",
                    modifier = Modifier.weight(1f),
                    onClick = onCreateFlashSale,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                ToolTile(
                    icon = Icons.Filled.CardGiftcard,
                    title = "Discount",
                    subtitle = "% or fixed off",
                    modifier = Modifier.weight(1f),
                    onClick = onCreateDiscount,
                )
                Spacer(Modifier.width(8.dp))
                ToolTile(
                    icon = Icons.Filled.ViewInAr,
                    title = "Bundle",
                    subtitle = "Group products together",
                    modifier = Modifier.weight(1f),
                    onClick = onCreateBundle,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                ToolTile(
                    icon = Icons.Filled.ToggleOn,
                    title = "Coupon",
                    subtitle = "Buyer-entered codes",
                    modifier = Modifier.weight(1f),
                    onClick = onCreateCoupon,
                )
                Spacer(Modifier.width(8.dp))
                ToolTile(
                    icon = Icons.Filled.Star,
                    title = "Featured Product",
                    subtitle = "Top placement for buyers",
                    modifier = Modifier.weight(1f),
                    onClick = onFeatureProduct,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Active promotions", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            ActivePromoRow("Flash Sale: Samsung Galaxy A15", "Ends in 3 days", Color(0xFFFEF3C7), Color(0xFFB45309))
            ActivePromoRow("Discount: Power Bank 20%", "Ends in 5 days", Color(0xFFDBEAFE), Color(0xFF1E40AF))
            Spacer(Modifier.height(12.dp))
            Text("Scheduled", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            ActivePromoRow("Bundle: iPhone + AirPods", "Starts in 2 weeks", Color(0xFFEDE9FE), Color(0xFF6D28D9))
            Spacer(Modifier.height(12.dp))
            Text("Completed", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            ActivePromoRow("Coupon: WELCOME10", "Ended", Color(0xFFD1FAE5), Color(0xFF059669))
        }
    }
}

@Composable
private fun ToolTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ScottsTechXColors.BluePrimaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = ScottsTechXColors.OnLightSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ActivePromoRow(title: String, subtitle: String, bg: Color, fg: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(subtitle, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, color = ScottsTechXColors.OnLight, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}
