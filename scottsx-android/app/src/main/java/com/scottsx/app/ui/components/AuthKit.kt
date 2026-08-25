package com.scottsx.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Shared visual language for the authentication screens (role selection,
 * login, forgot password), matching the reference design:
 *
 *   - an almost-black, faintly glowing "2035 marketplace" backdrop with
 *     technical ring/grid detail at very low opacity, and
 *   - a white bottom-sheet panel with heavily rounded top corners and a
 *     small drag indicator.
 *
 * The backdrop is drawn procedurally (no asset) so it scales to any screen
 * size and aspect ratio. It is deliberately static — one quiet surface
 * behind the content, nothing that moves.
 */
@Composable
fun FuturisticBackdrop(
    modifier: Modifier = Modifier,
    /** Adds a purple cast to the glows — used by the white-sheet screens. */
    purpleTint: Boolean = false,
) {
    val primary = if (purpleTint) Color(0xFF5B3DF5) else ScottsTechXColors.BluePrimary
    val ring = if (purpleTint) Color(0xFF8B7BFF) else ScottsTechXColors.BluePrimaryLight
    val baseTop = if (purpleTint) Color(0xFF070812) else Color(0xFF04060C)
    val baseBottom = if (purpleTint) Color(0xFF12081F) else Color(0xFF0A1024)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val d = density

        // Base vertical gradient — almost black at the top, a touch of
        // blue/purple depth at the bottom.
        drawRect(brush = Brush.verticalGradient(listOf(baseTop, Color(0xFF070B16), baseBottom)))

        // Large soft glow, top-right, where the "content horizon" sits.
        val glowCenter = Offset(w * 0.85f, h * 0.10f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(primary.copy(alpha = 0.30f), primary.copy(alpha = 0.10f), Color.Transparent),
                center = glowCenter,
                radius = w * 0.80f,
            ),
        )
        // Second, fainter glow bottom-left so the surface is not flat.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(ScottsTechXColors.CyanAccent.copy(alpha = 0.09f), Color.Transparent),
                center = Offset(w * 0.05f, h * 0.95f),
                radius = w * 0.65f,
            ),
        )

        // Concentric "radar" arcs around the glow — the technical detail.
        for (i in 1..3) {
            val r = i * 46f * d
            drawArc(
                color = ring.copy(alpha = 0.14f - i * 0.03f),
                startAngle = 105f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(glowCenter.x - r, glowCenter.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 1.5f * d),
            )
        }
        // A few fixed "nodes" on the arcs — cheap, deterministic sparkle.
        drawCircle(color = ring.copy(alpha = 0.35f), center = Offset(w * 0.62f, h * 0.20f), radius = 2.5f * d)
        drawCircle(color = ring.copy(alpha = 0.22f), center = Offset(w * 0.93f, h * 0.34f), radius = 2f * d)
        drawCircle(color = ScottsTechXColors.CyanAccent.copy(alpha = 0.25f), center = Offset(w * 0.12f, h * 0.78f), radius = 2f * d)

        // Hairline grid — barely there, just enough to read as "technical".
        val grid = Color.White.copy(alpha = 0.025f)
        val spacing = 24f * d
        var x = w * 0.5f
        while (x < w) {
            drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
            x += spacing
        }
        var y = 0f
        while (y < h) {
            drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
            y += spacing
        }
    }
}

/**
 * The white authentication panel over the dark backdrop: a short dark band
 * carrying the back arrow, then the sheet itself with a drag indicator and
 * a scrollable, keyboard-aware content column.
 *
 * Callers are expected to have already padded for the system bars (the
 * screen root applies safeDrawing), so the back arrow sits just below the
 * status bar on every device.
 */
@Composable
fun AuthSheet(
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(start = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    spotColor = Color.Black.copy(alpha = 0.25f),
                    ambientColor = Color.Transparent,
                )
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White),
        ) {
            // Drag indicator — the sheet reads as a sheet.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFC9D1E0)),
                )
            }
            // Scrollable + IME-aware: with the keyboard open the focused
            // field and the buttons below it scroll into view instead of
            // being covered.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            ) {
                content()
            }
        }
    }
}

/**
 * Filled, pill-corners text field for the white auth panels: bold dark
 * label, light-gray rounded container, large touch target, optional eye
 * toggle for passwords.
 */
