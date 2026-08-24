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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.MarketplaceDataSource
import com.scottsx.app.data.domain.RatingDistribution
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Full reviews list for a product. Reached from the PDP "See all
 * reviews" link. Re-uses the [ProductDetailScreen] review row
 * rendering logic for visual consistency.
 */
@Composable
fun ReviewsScreen(
    productId: String,
    onBack: () -> Unit,
) {
    val product = remember(productId) { MarketplaceDataSource.productById(productId) }
    val reviews = remember(productId) { MarketplaceDataSource.reviewsFor(productId) }
    val distribution = remember(productId) { MarketplaceDataSource.ratingDistributionFor(productId) }

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScottsTechXColors.BluePrimaryDark)
                .padding(start = 4.dp, end = 16.dp, top = 30.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Reviews", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        if (product == null) {
            Text("Product not found", modifier = Modifier.padding(16.dp), color = ScottsTechXColors.OnLight)
            return@Column
        }

        // Header summary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                    Text(
                        text = "%.1f".format(product.rating),
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp,
                    )
                    Row {
                        repeat(5) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(11.dp))
                        }
                    }
                    Text("${reviews.size} reviews", color = ScottsTechXColors.OnLightSecondary, fontSize = 10.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    for (stars in 5 downTo 1) {
                        RatingBarRow(stars = stars, percent = distribution.percent(stars))
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(reviews) { r -> ReviewRow(r) }
        }
    }
}
