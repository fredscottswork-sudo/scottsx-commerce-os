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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Spa
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.ui.components.BottomTab
import com.scottsx.app.ui.components.ProductCard
import com.scottsx.app.ui.components.ScottsTechXBottomBar
import com.scottsx.app.ui.theme.ScottsTechXColors

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onOpenProduct: (com.scottsx.app.data.domain.Product) -> Unit = {},
    onTabSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf<ProductCategory?>(null) }
    var bottomTab by remember { mutableStateOf(BottomTab.Home) }

    val grid = listOf(
        ProductCategory.All,
        ProductCategory.Electronics,
        ProductCategory.Fashion,
        ProductCategory.HomeLiving,
        ProductCategory.Beauty,
        ProductCategory.Sports,
        ProductCategory.Groceries,
        ProductCategory.Automotive,
    )

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary),
                        ),
                    )
                    .padding(top = 36.dp, bottom = 18.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Browse categories",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                    )
                    Text(
                        text = "Find what you need by category",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
            }

            if (selectedCategory == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(grid, key = { it.name }) { cat ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .clickable {
                                    selectedCategory = cat
                                }
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.BluePrimaryLight),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = iconFor(cat),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = cat.displayName,
                                color = ScottsTechXColors.OnLight,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                val products = MarketplaceDataSource.productsByCategory(selectedCategory!!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .clickable { selectedCategory = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "< All categories",
                            color = ScottsTechXColors.BluePrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = selectedCategory!!.displayName,
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(products, key = { it.id }) { p ->
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

private fun iconFor(c: ProductCategory): ImageVector = when (c) {
    ProductCategory.All -> Icons.Filled.LocalGroceryStore
    ProductCategory.Electronics -> Icons.Filled.Devices
    ProductCategory.Fashion -> Icons.Filled.Checkroom
    ProductCategory.HomeLiving -> Icons.Filled.Home
    ProductCategory.Beauty -> Icons.Filled.Spa
    ProductCategory.Sports -> Icons.Filled.DirectionsRun
    ProductCategory.Groceries -> Icons.Filled.LocalGroceryStore
    ProductCategory.Automotive -> Icons.Filled.DirectionsCar
    ProductCategory.More -> Icons.Filled.Home
}
