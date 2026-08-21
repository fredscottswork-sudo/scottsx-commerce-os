package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.Product
import com.scottsx.app.ui.theme.ScottsTechXColors
import java.text.DecimalFormat

/** "UGX 1,650,000" */
fun formatUgx(amount: Long): String {
    val formatted = DecimalFormat("#,###").format(amount)
    return "UGX $formatted"
}

/**
 * Compact money for dashboard tiles: 45,000,000 -> "45.0M", 320,500 -> "320.5K".
 *
 * A seller's revenue is a full UGX figure. Printed in full it is up to 13
 * characters, and four of those side by side overflow a 360dp phone — which is
 * exactly what the stats row used to do.
 */
fun formatUgxCompact(amount: Long): String {
    val a = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        a >= 1_000_000_000L -> sign + DecimalFormat("#,##0.0").format(a / 1_000_000_000.0) + "B"
        a >= 1_000_000L -> sign + DecimalFormat("#,##0.0").format(a / 1_000_000.0) + "M"
        a >= 10_000L -> sign + DecimalFormat("#,##0.0").format(a / 1_000.0) + "K"
        else -> sign + DecimalFormat("#,###").format(a)
    }
}

/** Full price with strikethrough old price, e.g. for detail screens. */
@Composable
fun PriceTag(product: Product, large: Boolean = false) {
    Column {
        Text(
            text = formatUgx(product.priceMinor),
            fontSize = if (large) 24.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = ScottsTechXColors.BluePrimary,
        )
        if (product.oldPriceMinor != null && product.oldPriceMinor > product.priceMinor) {
            Text(
                text = formatUgx(product.oldPriceMinor),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    }
}

@Composable
fun RatingRow(rating: Double, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = ScottsTechXColors.WarningAmber, modifier = Modifier.size(16.dp))
        Text("$rating", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text("($count)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val color = when (status.lowercase()) {
        "paid", "delivered", "approved", "answered", "open" -> ScottsTechXColors.SuccessGreen
        "shipped", "pending" -> ScottsTechXColors.WarningAmber
        else -> ScottsTechXColors.ErrorRed
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
fun ListDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

/** Quick reply chip used by AI screens and the thread composer. */
@Composable
// onClick is LAST so `QuickChip("hi") { … }` binds the trailing lambda to it.
// With modifier last, the lambda bound to `modifier` instead and the compiler
// reported "No value passed for parameter 'onClick'".
fun QuickChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = ScottsTechXColors.BluePrimary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = ScottsTechXColors.BluePrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Gradient header used across AI screens. */
@Composable
fun GradientHeader(
    title: String,
    subtitle: String,
    colors: List<Color> = ScottsTechXColors.BrandGradientColors,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(colors),
                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            )
            // Deliberate order: background() FIRST, then the insets. A
            // padding modifier only shrinks what comes after it, so painting
            // first lets the gradient fill the status-bar strip while the
            // title below is inset. Swapping these two lines would leave a
            // bare band above the gradient. Do not "tidy" this.
            .statusBarSpacer()
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Column {
            if (onBack != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = Color.White,
                    // Modifier order is evaluation order, innermost last.
                    // The old chain ended .padding(6.dp).size(36.dp), so the
                    // GLYPH was 36dp and the padding grew the disc to 48dp —
                    // an oversized circle with an oversized arrow in it. Now
                    // the disc is a fixed 40dp touch target and the padding
                    // insets the glyph to 24dp inside it.
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onBack)
                        .padding(8.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}

// ── Shared chat primitives (used by both AI assistants and the thread screen) ──

internal data class ChatTurn(val fromUser: Boolean, val text: String)

/** Chat bubble with a 4dp tail on the sender's side. */
@Composable
internal fun ChatTurnBubble(turn: ChatTurn) {
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (turn.fromUser) 16.dp else 4.dp,
        bottomEnd = if (turn.fromUser) 4.dp else 16.dp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (turn.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (turn.fromUser) {
                        Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight))
                    } else {
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                    },
                    shape,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = turn.text,
                color = if (turn.fromUser) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = if (turn.text == "…") FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
