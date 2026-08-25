package com.scottsx.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Uganda map background — draws the actual country outline (from
 * world.geo.json) on top of the ScottsTechX brand blue. Adds animated
 * city nodes for 10 major Uganda cities and dynamic connecting lines
 * that pulse between them.
 *
 * Used as the Slide 1 background ("Build the future. The future of
 * Uganda.").
 */
@Composable
fun UgandaMapBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "uganda-map")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "t",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val rotation by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rotation",
    )

    // Starfield particle positions (deterministic for stability).
    val stars = remember {
        List(48) {
            val x = (it * 41 % 100) / 100f
            val y = (it * 73 % 100) / 100f
            val r = 0.3f + (it % 7) * 0.15f
            Triple(x, y, r)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBackgroundGradient()
            drawStars(stars, t)
            drawCountryFill()
            drawCountryBorder()
            drawCountryGlow(t)
            drawGrid(t)
            drawCityConnections(t)
            drawCities(pulse)
            drawKampalaHighlight(t)
            drawCornerLabels()
        }
    }
}

private fun DrawScope.drawBackgroundGradient() {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF020617),
                Color(0xFF0B1120),
                Color(0xFF0B1120),
                Color(0xFF050717),
            ),
        ),
        size = size,
    )
    // Vignette darken at edges
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xCC000000),
            ),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.8f,
        ),
        size = size,
    )
}

private fun DrawScope.drawStars(stars: List<Triple<Float, Float, Float>>, t: Float) {
    stars.forEach { (xN, yN, r) ->
        val twinkle = (sin((t * 6.28f + xN * 30f)) * 0.5f + 0.5f).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = 0.15f + 0.25f * twinkle),
            radius = r,
            center = Offset(xN * size.width, yN * size.height),
        )
    }
}

private fun DrawScope.drawCountryFill() {
    val (w, h) = size
    // Map is rendered at 85% width, centered horizontally, vertically centred.
    val mapW = w * 0.78f
    val mapH = h * 0.62f
    val offsetX = (w - mapW) / 2f
    val offsetY = (h - mapH) / 2f
    val path = buildUgandaPath(mapW, mapH, offsetX, offsetY)

    // Inner gradient fill
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0x551E40AF),
                Color(0x773B82F6),
                Color(0x551E40AF),
            ),
            start = Offset(offsetX, offsetY),
            end = Offset(offsetX + mapW, offsetY + mapH),
        ),
    )
}

private fun DrawScope.drawCountryBorder() {
    val (w, h) = size
    val mapW = w * 0.78f
    val mapH = h * 0.62f
    val offsetX = (w - mapW) / 2f
    val offsetY = (h - mapH) / 2f
    val path = buildUgandaPath(mapW, mapH, offsetX, offsetY)
    drawPath(
        path = path,
        color = ScottsTechXColors.BluePrimaryLight,
        style = Stroke(width = 2.4f),
    )
}

private fun DrawScope.drawCountryGlow(t: Float) {
    val (w, h) = size
    val mapW = w * 0.78f
    val mapH = h * 0.62f
    val offsetX = (w - mapW) / 2f
    val offsetY = (h - mapH) / 2f
    val path = buildUgandaPath(mapW, mapH, offsetX, offsetY)
    val glowAlpha = (0.30f + 0.30f * sin(t * 6.28f)).coerceIn(0f, 1f)
    drawPath(
        path = path,
        color = ScottsTechXColors.BluePrimaryLight.copy(alpha = glowAlpha),
        style = Stroke(width = 6f),
    )
}

private fun DrawScope.drawGrid(t: Float) {
    val (w, h) = size
    val step = w / 16f
    val offset = (t * step) % step
    val gridColor = ScottsTechXColors.BluePrimaryLight.copy(alpha = 0.07f)
    var x = -step + offset
    while (x < w + step) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 0.6f,
        )
        x += step
    }
    val rowStep = h / 22f
    var y = -rowStep + (t * rowStep) % rowStep
    while (y < h + rowStep) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 0.6f,
        )
        y += rowStep
    }
}

private fun DrawScope.drawCityConnections(t: Float) {
    val (w, h) = size
    val mapW = w * 0.78f
    val mapH = h * 0.62f
    val offsetX = (w - mapW) / 2f
    val offsetY = (h - mapH) / 2f

    // Build pair list — every city connects to every other city.
    val keys = UgandaCities.keys.toList()
    for (i in keys.indices) {
        for (j in i + 1 until keys.size) {
            val a = UgandaCities[keys[i]]!!
            val b = UgandaCities[keys[j]]!!
            val ax = offsetX + a.x * mapW
            val ay = offsetY + a.y * mapH
            val bx = offsetX + b.x * mapW
            val by = offsetY + b.y * mapH

            val dist = kotlin.math.hypot(bx - ax, by - ay)
            if (dist > mapW * 0.55f) continue  // skip far connections

            // Animated dashed travelling pulse along the line.
            val phase = (t + i * 0.13f + j * 0.27f) % 1f
            val cycleLen = dist
            val dashOn = 6f
            val gapOn = 22f
            val phasePx = phase * (dashOn + gapOn)
            drawLine(
                color = ScottsTechXColors.BluePrimaryLight.copy(alpha = 0.45f),
                start = Offset(ax, ay),
                end = Offset(bx, by),
                strokeWidth = 1.2f,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(dashOn, gapOn),
                    phase = -phasePx,
                ),
            )
        }
    }
}

