package com.scottsx.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.SellerSalesPoint
import com.scottsx.app.ui.components.AnimatedNumber
import com.scottsx.app.ui.components.ShimmerBox
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Sales-performance chart card + Seller AI card + Stock Watch rows +
 * the seller loading skeleton. Split out of SellerHomeScreen.kt.
 * Everything is driven by the real backend series / stats.
 */

private fun dayLabel(dateStr: String): String = try {
    LocalDate.parse(dateStr)
        .dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3)
} catch (_: Exception) {
    dateStr.takeLast(2)
}

/**
 * Sales performance — the backend's real 14-day series drawn in with
 * a staggered bar animation; the delta vs the previous 7 days is
 * computed from the same real series (no hardcoded percentages).
 */
@Composable
internal fun SalesPerformanceCard(
    series: List<SellerSalesPoint>,
    period: SalesPeriod,
    onPeriod: (SalesPeriod) -> Unit,
) {
    val visible = if (series.size > period.days) series.takeLast(period.days) else series
    val last7 = visible.takeLast(7).sumOf { it.revenue }
    val prior7 = series.dropLast(7).takeLast(7).sumOf { it.revenue }
    val delta = when {
        prior7 <= 0L -> RevenueDelta("No prior data to compare", true, true)
        else -> {
            val pct = ((last7 - prior7) * 100f) / prior7.toFloat()
            if (pct >= 0f) {
                RevenueDelta("+${"%.0f".format(pct)}% vs previous 7 days", true, false)
            } else {
                RevenueDelta("${"%.0f".format(pct)}% vs previous 7 days", false, false)
            }
        }
    }
    val best = visible.maxByOrNull { it.revenue }
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
                text = "Sales Performance",
                color = ScottsTechXColors.OnLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            SalesPeriodChip("7 days", period == SalesPeriod.ThisWeek, { onPeriod(SalesPeriod.ThisWeek) })
            Spacer(Modifier.width(6.dp))
            SalesPeriodChip("14 days", period == SalesPeriod.ThisMonth, { onPeriod(SalesPeriod.ThisMonth) })
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedNumber(
                target = visible.sumOf { it.revenue },
                format = ::formatUgx,
                modifier = Modifier.weight(1f),
                color = ScottsTechXColors.OnLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "·  ${visible.sumOf { it.orders }} orders",
                color = ScottsTechXColors.OnLightSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (delta.neutral) delta.label else (if (delta.positive) "▲ " else "▼ ") + delta.label,
                color = when {
                    delta.neutral -> ScottsTechXColors.OnLightSecondary
                    delta.positive -> Color(0xFF15803D)
                    else -> Color(0xFFDC2626)
                },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (best != null && best.revenue > 0L) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Best: ${dayLabel(best.date)} · ${formatUgx(best.revenue)}",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SalesChartCanvas(points = visible)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            visible.forEachIndexed { i, p ->
                val showLabel = i % 2 == (visible.size - 1) % 2 || i == 0
                Box(modifier = Modifier.weight(1f)) {
                    if (showLabel) {
                        Text(
                            text = dayLabel(p.date),
                            color = ScottsTechXColors.OnLightSecondary,
                            fontSize = 8.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesPeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else ScottsTechXColors.OnLightSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ScottsTechXColors.BluePrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * Canvas bar chart with a staggered draw-in: bars grow from the
 * baseline one after another whenever the data changes. The last bar
 * (today) is highlighted.
 */
@Composable
private fun SalesChartCanvas(points: List<SellerSalesPoint>) {
    if (points.isEmpty()) return
    val dataKey = remember(points) { points.hashCode() }
    var started by remember(dataKey) { mutableStateOf(false) }
    LaunchedEffect(dataKey) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "chart-draw",
    )
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        val n = points.size
        val maxVal = points.maxOf { it.revenue }.coerceAtLeast(1L)
        val baseline = size.height - 4f
        val usable = baseline - 14f
        val gap = size.width / n
        val barW = (gap * 0.52f).coerceAtMost(26f)
        for (i in points.indices) {
            // Staggered draw-in: bar i starts when the sweep reaches it.
            val p = ((progress * (n + 3).toFloat()) - i).coerceIn(0f, 1f)
            val value = points[i].revenue
            if (value <= 0L) continue
            val h = usable * (value.toFloat() / maxVal.toFloat()) * p
            val x = i * gap + (gap - barW) / 2f
            val isLast = i == n - 1
            val top = baseline - h
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isLast) {
                        listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryDark)
                    } else {
                        listOf(
                            ScottsTechXColors.BluePrimary.copy(alpha = 0.55f),
                            ScottsTechXColors.BluePrimary.copy(alpha = 0.25f),
                        )
                    },
                    startY = top,
                    endY = baseline,
                ),
                topLeft = Offset(x, top),
                size = Size(barW, h),
                cornerRadius = CornerRadius(5f, 5f),
            )
        }
        drawLine(
            color = Color(0xFFE5E7EB),
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 2f,
        )
    }
}

