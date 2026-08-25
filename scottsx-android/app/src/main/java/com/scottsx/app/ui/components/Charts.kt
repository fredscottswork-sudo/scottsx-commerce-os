package com.scottsx.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlin.math.roundToLong

/**
 * ScottsTechX chart kit — dependency-free Compose Canvas charts.
 *
 * The Compose mirror of `web/src/components/charts.tsx` (AreaChart, BarChart,
 * Donut, Sparkline) so the app's dashboards look and read exactly like the
 * web dashboards: same blue palette, same data, same shapes.
 */

/** Animated area/line chart for time series (revenue, orders, signups). */
@Composable
fun AreaChart(
    points: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    color: Color = ScottsTechXColors.BluePrimary,
    labels: List<String> = emptyList(),
) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(points) { animate = true }
    val progress by animateFloatAsState(
        targetValue = if (animate) 1f else 0f,
        animationSpec = tween(700),
        label = "chart",
    )

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            if (points.size < 2) return@Canvas
            val max = (points.max()).coerceAtLeast(1L).toFloat()
            val min = 0f
            val w = size.width
            val h = size.height
            val stepX = w / (points.size - 1)

            // Horizontal grid lines (4 bands, dashed — same as the web chart).
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            for (i in 1..3) {
                val y = h * i / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f,
                    pathEffect = dash,
                )
            }

            fun yFor(v: Long): Float {
                val norm = (v - min) / (max - min)
                // 6% headroom at the top, baseline at the bottom.
                return h - (norm * progress) * (h * 0.88f) - h * 0.04f
            }

            // Line path.
            val line = Path()
            points.forEachIndexed { i, v ->
                val x = i * stepX
                val y = yFor(v)
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }

            // Gradient fill under the line.
            val fill = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = h,
                ),
            )
            drawPath(
                path = line,
                color = color,
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )

            // End dot on the latest value.
            val lastX = (points.size - 1) * stepX
            val lastY = yFor(points.last())
            drawCircle(color = color, radius = 8f, center = Offset(lastX, lastY))
            drawCircle(color = Color.White, radius = 4f, center = Offset(lastX, lastY))
        }

        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // First, middle and last label — enough to read the range
                // without crowding a phone-width chart.
                val shown = listOf(
                    labels.first(),
                    labels[labels.size / 2],
                    labels.last(),
                )
                shown.forEach { Text(it, fontSize = 10.sp, color = labelColor) }
            }
        }
    }
}

/** Horizontal bar list — "top products" style ranking with animated bars. */
@Composable
fun HBarList(
    items: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    color: Color = ScottsTechXColors.BluePrimary,
    valueSuffix: String = " sold",
) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(items) { animate = true }
    val progress by animateFloatAsState(
        targetValue = if (animate) 1f else 0f,
        animationSpec = tween(600),
        label = "bars",
    )
    val max = items.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                    )
                    Text(
                        "$value$valueSuffix",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(99.dp),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (value.toFloat() / max) * progress)
                            .height(8.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(color, ScottsTechXColors.CyanAccent),
                                ),
                                RoundedCornerShape(99.dp),
                            ),
                    )
                }
            }
        }
    }
}

data class DonutSegment(val label: String, val value: Int, val color: Color)

/** Donut chart with a centre label + legend, matching the web Donut. */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    centerLabel: String = "",
    centerSub: String = "",
) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(segments) { animate = true }
    val progress by animateFloatAsState(
        targetValue = if (animate) 1f else 0f,
        animationSpec = tween(700),
        label = "donut",
    )
    val total = segments.sumOf { it.value }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = this.size.minDimension * 0.14f, cap = StrokeCap.Butt)
                val inset = stroke.width / 2f
                val arcSize = androidx.compose.ui.geometry.Size(
                    this.size.width - stroke.width,
                    this.size.height - stroke.width,
                )
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                var start = -90f
                segments.forEach { s ->
                    val sweep = (s.value.toFloat() / total) * 360f * progress
                    drawArc(
                        color = s.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (centerLabel.isNotBlank()) {
                    Text(centerLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (centerSub.isNotBlank()) {
                    Text(centerSub, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            segments.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(s.color, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${s.label} · ${s.value}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Count-up text for KPI values — the Compose version of the web's CountUp. */
@Composable
fun CountUpText(
    target: Long,
    modifier: Modifier = Modifier,
    formatter: (Long) -> String = { it.toString() },
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    color: Color = Color.Unspecified,
) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(target) { animate = true }
    val progress by animateFloatAsState(
        targetValue = if (animate) 1f else 0f,
        animationSpec = tween(650),
        label = "countup",
    )
    Text(
        formatter((target * progress.toDouble()).roundToLong()),
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

/** KPI stat card — icon, animated value, label and optional delta chip. */
@Composable
fun StatCard(
    icon: ImageVector,
    label: String,
    value: Long,
    modifier: Modifier = Modifier,
    accent: Color = ScottsTechXColors.BluePrimary,
    formatter: (Long) -> String = { it.toString() },
    hint: String = "",
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.weight(1f))
                if (hint.isNotBlank()) {
                    Surface(
                        color = ScottsTechXColors.SuccessGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(99.dp),
                    ) {
                        Text(
                            hint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ScottsTechXColors.SuccessGreen,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            CountUpText(target = value, formatter = formatter, fontSize = 20.sp)
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
