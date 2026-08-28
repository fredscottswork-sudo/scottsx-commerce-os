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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.LiveMarketplace
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.ui.components.statusBarSpacer

/**
 * Full reviews list for a product, fed entirely by
 * `GET /api/v1/products/:id/ratings` — real buyer ratings with a
 * real histogram. Reached from the PDP "See all reviews" link.
 * Re-uses [ReviewRow] from the PDP for visual consistency.
 */
@Composable
fun ReviewsScreen(
    productId: String,
    onBack: () -> Unit,
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var ratingsPage by remember { mutableStateOf<V2Client.ProductRatingsPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(productId) {
        isLoading = true
        loadError = null
        try {
            product = LiveMarketplace.byIdOrFetch(productId)
            val page = V2Client.fetchProductRatings(productId)
            if (page == null) {
                loadError = "Couldn't load reviews — check your connection."
            } else {
                ratingsPage = page
            }
        } catch (t: Throwable) {
            loadError = "Couldn't load reviews: ${t.message ?: "unknown error"}"
        }
        isLoading = false
    }

    val distribution = remember(ratingsPage) {
        ratingsPage?.let { with(V2Client) { it.toDistribution() } }
    }

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight).statusBarSpacer()) {
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

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = ScottsTechXColors.BluePrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            loadError != null && ratingsPage == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadError ?: "", color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 13.sp, modifier = Modifier.padding(24.dp))
                }
            }
            else -> {
                val page = ratingsPage ?: return@Column
                val average = page.summary.average

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
                                text = "%.1f".format(average),
                                color = ScottsTechXColors.OnLight,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp,
                            )
                            Row {
                                repeat(5) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = if (it < kotlin.math.round(average).toInt()) Color(0xFFFBBF24) else Color(0xFFD1D5DB),
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                            Text("${page.summary.count} reviews",
                                color = ScottsTechXColors.OnLightSecondary, fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (distribution != null && distribution.total > 0) {
                                for (stars in 5 downTo 1) {
                                    RatingBarRow(stars = stars, percent = distribution.percent(stars))
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    val reviews = page.ratings.map { with(V2Client) { it.toDomainReview(productId) } }
                    items(reviews, key = { it.id }) { r -> ReviewRow(r) }
                    if (reviews.isEmpty()) {
                        item("empty") {
                            Text(
                                "No reviews yet for${product?.let { " ${it.name}" } ?: " this product"} — be the first to rate it.",
                                color = ScottsTechXColors.OnLightSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