/**
 * Seller AI card — the copy is generated from the seller's REAL stats
 * (low-stock count, pending reviews, best seller); the CTA opens the
 * real Seller AI assistant screen.
 */
@Composable
internal fun SellerAiCard(
    lowStock: Int,
    pendingApproval: Int,
    topProduct: String?,
    onAsk: () -> Unit,
) {
    val body = buildString {
        if (lowStock > 0) {
            append(lowStock)
            append(if (lowStock > 1) " listings are running low on stock. " else " listing is running low on stock. ")
        }
        if (pendingApproval > 0) {
            append(pendingApproval)
            append(if (pendingApproval > 1) " are awaiting review. " else " is awaiting review. ")
        }
        if (topProduct != null) {
            append("Your best seller is ")
            append(topProduct.take(40))
            append(". ")
        }
        if (isBlank) {
            append("Ask me how to get your first order or which listings to fix next. ")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ScottsTechXColors.BluePrimaryDark,
                        ScottsTechXColors.BluePrimary,
                    ),
                ),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF93C5FD),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Seller AI",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 12.5.sp,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable { onAsk() }
                .padding(horizontal = 18.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Ask Seller AI",
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
            )
        }
    }
}

/**
 * Real low-stock / out-of-stock row with an inline +10 restock action
 * (real partial PATCH; optimistic with honest revert on failure).
 */
@Composable
internal fun LowStockRow(
    name: String,
    stock: Int,
    busy: Boolean,
    onRestock: () -> Unit,
    onOpen: () -> Unit,
) {
    val isOut = stock == 0
    val chipTint = if (isOut) Color(0xFFDC2626) else Color(0xFFB45309)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = ScottsTechXColors.OnLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpen() },
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(chipTint.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (isOut) "Out of stock" else "$stock left",
                    color = chipTint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (busy) Color(0xFFE5E7EB) else ScottsTechXColors.BluePrimary)
                .clickable(enabled = !busy) { onRestock() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    busy -> "Saving…"
                    isOut -> "Restock +10"
                    else -> "+10 stock"
                },
                color = if (busy) ScottsTechXColors.OnLightSecondary else Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun WellStockedCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF15803D).copy(alpha = 0.10f))
            .padding(16.dp),
    ) {
        Text(
            text = "All listings are well stocked",
            color = Color(0xFF15803D),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Nothing is below the 5-unit threshold right now.",
            color = ScottsTechXColors.OnLightSecondary,
            fontSize = 11.5.sp,
        )
    }
}

/**
 * Shimmer skeleton mirroring the real seller layout (white cards with
 * a light-grey sweep — matches the live state's surfaces).
 */
@Composable
internal fun SellerFeedSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            shape = RoundedCornerShape(22.dp),
            base = Color(0xFFF1F5F9),
            highlight = Color(0xFFE2E8F0),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    base = Color(0xFFF1F5F9),
                    highlight = Color(0xFFE2E8F0),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        repeat(2) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp),
                shape = RoundedCornerShape(18.dp),
                base = Color(0xFFF1F5F9),
                highlight = Color(0xFFE2E8F0),
            )
            Spacer(Modifier.height(10.dp))
        }
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(22.dp),
            base = Color(0xFFF1F5F9),
            highlight = Color(0xFFE2E8F0),
        )
    }
}
