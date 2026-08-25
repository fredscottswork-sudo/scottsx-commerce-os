package com.scottsx.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.SellerDataSource
import com.scottsx.app.data.domain.Product
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx

/**
 * Seller analytics. Aggregates sales + orders over 7/30/90 day
 * windows. The data is derived from the same per-product metadata
 * the rest of the marketplace uses (no standalone mock).
 */
@Composable
fun SellerAnalyticsScreen(
    onBack: () -> Unit,
) {
    var period by remember { mutableStateOf(0) } // 0=7d, 1=30d, 2=90d
    val snap = remember { SellerDataSource.snapshot() }
    val days = listOf(7, 30, 90)
    val multipliers = listOf(1f, 4.2f, 12.0f)
    val periodLabel = days[period]!!

    // Synthetic but deterministic: take the dashboard's per-day sales
    // and multiply by the period multiplier, then plot.
    val points = remember(period) {
        val base = snap.sales
        val mul = multipliers[period]!!
        base.map { (it.label to (it.amountUgx * mul).toLong()) }
    }
    val totalRevenue = points.sumOf { it.second }
    val totalOrders = (snap.ordersOverview.completed * multipliers[period]!!).toInt()
    val aov = if (totalOrders == 0) 0L else totalRevenue / totalOrders

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
            Text("Analytics", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PeriodRow(period = period, onChange = { period = it })
            RevenueCard(totalRevenue, totalOrders, aov)
            ChartCard(points = points)
            BestProducts(snap)
        }
    }
}

@Composable
private fun PeriodRow(period: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("7 Days", "30 Days", "90 Days").forEachIndexed { i, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (period == i) ScottsTechXColors.BluePrimary else Color.White,
                    )
                    .clickable { onChange(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (period == i) Color.White else ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun RevenueCard(revenue: Long, orders: Int, aov: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(ScottsTechXColors.BluePrimary, Color(0xFF6366F1)),
                ),
            )
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Revenue", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text("UGX ${formatUgx(revenue)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Row {
                MetaCell("Orders", orders.toString())
                Spacer(Modifier.width(16.dp))
                MetaCell("Avg Order", "UGX ${formatUgx(aov)}")
            }
        }
    }
}

@Composable
private fun MetaCell(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ChartCard(points: List<Pair<String, Long>>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        if (points.isEmpty()) {
            Text("No sales yet", color = ScottsTechXColors.OnLightSecondary, fontSize = 13.sp)
            return
        }
        Column {
            Text("Sales trend", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxV = points.maxOf { it.second }.toFloat().coerceAtLeast(1f)
                    val w = size.width
                    val h = size.height
                    val stepX = w / (points.size - 1).coerceAtLeast(1)
                    val pointsList = points.mapIndexed { idx, p ->
                        Offset(idx * stepX, h - (p.second.toFloat() / maxV) * h * 0.85f)
                    }
                    // Grid
                    drawLine(
                        color = Color(0xFFE5E7EB),
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = 1f,
                    )
                    // Fill below
                    val fillPath = Path().apply {
                        moveTo(0f, h)
                        pointsList.forEach { lineTo(it.x, it.y) }
                        lineTo(w, h)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(ScottsTechXColors.BluePrimary.copy(alpha = 0.35f), Color.Transparent),
                        ),
                    )
                    // Line
                    val linePath = Path().apply {
                        moveTo(0f, h)
                        pointsList.forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = linePath,
                        color = ScottsTechXColors.BluePrimary,
                        style = Stroke(width = 3f),
                    )
                    // Dot at last point
                    val last = pointsList.last()
                    drawCircle(ScottsTechXColors.BluePrimary, radius = 5f, center = last)
                    drawCircle(Color.White, radius = 2.5f, center = last)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                points.forEach { (label, _) ->
                    Text(label, color = ScottsTechXColors.OnLightSecondary, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun BestProducts(snap: com.scottsx.app.data.domain.SellerDashboardSnapshot) {
    val best = remember { MarketplaceDataSource.topSelling(5) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Column {
            Text("Best products", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            best.forEachIndexed { idx, p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ScottsTechXColors.BluePrimary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${idx + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = p.name,
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text("UGX ${formatUgx(p.priceUgx)}", color = ScottsTechXColors.BluePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