@Composable
fun AuthFilledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = ScottsTechXColors.OnLight,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Box(modifier = Modifier.height(58.dp)) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxSize(),
                singleLine = true,
                placeholder = {
                    Text(placeholder, color = Color(0xFF8A94A8), fontSize = 15.sp)
                },
                visualTransformation =
                    if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                ),
                trailingIcon = {
                    if (isPassword) {
                        IconButton(
                            onClick = { visible = !visible },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password",
                                tint = Color(0xFF5A6478),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                // Only parameters proven to exist in material3 1.2.1 (the
                // BOM's version): the same set the messaging screens use.
                // The cursor defaults to the theme primary (BluePrimary),
                // and the placeholder's grey comes from the explicit Text
                // colour above, so nothing visible is lost by omitting the
                // rest — disabled/error states are never used on this form.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ScottsTechXColors.PanelInputLight,
                    unfocusedContainerColor = ScottsTechXColors.PanelInputLight,
                    focusedTextColor = ScottsTechXColors.OnLight,
                    unfocusedTextColor = ScottsTechXColors.OnLight,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(25.dp),
            )
        }
    }
}

/**
 * The Google "G", drawn in its four brand colours — no asset, no extra
 * dependency, crisp at any size.
 */
@Composable
fun GoogleG(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width.coerceAtMost(size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = s * 0.42f
        val sw = s * 0.145f
        val topLeft = Offset(cx - r, cy - r)
        val arcSize = Size(r * 2f, r * 2f)
        val blue = Color(0xFF4285F4)
        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)
        // Screen angles: 0° at 3 o'clock, positive = clockwise.
        drawArc(color = blue, startAngle = 270f, sweepAngle = 90f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(sw))
        drawArc(color = red, startAngle = 180f, sweepAngle = 90f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(sw))
        drawArc(color = yellow, startAngle = 90f, sweepAngle = 90f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(sw))
        drawArc(color = green, startAngle = 0f, sweepAngle = 90f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(sw))
        // The horizontal bar of the G, in blue.
        drawRect(color = blue, topLeft = Offset(cx, cy - sw / 2f), size = Size(r + sw / 2f, sw))
    }
}

/**
 * Apple's mark, drawn as a single silhouette (body + leaf, with the bite
 * punched out in the button's background colour) so it needs no asset.
 */
@Composable
fun AppleLogo(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF101828),
    /** Colour used to carve the bite out — the button behind it. */
    biteColor: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val s = size.width.coerceAtMost(size.height)
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        // Design space is 100x100; content spans ~3..79, so shift +9 to centre.
        fun px(v: Float) = ox + v / 100f * s
        fun py(v: Float) = oy + (v + 9f) / 100f * s

        val body = Path().apply {
            moveTo(px(50f), py(27f))
            cubicTo(px(46f), py(21f), px(38f), py(18.5f), px(31f), py(19.5f))
            cubicTo(px(20f), py(21.5f), px(13f), py(31f), px(13f), py(43f))
            cubicTo(px(13f), py(57f), px(20f), py(69f), px(28.5f), py(75f))
            cubicTo(px(33.5f), py(78.5f), px(38.5f), py(78.5f), px(41.5f), py(76.5f))
            cubicTo(px(43.5f), py(75.2f), px(45.5f), py(75.2f), px(47f), py(76f))
            cubicTo(px(49f), py(77f), px(51f), py(77f), px(53f), py(76f))
            cubicTo(px(54.5f), py(75.2f), px(56.5f), py(75.2f), px(58.5f), py(76.5f))
            cubicTo(px(61.5f), py(78.5f), px(66.5f), py(78.5f), px(71.5f), py(75f))
            cubicTo(px(80f), py(69f), px(87f), py(57f), px(87f), py(43f))
            cubicTo(px(87f), py(31f), px(80f), py(21.5f), px(69f), py(19.5f))
            cubicTo(px(62f), py(18.5f), px(54f), py(21f), px(50f), py(27f))
            close()
        }
        drawPath(body, color)

        val leaf = Path().apply {
            moveTo(px(52f), py(16f))
            quadraticBezierTo(px(53f), py(6f), px(66f), py(3.5f))
            quadraticBezierTo(px(63f), py(13f), px(52f), py(16f))
            close()
        }
        drawPath(leaf, color)

        // The bite — a circle of the button background on the right flank.
        drawCircle(color = biteColor, center = Offset(px(90f), py(40f)), radius = px(13.5f))
    }
}

/** Tiny helper so both auth screens share the exact gradient pill button. */
@Composable
fun AuthGradientButton(
    text: String,
    onClick: () -> Unit,
    loading: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.horizontalGradient(
        listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(gradient, RoundedCornerShape(29.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF0B1526),
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(
                text = text,
                color = if (enabled) Color(0xFF0B1526) else Color(0xFF0B1526).copy(alpha = 0.4f),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
