package com.scottsx.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.util.formatUgx
import kotlinx.coroutines.launch

/**
 * Stage 5 — Nearby screen with real geolocation.
 *
 * Uses FusedLocationProviderClient to get the device's current
 * location, then queries [V2Client.nearbySellers] which hits
 * /api/v1/sellers/v2/nearby on the Fastify backend. Falls back to
 * a Kampala-anchored position if permission is denied.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NearbyMapScreen(
    onBack: () -> Unit,
    onOpenStore: (String) -> Unit = {},
    onOpenProduct: (Product) -> Unit = {},
    onOpenProductById: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lat by remember { mutableStateOf(0.3476) } // Kampala fallback
    var lng by remember { mutableStateOf(32.5825) }
    var radiusKm by remember { mutableStateOf(25f) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf("list") }
    var sellers by remember { mutableStateOf<List<V2Client.NearbySeller>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var locationStatus by remember { mutableStateOf("Locating you…") }

    fun reload() {
        isLoading = true
        scope.launch {
            val list = V2Client.nearbySellers(
                lat = lat, lng = lng,
                radiusKm = radiusKm.toDouble(),
                category = categoryFilter, limit = 60,
            )
            sellers = list
            isLoading = false
            V2Client.recordSignal("category", "Nearby")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            scope.launch {
                runCatching {
                    val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(ctx)
                    val cts = com.google.android.gms.tasks.CancellationTokenSource()
                    @Suppress("MissingPermission")
                    client.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token,
                    ).addOnSuccessListener { loc ->
                        if (loc != null) {
                            lat = loc.latitude
                            lng = loc.longitude
                            locationStatus = "Live location"
                        } else {
                            locationStatus = "Approximate — Kampala"
                        }
                        reload()
                    }.addOnFailureListener {
                        locationStatus = "Approximate — Kampala"
                        reload()
                    }
                }.onFailure {
                    locationStatus = "Approximate — Kampala"
                    reload()
                }
            }
        } else {
            locationStatus = "Approximate — Kampala"
            reload()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching {
                val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(ctx)
                val cts = com.google.android.gms.tasks.CancellationTokenSource()
                @Suppress("MissingPermission")
                client.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token,
                ).addOnSuccessListener { loc ->
                    if (loc != null) {
                        lat = loc.latitude; lng = loc.longitude
                        locationStatus = "Live location"
                    } else {
                        locationStatus = "Approximate — Kampala"
                    }
                    reload()
                }.addOnFailureListener {
                    locationStatus = "Approximate — Kampala"
                    reload()
                }
            }.onFailure {
                locationStatus = "Approximate — Kampala"
                reload()
            }
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nearby", fontWeight = FontWeight.SemiBold)
                        Text(
                            locationStatus,
                            fontSize = 11.sp,
                            color = ScottsTechXColors.TextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewMode = if (viewMode == "list") "map" else "list"
                    }) {
                        Icon(
                            if (viewMode == "list") Icons.Filled.Map else Icons.Filled.ViewList,
                            contentDescription = "Toggle view",
                            tint = ScottsTechXColors.Primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScottsTechXColors.Background,
                    titleContentColor = ScottsTechXColors.TextPrimary,
                    navigationIconContentColor = ScottsTechXColors.TextPrimary,
                ),
            )
        },
        containerColor = ScottsTechXColors.Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Category chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val cats = listOf<String?>(null) + ProductCategory.values().map { it.name }
                items(cats) { cat ->
                    val label = cat ?: "All"
                    val selected = categoryFilter == cat
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) ScottsTechXColors.Primary else ScottsTechXColors.Surface,
                        modifier = Modifier.clickable {
                            categoryFilter = cat
                            reload()
                        },
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else ScottsTechXColors.TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Radius slider
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Radius:", fontSize = 12.sp, color = ScottsTechXColors.TextSecondary)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = radiusKm,
                    onValueChange = { radiusKm = it },
                    valueRange = 1f..100f,
                    onValueChangeFinished = { reload() },
                    colors = SliderDefaults.colors(
                        thumbColor = ScottsTechXColors.Primary,
                        activeTrackColor = ScottsTechXColors.Primary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${radiusKm.toInt()} km",
                    fontSize = 12.sp,
                    color = ScottsTechXColors.TextPrimary,
                )
            }

            Divider(color = ScottsTechXColors.Divider)

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = ScottsTechXColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else if (sellers.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No sellers found within ${radiusKm.toInt()} km. Try widening the radius.",
                        color = ScottsTechXColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (viewMode == "list") {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sellers) { s ->
                        NearbySellerCard(
                            s,
                            onClick = { onOpenStore(s.sellerId) },
                            onProductClick = { p ->
                                scope.launch { V2Client.recordSignal("seller", s.sellerId) }
                                onOpenProductById(p.id)
                            },
                        )
                    }
                }
            } else {
                // Map view — list of pin rows
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sellers) { s ->
                        MapRow(s) { onOpenStore(s.sellerId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbySellerCard(
    s: V2Client.NearbySeller,
    onClick: () -> Unit,
    onProductClick: (V2Client.NearbyProduct) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ScottsTechXColors.Surface,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = ScottsTechXColors.Primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.storeName.ifBlank { "Seller ${s.sellerId.take(6)}" },
                        color = ScottsTechXColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val loc = listOfNotNull(s.city, s.address).joinToString(" · ")
                    if (loc.isNotBlank()) {
                        Text(loc, fontSize = 11.sp, color = ScottsTechXColors.TextSecondary)
                    }
                }
                Text(
                    "${"%.1f".format(s.distanceKm)} km",
                    fontSize = 12.sp,
                    color = ScottsTechXColors.Primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (s.products.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.products.take(8)) { p ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ScottsTechXColors.Background,
                            modifier = Modifier.clickable { onProductClick(p) },
                        ) {
                            Column(Modifier.padding(8.dp).width(120.dp)) {
                                if (p.image != null) {
                                    AsyncImage(
                                        model = p.image,
                                        contentDescription = p.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .background(ScottsTechXColors.Divider, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("📦", fontSize = 28.sp)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    p.title, fontSize = 11.sp,
                                    color = ScottsTechXColors.TextPrimary,
                                    maxLines = 1,
                                )
                                Text(
                                    formatUgx(p.priceMinor),
                                    fontSize = 11.sp,
                                    color = ScottsTechXColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
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
private fun MapRow(s: V2Client.NearbySeller, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp)
                .clip(CircleShape)
                .background(ScottsTechXColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.storeName.ifBlank { "Seller ${s.sellerId.take(6)}" },
                color = ScottsTechXColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${"%.1f".format(s.distanceKm)} km away",
                fontSize = 11.sp,
                color = ScottsTechXColors.TextSecondary,
            )
        }
    }
}
