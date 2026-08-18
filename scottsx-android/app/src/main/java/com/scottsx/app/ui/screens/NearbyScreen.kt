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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.NearbySeller
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.EmptyState
import com.scottsx.app.ui.components.ListDivider
import com.scottsx.app.ui.components.LoadingRow
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Uganda city presets used by the location selector. */
private data class City(val name: String, val lat: Double, val lng: Double)

private val CITIES = listOf(
    City("Kampala", 0.3476, 32.5825),
    City("Entebbe", 0.0611, 32.4444),
    City("Jinja", 0.4255, 33.2041),
    City("Mbarara", -0.6072, 30.6545),
    City("Gulu", 2.7724, 32.2881),
    City("Mbale", 1.0747, 34.1761),
)

private enum class SortMode(val label: String) { Nearest("Nearest"), TopRated("Top rated"), MostProducts("Most products") }

/**
 * NearbyScreen — LocationStatusCard (gradient) with 6 Uganda cities as chips,
 * a FilterSortBar (category chips, sort pill, verified toggle, radius slider),
 * then a filtered + sorted seller list.
 */
@Composable
fun NearbyScreen(onBack: () -> Unit) {
    var sellers by remember { mutableStateOf<List<NearbySeller>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedCity by remember { mutableStateOf(CITIES[0]) }
    var category by remember { mutableStateOf(ProductCategory.All) }
    var sortMode by remember { mutableStateOf(SortMode.Nearest) }
    var verifiedOnly by remember { mutableStateOf(false) }
    var radiusKm by remember { mutableFloatStateOf(50f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun refresh() {
        loading = true
        scope.launch {
            sellers = V2Client.fetchNearbySellers(selectedCity.lat, selectedCity.lng, radiusKm.toInt())
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(selectedCity) { refresh() }
    LaunchedEffect(radiusKm.toInt()) { refresh() }

    val filtered = sellers.asSequence()
        .filter { it.distanceKm <= radiusKm }
        .filter { if (verifiedOnly) it.verified else true }
        .sortedWith(
            when (sortMode) {
                SortMode.TopRated -> compareByDescending { it.rating }
                SortMode.MostProducts -> compareByDescending { it.productCount }
                else -> compareBy { it.distanceKm }
            },
        )
        .toList()

    Column(modifier = Modifier.fillMaxSize()) {
        // LocationStatusCard (gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)),
                    RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                )
                .padding(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(onClick = onBack)
                            .padding(4.dp)
                            .size(32.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text("Nearby sellers", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${filtered.size} sellers within ${radiusKm.toInt()} km", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 6 Uganda city chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CITIES.size) { index ->
                        val city = CITIES[index]
                        val isSelected = city.name == selectedCity.name
                        Surface(
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { selectedCity = city },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = if (isSelected) ScottsTechXColors.BluePrimary else Color.White, modifier = Modifier.size(14.dp))
                                Text(
                                    city.name,
                                    color = if (isSelected) ScottsTechXColors.BluePrimary else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // FilterSortBar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ProductCategory.values().toList()) { cat ->
                    val selected = cat == category
                    Surface(
                        color = if (selected) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { category = cat },
                    ) {
                        Text(
                            "${cat.emoji} ${cat.displayName}",
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Sort pill (cycles)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable {
                        sortMode = when (sortMode) {
                            SortMode.Nearest -> SortMode.TopRated
                            SortMode.TopRated -> SortMode.MostProducts
                            SortMode.MostProducts -> SortMode.Nearest
                        }
                    },
                ) {
                    Text(
                        "⇅ ${sortMode.label}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                // Verified-only toggle
                Surface(
                    color = if (verifiedOnly) ScottsTechXColors.SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { verifiedOnly = !verifiedOnly },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (verifiedOnly) ScottsTechXColors.SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "Verified only",
                            color = if (verifiedOnly) ScottsTechXColors.SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                // Radius slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("${radiusKm.toInt()} km", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ScottsTechXColors.BluePrimary)
                    Slider(
                        value = radiusKm,
                        onValueChange = { radiusKm = it },
                        valueRange = 1f..100f,
                    )
                }
            }
        }

        if (loading) {
            LoadingRow()
        } else if (filtered.isEmpty()) {
            EmptyState("📍", "No sellers found", "Try a bigger radius or another city.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(filtered, key = { it.id }) { seller ->
                    SellerRow(seller)
                    ListDivider()
                }
            }
        }
    }
}

@Composable
private fun SellerRow(seller: NearbySeller) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(
                    Brush.linearGradient(listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(seller.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(seller.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (seller.verified) Icon(Icons.Filled.CheckCircle, contentDescription = "Verified", tint = ScottsTechXColors.SuccessGreen, modifier = Modifier.size(15.dp))
            }
            Text(
                "${seller.rating} ★ · ${seller.productCount} products · ${seller.city.ifBlank { "Uganda" }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = ScottsTechXColors.BluePrimary.copy(alpha = 0.10f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                "${seller.distanceKm} km",
                color = ScottsTechXColors.BluePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}
