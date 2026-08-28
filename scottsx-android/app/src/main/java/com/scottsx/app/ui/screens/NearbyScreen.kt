package com.scottsx.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.data.location.LocationProvider
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.components.ShimmerBox
import com.scottsx.app.ui.components.navBarSpacer
import com.scottsx.app.ui.components.statusBarSpacer
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nearby stores — global, self-locating. Function-parity with the web's
 * `/nearby` page (same `GET /api/v1/sellers/nearby` call):
 *   • NO radius control and NO district list — the marketplace is worldwide;
 *     the server returns every store on earth, sorted by distance.
 *   • Auto-detects the buyer's position on open, names the spot
 *     (village / city / region / country) and re-sorts continuously as the
 *     buyer moves ("Follow my location", 250 m re-query threshold).
 *   • Sellers sharing their location show a LIVE pin; everybody else stays
 *     pinned at their last known position ("Last seen" / "Fixed address").
 */

private const val REFETCH_METRES = 250.0

private fun metresBetween(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val la1 = Math.toRadians(aLat)
    val la2 = Math.toRadians(bLat)
    val h = sin(dLat / 2).let { it * it } +
        cos(la1) * cos(la2) * sin(dLng / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}

@Composable
fun NearbyScreen(
    onBack: () -> Unit,
    onOpenProduct: (com.scottsx.app.data.domain.Product) -> Unit = {},
    onOpenStore: (String) -> Unit = {},
    onTabSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { LocationProvider(context) }

    // ---- Position state ----------------------------------------------------
    var hasLocationPermission by remember {
        mutableStateOf(provider.hasLocationPermission())
    }
    var center by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var place by remember { mutableStateOf<V2Client.GeoPlace?>(null) }
    var locating by remember { mutableStateOf(true) }
    var geoDenied by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(false) }
    var watcherStop by remember { mutableStateOf<(() -> Unit)?>(null) }
    var moved by remember { mutableStateOf(false) }
    val savedOnce = remember { mutableStateOf(false) }

    // ---- Filter state (mirrors the web: search / sort / verified / open) ---
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("distance") } // distance|rating|products|newest
    var verifiedOnly by remember { mutableStateOf(false) }
    var openOnly by remember { mutableStateOf(false) }

    // ---- Results -----------------------------------------------------------
    var sellers by remember { mutableStateOf<List<V2Client.NearbySeller>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var liveCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastFetchCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocationPermission = granted }

    // Auto-identify the buyer's location the moment the screen opens (web
    // parity) — ask for the permission once on first composition.
    var askedPermission by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission && !askedPermission) {
            askedPermission = true
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** Apply a new buyer position: name it, persist it once, refresh list. */
    suspend fun applyPosition(lat: Double, lng: Double, accuracyM: Float? = null) {
        center = lat to lng
        locating = false
        if (!savedOnce.value) {
            savedOnce.value = true
            // Signed-in buyers get their fix persisted (web parity); guests
            // just get the reverse-geocoded label.
            val saved = try { V2Client.saveMyLocation(lat, lng, accuracyM?.toDouble()) } catch (_: Throwable) { null }
            if (saved != null) place = saved
            else place = try { V2Client.geoReverse(lat, lng) } catch (_: Throwable) { null }
        } else {
            place = try { V2Client.geoReverse(lat, lng) } catch (_: Throwable) { null }
        }
    }

    suspend fun fetchSellers(at: Pair<Double, Double>) {
        error = null
        val r = try {
            V2Client.nearbySellers(
                lat = at.first, lng = at.second,
                q = query.trim().ifBlank { null },
                sort = sort,
                verifiedOnly = verifiedOnly,
                openOnly = openOnly,
            )
        } catch (_: Throwable) { null }
        if (r == null) {
            error = "Could not load nearby stores — check your connection."
        } else {
            sellers = r.sellers
            total = r.total
            liveCount = r.liveCount
            if (r.place != null) place = r.place
            lastFetchCenter = at
            moved = false
        }
        loading = false
    }

    // ── Initial fix: GPS when permitted, saved position for signed-in users,
    //    otherwise the honest "couldn't detect" card with a retry. ─────────
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && center == null) {
            locating = true
            geoDenied = false
            val loc = provider.currentLocation()
            if (loc != null) {
                applyPosition(loc.latitude, loc.longitude, loc.accuracy)
            } else {
                val saved = try { V2Client.fetchMyLocation() } catch (_: Throwable) { null }
                if (saved != null && saved.lat != 0.0) {
                    center = saved.lat to saved.lng
                    if (saved.place != null) place = saved.place
                } else {
                    geoDenied = true
                }
                locating = false
                loading = false
            }
        } else if (!hasLocationPermission) {
            val saved = try { V2Client.fetchMyLocation() } catch (_: Throwable) { null }
            if (saved != null && saved.lat != 0.0) {
                center = saved.lat to saved.lng
                if (saved.place != null) place = saved.place
                loading = false
            } else {
                geoDenied = true
                loading = false
            }
            locating = false
        }
    }

    // Refresh whenever the position or a filter changes (debounced like the web).
    LaunchedEffect(center, query, sort, verifiedOnly, openOnly) {
        val at = center ?: return@LaunchedEffect
        loading = true
        delay(220)
        fetchSellers(at)
    }

    fun startFollowing() {
        if (!hasLocationPermission) { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION); return }
        following = true
        geoDenied = false
        watcherStop = provider.watchLocation { loc ->
            // Re-sort locally on every fix (instant feedback), re-query the
            // server only after the buyer has really moved.
            sellers = sellers.map {
                it.copy(distanceKm = (metresBetween(loc.latitude, loc.longitude, it.lat, it.lng) / 1000.0 * 100).roundToInt() / 100.0)
            }.let { list ->
                if (sort == "distance") list.sortedBy { it.distanceKm } else list
            }
            val from = lastFetchCenter
            if (from == null || metresBetween(from.first, from.second, loc.latitude, loc.longitude) > REFETCH_METRES) {
                moved = true
                savedOnce.value = false
                scope.launch { applyPosition(loc.latitude, loc.longitude, loc.accuracy) }
            }
        }
    }

    fun stopFollowing() {
        watcherStop?.invoke()
        watcherStop = null
        following = false
    }

    DisposableEffect(Unit) { onDispose { watcherStop?.invoke() } }

    // ───────────────────────────────────────────────────────────────────────
    Column(modifier = modifier.fillMaxSize().background(ScottsTechXColors.BackgroundLight)) {
        // ---- Gradient header (status-bar padded) ---------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary),
                        start = Offset.Zero, end = Offset(800f, 500f),
                    ),
                )
                .statusBarSpacer()
                .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Stores near you", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(
                            "Live sellers follow their GPS; others stay pinned at last known positions.",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (following) Color(0xFF16A34A)
                                else Color.White.copy(alpha = 0.18f),
                            )
                            .clickable { if (following) stopFollowing() else startFollowing() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (following) Icons.Filled.LocationOff else Icons.Filled.MyLocation,
                                contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (following) "Tracking" else "Follow me",
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // ---- Your-location banner ------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, ScottsTechXColors.Divider, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Your location", color = ScottsTechXColors.OnLightTertiary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        locating -> "Detecting your location…"
                        place != null && place?.label?.isNotBlank() == true -> place!!.label
                        center != null -> "Location detected"
                        else -> "Location unavailable"
                    },
                    color = ScottsTechXColors.OnLight, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                place?.let { p ->
                    val parts = listOfNotNull(p.village, p.city, p.region, p.country).distinct()
                    if (parts.isNotEmpty()) {
                        Text(
                            parts.joinToString(" · "),
                            color = ScottsTechXColors.OnLightTertiary, fontSize = 10.5.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (following) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFDCFCE7))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("Live GPS", color = Color(0xFF16A34A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---- Permission-denied card ----------------------------------------
        if (geoDenied) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFEF3C7))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("We could not detect your location", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Allow location access — you'll see every store, sorted by distance, anywhere in the world.",
                        color = ScottsTechXColors.OnLightSecondary, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ScottsTechXColors.BluePrimary)
                        .clickable {
                            geoDenied = false
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Try again", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---- Filters: search / sort / verified / open — NO radius, NO district
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, ScottsTechXColors.Divider, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = ScottsTechXColors.OnLightTertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Filter stores…", color = ScottsTechXColors.OnLightTertiary, fontSize = 13.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = ScottsTechXColors.OnLight, fontSize = 13.sp),
                    singleLine = true,
                )
            }
        }
        // Sort chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf(
                "distance" to "Nearest first",
                "rating" to "Top rated",
                "products" to "Most products",
                "newest" to "Newest stores",
            )) { (key, label) ->
                val active = sort == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) ScottsTechXColors.BluePrimary else Color.White)
                        .border(1.dp, if (active) ScottsTechXColors.BluePrimary else ScottsTechXColors.Divider, RoundedCornerShape(50))
                        .clickable { sort = key }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(label, color = if (active) Color.White else ScottsTechXColors.OnLightSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                val active = verifiedOnly
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Color(0xFF16A34A) else Color.White)
                        .border(1.dp, if (active) Color(0xFF16A34A) else ScottsTechXColors.Divider, RoundedCornerShape(50))
                        .clickable { verifiedOnly = !verifiedOnly }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Verified, contentDescription = null,
                            tint = if (active) Color.White else ScottsTechXColors.OnLightTertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Verified", color = if (active) Color.White else ScottsTechXColors.OnLightSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                val active = openOnly
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) ScottsTechXColors.PurpleAccent else Color.White)
                        .border(1.dp, if (active) ScottsTechXColors.PurpleAccent else ScottsTechXColors.Divider, RoundedCornerShape(50))
                        .clickable { openOnly = !openOnly }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text("Open now", color = if (active) Color.White else ScottsTechXColors.OnLightSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---- Stats strip -----------------------------------------------------
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { NearbyPill(text = "${sellers.size} of $total stores", bg = ScottsTechXColors.BluePrimary.copy(alpha = 0.10f), fg = ScottsTechXColors.BluePrimary) }
            if (liveCount > 0) item { NearbyPill(text = "$liveCount live", bg = Color(0xFFDCFCE7), fg = Color(0xFF16A34A)) }
            item { NearbyPill(text = "${sellers.count { it.isOpen }} open now", bg = ScottsTechXColors.CyanAccent.copy(alpha = 0.12f), fg = ScottsTechXColors.CyanAccent) }
            item { NearbyPill(text = "${sellers.count { it.withinServiceRadius }} deliver to you", bg = ScottsTechXColors.PurpleAccent.copy(alpha = 0.10f), fg = ScottsTechXColors.PurpleAccent) }
            if (moved) item { NearbyPill(text = "You moved — refreshing…", bg = ScottsTechXColors.WarningAmber.copy(alpha = 0.14f), fg = ScottsTechXColors.WarningAmber) }
        }

        // ---- Results ---------------------------------------------------------
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(4) {
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(18.dp)),
                            base = ScottsTechXColors.Divider,
                            highlight = Color.White,
                        )
                    }
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = ScottsTechXColors.OnLightTertiary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(error!!, color = ScottsTechXColors.OnLightSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ScottsTechXColors.BluePrimary)
                            .clickable {
                                loading = true
                                scope.launch {
                                    val at = center ?: return@launch
                                    fetchSellers(at)
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text("Retry", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                sellers.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Store, contentDescription = null, tint = ScottsTechXColors.OnLightTertiary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No stores match", color = ScottsTechXColors.OnLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Clear the filters to see every store, sorted by distance from you.",
                        color = ScottsTechXColors.OnLightSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ScottsTechXColors.BluePrimary)
                            .clickable {
                                query = ""; verifiedOnly = false; openOnly = false; sort = "distance"
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text("Clear filters", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sellers, key = { it.sellerId }) { seller ->
                        SellerCard(seller = seller, onOpen = { onOpenStore(seller.sellerId) })
                    }
                }
            }

            // Bottom navigation
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                ScottsTechXBottomBar(selected = BottomTab.Nearby, onSelect = onTabSelect)
            }
        }
    }
}

