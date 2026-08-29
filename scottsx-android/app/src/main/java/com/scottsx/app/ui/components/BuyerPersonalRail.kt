package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.data.Session
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import kotlinx.coroutines.launch

private data class RailData(
    val orders: List<V2Client.MyOrder>,
    val following: List<V2Client.FavoriteSeller>,
)

/**
 * Personal rail from the web buyer dashboard (web/src/pages/buyer/
 * BuyerDashboard.tsx): stats row, "On the way" strip, and the
 * sellers-you-follow strip. Signed-out visitors see nothing.
 */
@Composable
fun BuyerPersonalRail(
    onOpenOrders: () -> Unit,
    onTrackOrder: (String) -> Unit,
    onOpenStore: (String) -> Unit,
    savedCount: Int,
) {
    val signedIn = Session.tokenOrNull() != null
    val data by produceState<RailData?>(initialValue = null, signedIn) {
        if (!signedIn) return@produceState
        val ready = kotlinx.coroutines.coroutineScope {
            var orders: List<V2Client.MyOrder>? = null
            var following: List<V2Client.FavoriteSeller>? = null
            val j1 = launch { orders = V2Client.fetchMyOrders() }
            val j2 = launch { following = V2Client.fetchFavoriteSellers() }
            j1.join(); j2.join()
            if (orders == null && following == null) null else RailData(orders ?: emptyList(), following ?: emptyList())
        }
        value = ready
    }
    if (!signedIn || data == null) return

    val active = data!!.orders.filter {
        it.status.lowercase() !in listOf("delivered", "cancelled", "refunded")
    }
    val fullSpent = data!!.orders.filter {
        it.status.lowercase() in listOf("paid", "shipped", "delivered", "completed")
    }.sumOf { it.amountUgx }
    val following = data!!.following

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Stat chips (web: Active orders / Total spent / Saved / Following)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RailStat(
                label = "ACTIVE ORDERS", value = active.size.toString(),
                hint = "${data!!.orders.size} lifetime",
                onClick = onOpenOrders, modifier = Modifier.weight(1f),
            )
            RailStat(
                label = "TOTAL SPENT", value = formatUgx(fullSpent),
                hint = "Paid orders",
                onClick = onOpenOrders, modifier = Modifier.weight(1f),
            )
            RailStat(
                label = "SAVED", value = savedCount.toString(),
                hint = "In your wishlist",
                onClick = null, modifier = Modifier.weight(1f),
            )
            RailStat(
                label = "FOLLOWING", value = following.size.toString(),
                hint = "Sellers you follow",
                onClick = null, modifier = Modifier.weight(1f),
            )
        }

        if (active.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "On the way",
                    color = ScottsTechXColors.OnDark,
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.clickable(onClick = onOpenOrders),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("All orders", color = ScottsTechXColors.BluePrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(3.dp))
                    androidx.compose.material3.Icon(
                        Icons.Filled.ArrowForward, null,
                        tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(13.dp),
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(active.take(6), key = { it.id }) { o ->
                    TrackCard(o = o, onClick = { onTrackOrder(o.id) })
                }
            }
        }

        if (following.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sellers you follow",
                    color = ScottsTechXColors.OnDark,
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(following, key = { it.id }) { s ->
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ScottsTechXColors.CardSurface)
                            .clickable { onOpenStore(s.id) }
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SellerAvatar(name = s.storeName, logoUrl = s.logoUrl)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.storeName, color = ScottsTechXColors.OnCard,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "★ ${"%.1f".format(s.rating)} · ${s.productCount} products",
                                    color = ScottsTechXColors.OnCardSecondary, fontSize = 10.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailStat(
    label: String,
    value: String,
    hint: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.CardSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(label, color = ScottsTechXColors.OnCardTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            value, color = ScottsTechXColors.OnCard,
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            hint, color = ScottsTechXColors.OnCardSecondary, fontSize = 9.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrackCard(o: V2Client.MyOrder, onClick: () -> Unit) {
    val accent = when (o.status.lowercase()) {
        "shipped" -> ScottsTechXColors.CyanAccent
        "delivered", "completed" -> ScottsTechXColors.Success
        "paid", "processing" -> ScottsTechXColors.BluePrimary
        else -> ScottsTechXColors.WarningAmber
    }
    Row(
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ScottsTechXColors.CardSurface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (o.imageUrl.isNotBlank()) {
            AsyncImage(
                model = o.imageUrl,
                contentDescription = o.title,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ScottsTechXColors.CardSurfaceAlt),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                o.title, color = ScottsTechXColors.OnCard,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${o.storeName.ifBlank { "Seller" }} · ${formatUgx(o.amountUgx)}",
                color = ScottsTechXColors.OnCardSecondary, fontSize = 10.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                o.displayStatus.uppercase(),
                color = accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
internal fun SellerAvatar(name: String, logoUrl: String?) {
    if (!logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = logoUrl,
            contentDescription = name,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(ScottsTechXColors.BluePrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (name.firstOrNull() ?: 'S').uppercase(),
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            )
        }
    }
}
