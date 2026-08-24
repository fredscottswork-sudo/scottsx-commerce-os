package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.SettingsScaffold
import com.scottsx.app.ui.components.SettingsBlankHint
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SavedProductsScreen(onBack: () -> Unit, onOpenProduct: (String) -> Unit = {}) {
    val list = remember { mutableStateListOf<org.json.JSONObject>() }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            val arr = V2Client.fetchSavedProducts()
            list.clear()
            if (arr != null) for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            if (obj != null) {
                                list.add(obj as JSONObject)
                            }
                        }
            loading = false
        }
    }
    SettingsScaffold(title = "Saved Products", onBack = onBack) {
        if (loading) {
            SettingsBlankHint("Loading...")
        } else if (list.isEmpty()) {
            SettingsBlankHint("You haven't saved any products yet.")
        } else {
            list.forEach { p ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable { onOpenProduct(p.optString("productId")) }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = p.optString("imageUrl"),
                            contentDescription = null,
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE5E7EB)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.optString("title"), maxLines = 2, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                formatMinor(p.optLong("priceMinor"), p.optString("currency")),
                                fontSize = 13.sp,
                                color = ScottsTechXColors.BluePrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!p.isNull("storeName")) {
                                Text("by ${p.optString("storeName")}", fontSize = 11.sp, color = ScottsTechXColors.OnLightSecondary)
                            }
                        }
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = "Remove",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp).clickable {
                                scope.launch {
                                    V2Client.unsaveProduct(p.optString("productId"))
                                    list.remove(p)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedSellersScreen(onBack: () -> Unit, onOpenStore: (String) -> Unit = {}) {
    val list = remember { mutableStateListOf<org.json.JSONObject>() }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            val arr = V2Client.fetchSavedSellers()
            list.clear()
            if (arr != null) for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            if (obj != null) {
                                list.add(obj as JSONObject)
                            }
                        }
            loading = false
        }
    }
    SettingsScaffold(title = "Favorite Sellers", onBack = onBack) {
        if (loading) {
            SettingsBlankHint("Loading...")
        } else if (list.isEmpty()) {
            SettingsBlankHint("You haven't favorited any sellers yet.")
        } else {
            list.forEach { s ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable { onOpenStore(s.optString("sellerId")) }
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ScottsTechXColors.BluePrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                s.optString("displayName").firstOrNull()?.uppercase() ?: "S",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.optString("businessName"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(s.optString("marketName"), fontSize = 12.sp, color = ScottsTechXColors.OnLightSecondary)
                        }
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Unfollow",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp).clickable {
                                scope.launch {
                                    V2Client.unsaveSeller(s.optString("sellerId"))
                                    list.remove(s)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun formatMinor(minor: Long, currency: String?): String {
    val c = currency ?: "UGX"
    val major = minor / 100
    return if (c == "UGX") "$c $major" else "$c $major"
}