@Composable
private fun NearbyPill(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** One store card — layout parity with the web Nearby card. */
@Composable
private fun SellerCard(seller: V2Client.NearbySeller, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, ScottsTechXColors.Divider, RoundedCornerShape(18.dp))
            .clickable { onOpen() }
            .padding(14.dp),
    ) {
        // Store avatar: real logo when the backend has one, initial otherwise.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ScottsTechXColors.BluePrimary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!seller.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = seller.logoUrl,
                    contentDescription = seller.storeName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    (seller.storeName.firstOrNull() ?: 'S').uppercase(),
                    color = ScottsTechXColors.BluePrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Title row: name + verified tick + open/closed badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    seller.storeName,
                    color = ScottsTechXColors.OnLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (seller.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = Color(0xFF16A34A), modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (seller.isOpen) Color(0xFFDCFCE7) else ScottsTechXColors.Divider)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        if (seller.isOpen) "Open" else "Closed",
                        color = if (seller.isOpen) Color(0xFF16A34A) else ScottsTechXColors.OnLightTertiary,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Rating · products · new-this-week
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                Text(
                    " ${"%.1f".format(seller.rating)} · ${seller.productCount} products",
                    color = ScottsTechXColors.OnLightSecondary, fontSize = 11.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (seller.newThisWeek > 0) {
                    Text(" · ${seller.newThisWeek} new", color = Color(0xFF16A34A), fontSize = 11.5.sp, maxLines = 1)
                }
            }
            // Real place label for the store pin (as the web shows it)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = ScottsTechXColors.OnLightTertiary, modifier = Modifier.size(12.dp))
                Text(
                    " " + seller.placeLabel.ifBlank { seller.address ?: seller.city ?: "—" },
                    color = ScottsTechXColors.OnLightTertiary, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // Trust badges: live / last-seen / delivery / COD
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (seller.live) {
                    NearbyPill(
                        text = "Live · ${seller.locationAgeMinutes ?: 0}m ago",
                        bg = Color(0xFFDCFCE7), fg = Color(0xFF16A34A),
                    )
                } else {
                    NearbyPill(
                        text = if (seller.locationSharing) "Last seen" else "Fixed address",
                        bg = ScottsTechXColors.WarningAmber.copy(alpha = 0.12f), fg = Color(0xFFB45309),
                    )
                }
                if (seller.withinServiceRadius) {
                    NearbyPill(
                        text = if (seller.deliveryFeeUgx > 0) "Delivery UGX ${"%,d".format(seller.deliveryFeeUgx)}" else "Free delivery",
                        bg = ScottsTechXColors.CyanAccent.copy(alpha = 0.10f), fg = ScottsTechXColors.CyanAccent,
                    )
                } else {
                    NearbyPill(text = "Outside delivery zone", bg = ScottsTechXColors.Divider, fg = ScottsTechXColors.OnLightTertiary)
                }
                if (seller.codEnabled) {
                    NearbyPill(text = "Pay on delivery", bg = ScottsTechXColors.PurpleAccent.copy(alpha = 0.10f), fg = ScottsTechXColors.PurpleAccent)
                }
            }
        }
        // Distance + ETA (server-computed)
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Filled.Navigation, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(14.dp))
            Text(
                if (seller.distanceKm < Double.MAX_VALUE) "${"%.1f".format(seller.distanceKm)} km" else "—",
                color = ScottsTechXColors.OnLight, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                maxLines = 1, softWrap = false,
            )
            if (seller.etaMinutes > 0) {
                Text("~${seller.etaMinutes} min", color = ScottsTechXColors.OnLightTertiary, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}
