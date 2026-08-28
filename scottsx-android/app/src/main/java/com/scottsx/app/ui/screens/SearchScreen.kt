package com.scottsx.app.ui.screens

import com.scottsx.app.data.CartStore

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.LiveMarketplace
import com.scottsx.app.data.remote.V2Client
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.theme.ScottsTechXColors

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenProduct: (com.scottsx.app.data.domain.Product) -> Unit = {},
    onTabSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var bottomTab by remember { mutableStateOf(BottomTab.Home) }

    // Live search — hits the canonical /products/search endpoint with a
    // short debounce. On failure it degrades to filtering the live
    // catalogue cache; it never reads the retired seed fixture.
    var results by remember { mutableStateOf<List<com.scottsx.app.data.domain.Product>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var trending by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.launch { LiveMarketplace.ensureLoaded() }
        val facets = try { V2Client.fetchCatalogFacets() } catch (_: Throwable) { null }
        if (facets != null) {
            trending = (facets.categories.take(6).map { it.name } +
                facets.brands.take(4).map { it.name }).take(8)
        }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        val remote = try { V2Client.searchProducts(query = q) } catch (_: Throwable) { null }
        if (remote != null) {
            LiveMarketplace.cache(remote)
            results = remote
        } else {
            val needle = q.lowercase()
            results = LiveMarketplace.products.value.filter { p ->
                (p.name + " " + p.shortDescription + " " + p.brand.name + " " + p.category.displayName)
                    .lowercase().contains(needle)
            }
        }
        searching = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary),
                        ),
                    )
                    .padding(top = 36.dp, bottom = 14.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = ScottsTechXColors.OnLightSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    text = "Search for products, brands, categories...",
                                    color = ScottsTechXColors.OnLightSecondary,
                                    fontSize = 13.sp,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = ScottsTechXColors.OnLight,
                                unfocusedTextColor = ScottsTechXColors.OnLight,
                            ),
                        )
                    }
                }
            }

            if (query.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Trending searches",
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(trending) { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .clickable { query = tag }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = tag,
                                    color = ScottsTechXColors.BluePrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Browse categories",
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "Tap a category on the home screen to see products in that category.",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 12.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(results, key = { it.id }) { p ->
                        ProductCard(
                            product = p,
                            width = 168.dp,
                            onClick = { onOpenProduct(p) },
                            onAddToCart = { CartStore.add(p.id) },
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            ScottsTechXBottomBar(
                selected = bottomTab,
                onSelect = { tab ->
                    bottomTab = tab
                    onTabSelect(tab)
                },
            )
        }
    }
}