private fun DrawScope.drawCities(pulse: Float) {
    val (w, h) = size
    val mapW = w * 0.78f
    val mapH = h * 0.62f
    val offsetX = (w - mapW) / 2f
    val offsetY = (h - mapH) / 2f
    UgandaCities.forEach { (name, pos) ->
        val cx = offsetX + pos.x * mapW
        val cy = offsetY + pos.y * mapH
        // Outer halo
        drawCircle(
            color = ScottsTechXColors.BlueGlow,
            radius = 14f * pulse,
            center = Offset(cx, cy),
        )
        // Solid inner dot
        drawCircle(
            color = ScottsTechXColors.OnDark,
            radius = 3.4f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = ScottsTechXColors.BluePrimaryLight,
            radius = 2.4f,
            center = Offset(cx, cy),
        )
    }
}

private fun DrawScope.drawKampalaHighlight(t: Float) {
    val (w, h) = size
    val mapW = w * 0.78f
    val mapH = h * 0.62f
    val offsetX = (w - mapW) / 2f
    val offsetY = (h - mapH) / 2f
    val pos = UgandaCities["Kampala"]!!
    val cx = offsetX + pos.x * mapW
    val cy = offsetY + pos.y * mapH
    val r1 = 22f + 30f * (t % 1f)
    val r2 = 18f + 50f * ((t + 0.5f) % 1f)
    drawCircle(
        color = ScottsTechXColors.BluePrimaryLight.copy(alpha = (1f - (t % 1f)) * 0.45f),
        radius = r1,
        center = Offset(cx, cy),
        style = Stroke(width = 1.6f),
    )
    drawCircle(
        color = ScottsTechXColors.OnDark.copy(alpha = (1f - ((t + 0.5f) % 1f)) * 0.30f),
        radius = r2,
        center = Offset(cx, cy),
        style = Stroke(width = 1.0f),
    )
}

private fun DrawScope.drawCornerLabels() {
    val pad = 14f
    val cornerColor = ScottsTechXColors.BluePrimaryLight.copy(alpha = 0.6f)
    val len = 18f
    // top-left
    drawLine(cornerColor, Offset(pad, pad), Offset(pad + len, pad), strokeWidth = 1.5f)
    drawLine(cornerColor, Offset(pad, pad), Offset(pad, pad + len), strokeWidth = 1.5f)
    // top-right
    drawLine(cornerColor, Offset(size.width - pad, pad), Offset(size.width - pad - len, pad), strokeWidth = 1.5f)
    drawLine(cornerColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + len), strokeWidth = 1.5f)
    // bottom-left
    drawLine(cornerColor, Offset(pad, size.height - pad), Offset(pad + len, size.height - pad), strokeWidth = 1.5f)
    drawLine(cornerColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - len), strokeWidth = 1.5f)
    // bottom-right
    drawLine(cornerColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - len, size.height - pad), strokeWidth = 1.5f)
    drawLine(cornerColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - len), strokeWidth = 1.5f)
}

private fun buildUgandaPath(mapW: Float, mapH: Float, offsetX: Float, offsetY: Float): Path {
    return Path().apply {
        UgandaCountryOutline.forEachIndexed { i, p ->
            val x = offsetX + p.x * mapW
            val y = offsetY + p.y * mapH
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/* ---------------- Country outline + city data ---------------- */

internal val UgandaCountryOutline: List<Offset> = listOf(
    Offset(0.4191f, 0.9269f),
    Offset(0.2182f, 0.9247f),
    Offset(0.1539f, 0.9458f),
    Offset(0.0444f, 1.0000f),
    Offset(0.0000f, 0.9821f),
    Offset(0.0015f, 0.8497f),
    Offset(0.0440f, 0.7825f),
    Offset(0.0543f, 0.6416f),
    Offset(0.0929f, 0.5599f),
    Offset(0.1629f, 0.4683f),
    Offset(0.2333f, 0.4216f),
    Offset(0.2923f, 0.3593f),
    Offset(0.2188f, 0.3355f),
    Offset(0.2299f, 0.1301f),
    Offset(0.3053f, 0.0822f),
    Offset(0.4219f, 0.1215f),
    Offset(0.5694f, 0.0804f),
    Offset(0.6983f, 0.0808f),
    Offset(0.8111f, 0.0000f),
    Offset(0.8979f, 0.1219f),
    Offset(0.9194f, 0.2101f),
    Offset(1.0000f, 0.4117f),
    Offset(0.9333f, 0.5398f),
    Offset(0.8431f, 0.6560f),
    Offset(0.7906f, 0.7272f),
    Offset(0.7925f, 0.9133f),
    Offset(0.4191f, 0.9269f),
)

internal val UgandaCities: Map<String, Offset> = mapOf(
    "Kampala" to Offset(0.5504f, 0.6854f),
    "Entebbe" to Offset(0.5311f, 0.7375f),
    "Jinja" to Offset(0.6640f, 0.6679f),
    "Gulu" to Offset(0.4984f, 0.2591f),
    "Mbale" to Offset(0.8423f, 0.5564f),
    "Mbarara" to Offset(0.1970f, 0.8531f),
    "FortPortal" to Offset(0.1277f, 0.6303f),
    "Lira" to Offset(0.6085f, 0.3513f),
    "Arua" to Offset(0.2441f, 0.2159f),
    "Soroti" to Offset(0.7389f, 0.4453f),
)