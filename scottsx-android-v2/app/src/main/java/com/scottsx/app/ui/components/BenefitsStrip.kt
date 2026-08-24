package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Benefit
import com.scottsx.app.data.domain.BenefitIcon
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Four-card benefit strip — Free Delivery, Secure Payments,
 * Easy Returns, Buyer Protection. Each card is a compact rounded
 * tile with a colored icon and a 2-line stacked label.
 */
@Composable
fun BenefitsStrip(
    benefits: List<Benefit>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        benefits.forEach { benefit ->
            BenefitCard(benefit)
        }
    }
}

@Composable
private fun BenefitCard(benefit: Benefit) {
    Box(
        modifier = Modifier
            .width(108.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFF8FAFC)),
                ),
            )
            .padding(12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
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
                    imageVector = iconFor(benefit.iconKind),
                    contentDescription = benefit.title,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = benefit.title,
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = benefit.subtitle,
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 9.sp,
                textAlign = TextAlign.Start,
                lineHeight = 12.sp,
            )
        }
    }
}

private fun iconFor(kind: BenefitIcon): ImageVector = when (kind) {
    BenefitIcon.Delivery -> Icons.Filled.LocalShipping
    BenefitIcon.Security -> Icons.Filled.Shield
    BenefitIcon.Returns -> Icons.Filled.Refresh
    BenefitIcon.Protection -> Icons.Filled.VerifiedUser
}