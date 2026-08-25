package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.SellerApiOrder
import com.scottsx.app.data.domain.SellerStats
import com.scottsx.app.ui.components.AnimatedNumber
import com.scottsx.app.ui.components.PulsingDot
import com.scottsx.app.ui.components.ShimmerBox
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx

/**
 * Seller dashboard section cards — all data-driven from the live
 * backend responses. Split out of SellerHomeScreen.kt.
 */

/** Chart period — the backend delivers exactly 14 days of sales data. */
internal enum class SalesPeriod(val label: String, val days: Int) {
    ThisWeek("7 days", 7),
    ThisMonth("14 days", 14),
}

/** Real revenue delta: last [window] days vs the window before it. */
internal data class RevenueDelta(val label: String, val positive: Boolean, val neutral: Boolean)

/**
 * Onboarding hero — only rendered when the backend confirms the store
 * has zero products AND zero orders. Every CTA opens a real screen.
 */
@Composable
internal fun OnboardingCard(onAddProduct: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ScottsTechXColors.BluePrimary,
                        ScottsTechXColors.BluePrimaryDark,
                    ),
                ),
            )
            .padding(20.dp),
    ) {
        Text(
            text = "Set up your store",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "1 · Add a product with a real photo\n2 · Submit it for review\n3 · Go live to every buyer in the app",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.5.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable { onAddProduct() }
                .padding(horizontal = 20.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Add your first product",
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * Real 30-day overview — every number comes from the backend's
 * `dashboard/stats` (Postgres aggregates). Counters animate up.
 */
@Composable
internal fun SellerOverviewCard(stats: SellerStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Overview",
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            PulsingDot(color = Color(0xFF15803D))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Live",
                color = Color(0xFF15803D),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewStatCard(
                label = "Revenue · 30d",
                value = {
                    AnimatedNumber(
                        target = stats.revenue30Ugx,
                        format = ::formatUgx,
                        color = ScottsTechXColors.OnLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                sub = "Lifetime ${formatUgx(stats.revenueUgx)}",
                tint = Color(0xFFEA580C),
                modifier = Modifier.weight(1f),
            )
            OverviewStatCard(
                label = "Orders · 30d",
                value = {
                    AnimatedNumber(
                        target = stats.orders30.toLong(),
                        format = { it.toString() },
                        color = ScottsTechXColors.OnLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                sub = "Lifetime ${stats.orders}",
                tint = ScottsTechXColors.BluePrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewStatCard(
                label = "Avg order",
                value = {
                    AnimatedNumber(
                        target = stats.avgOrderValueUgx,
                        format = ::formatUgx,
                        color = ScottsTechXColors.OnLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                sub = if (stats.topProduct != null) "Top: ${stats.topProduct}" else "No sales yet",
                tint = Color(0xFF059669),
                modifier = Modifier.weight(1f),
            )
            OverviewStatCard(
                label = "Product views",
                value = {
                    AnimatedNumber(
                        target = stats.totalViews.toLong(),
                        format = { it.toString() },
                        color = ScottsTechXColors.OnLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                sub = "${stats.followers} followers",
                tint = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
            )
        }
        // Stock health strip — real counts from the same stats block.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ScottsTechXColors.PanelInputLight)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${stats.approved} live · ${stats.pending} in review · ${stats.lowStock} low stock",
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            if (stats.outOfStock > 0) {
                Text(
                    text = "$stats.outOfStock out of stock",
                    color = Color(0xFFDC2626),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun OverviewStatCard(
    label: String,
    value: @Composable () -> Unit,
    sub: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ScottsTechXColors.PanelInputLight)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = ScottsTechXColors.OnLightSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box {
            value()
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub,
            color = ScottsTechXColors.OnLightSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Real order-status counters computed from the live order list. */
@Composable
internal fun OrdersOverviewRow(
    orders: List<SellerApiOrder>,
    onSeeAll: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OrdersOverviewChip("Pending", orders.count { it.status == "pending" }, Color(0xFFB45309), Modifier.weight(1f))
            OrdersOverviewChip("Paid", orders.count { it.status == "paid" }, ScottsTechXColors.BluePrimary, Modifier.weight(1f))
            OrdersOverviewChip("Shipped", orders.count { it.status == "shipped" }, Color(0xFF7C3AED), Modifier.weight(1f))
            OrdersOverviewChip("Delivered", orders.count { it.status == "delivered" }, Color(0xFF15803D), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onSeeAll() },
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "View all orders →",
                color = ScottsTechXColors.BluePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun OrdersOverviewChip(
    label: String,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = tint,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = count.toString(),
            color = ScottsTechXColors.OnLight,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
        )
    }
}

/** One real order. The row and its button both open the orders screen. */
@Composable
internal fun OrderRow(
    title: String,
    buyerName: String,
    quantity: Int,
    amount: Long,
    statusLabel: String,
    placedAt: String,
    onView: () -> Unit,
) {
    val (statusTint, statusBg) = when (statusLabel) {
        "Pending" -> Color(0xFFB45309) to Color(0xFFF97316).copy(alpha = 0.12f)
        "Paid" -> ScottsTechXColors.BluePrimary to ScottsTechXColors.BluePrimary.copy(alpha = 0.10f)
        "Shipped" -> Color(0xFF7C3AED) to Color(0xFF7C3AED).copy(alpha = 0.10f)
        "Delivered" -> Color(0xFF15803D) to Color(0xFF15803D).copy(alpha = 0.10f)
        else -> Color(0xFF6B7280) to Color(0xFF6B7280).copy(alpha = 0.10f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable { onView() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = buyerName.firstOrNull()?.uppercase() ?: "?",
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ScottsTechXColors.OnLight,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "×$quantity · $buyerName · $placedAt",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                // Backend `amount` is the unit price; the line shows the
                // real order total (unit price × quantity).
                Text(
                    text = formatUgx(amount * quantity),
                    color = ScottsTechXColors.OnLight,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusBg),
                ) {
                    Text(
                        text = statusLabel,
                        color = statusTint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.08f))
                .clickable { onView() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "View Order",
                color = ScottsTechXColors.BluePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** All five quick actions navigate to real screens. */
@Composable
internal fun QuickActionsRow(
    onAddProduct: () -> Unit,
    onManageOrders: () -> Unit,
    onPromotions: () -> Unit,
    onAnalytics: () -> Unit,
    onMessages: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickAction(Icons.Filled.AddCircle, "Add Product", Color(0xFF059669), onAddProduct)
        QuickAction(Icons.Filled.ReceiptLong, "Orders", ScottsTechXColors.BluePrimary, onManageOrders)
        QuickAction(Icons.Filled.Campaign, "Promotions", Color(0xFF7C3AED), onPromotions)
        QuickAction(Icons.Filled.Analytics, "Analytics", Color(0xFFEA580C), onAnalytics)
        QuickAction(Icons.Filled.ChatBubble, "Messages", Color(0xFF0EA5E9), onMessages)
    }
}

@Composable
internal fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = ScottsTechXColors.OnLight,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
