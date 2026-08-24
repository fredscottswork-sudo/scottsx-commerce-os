package com.scottsx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.scottsx.app.data.domain.NewProductPayload
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CATEGORIES = listOf("Electronics", "Fashion", "Sports", "Beauty", "Home & Living", "Groceries", "Automotive")

/**
 * Add product wizard.
 *
 * Step 0 — Basics: name, description, category chips + "✨ AI suggest from photo".
 * Step 1 — Pricing: price (UGX), old price, stock.
 * Step 2 — Photos: one image URL (Coil preview).
 * Step 3 — Review & publish.
 */
@Composable
fun AddProductScreen(onBack: () -> Unit) {
    var step by remember { mutableStateOf(0) }

    // Step 0
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES[0]) }
    // AI suggestion
    var suggestion by remember { mutableStateOf<Pair<String, String>?>(null) } // title to category
    var suggestedDescription by remember { mutableStateOf("") }

    // Step 1
    var price by remember { mutableStateOf("") }
    var oldPrice by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("1") }

    // Step 2
    var imageUrl by remember { mutableStateOf("") }

    var publishing by remember { mutableStateOf(false) }
    var published by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Photo picker: on Android 13+ this is the system picker and needs no
    // storage permission at all; below that the platform provides the same
    // contract backed by the document picker.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                uploading = true
                uploadError = ""
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) {
                    uploadError = "Could not read that photo"
                } else {
                    // Phones capture 8–15 MB photos that no one needs at full
                    // resolution in a product grid: downscale to ~1600px and
                    // JPEG-compress before uploading so slow networks don't
                    // time out. Decodable images are always re-encoded as
                    // JPEG; anything that isn't decodable keeps its original
                    // bytes and is held to the original 3 MB cap.
                    val compressed = withContext(Dispatchers.IO) { compressForUpload(bytes) }
                    val payload = compressed?.data ?: bytes
                    if (payload.size > 3 * 1024 * 1024) {
                        uploadError = "That photo is larger than 3 MB — pick a smaller one"
                    } else {
                        val mime = if (compressed != null) "image/jpeg"
                        else context.contentResolver.getType(uri) ?: "image/jpeg"
                        val url = V2Client.uploadImage(payload, "product.jpg", mime)
                        if (url != null) imageUrl = url else uploadError = "Upload failed — check your connection"
                    }
                }
                uploading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Add product", onBack = onBack)

        // Step indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (0..3).forEach { i ->
                Surface(
                    color = if (i <= step) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "${i + 1}",
                        color = if (i <= step) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            when (step) {
                0 -> {
                    Text("Basics", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    InputField(value = title, onValueChange = { title = it }, label = "Product name", placeholder = "e.g. Ankara Maxi Dress")
                    Spacer(Modifier.height(10.dp))
                    InputField(value = description, onValueChange = { description = it }, label = "Description", placeholder = "Condition, size, colour, delivery info…")

                    // ✨ AI suggest from photo
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val first = imageUrl.lowercase()
                            val name = heuristicName(first)
                            val cat = heuristicCategory(first)
                            suggestion = name to cat
                            suggestedDescription = "Carefully sourced and inspected before listing. Fast delivery within Kampala and across Uganda, with Cash-on-Delivery available. Message the seller for more photos or a bulk discount."
                        },
                        enabled = imageUrl.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("✨ AI suggest from photo", fontWeight = FontWeight.SemiBold)
                    }
                    if (imageUrl.isBlank()) {
                        Text(
                            "Add a photo URL in step 3 to enable AI suggestions.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    suggestion?.let { (sTitle, sCat) ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = ScottsTechXColors.BluePrimary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("AI suggestion", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ScottsTechXColors.BluePrimary)
                                Text("Title: $sTitle", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Category: $sCat", fontSize = 13.sp)
                                Text(suggestedDescription, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(color = ScottsTechXColors.SuccessGreen, shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable {
                                        title = sTitle
                                        category = sCat
                                        description = suggestedDescription
                                        suggestion = null
                                    }) {
                                        Text("Apply", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                                    }
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { suggestion = null }) {
                                        Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Category", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        CATEGORIES.take(4).forEach { cat ->
                            CategoryChip(cat, category == cat) { category = cat }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CATEGORIES.drop(4).forEach { cat ->
                            CategoryChip(cat, category == cat) { category = cat }
                        }
                    }
                }

                1 -> {
                    Text("Pricing & stock", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    InputField(value = price, onValueChange = { price = it }, label = "Price (UGX)", placeholder = "e.g. 85000", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    Spacer(Modifier.height(10.dp))
                    InputField(value = oldPrice, onValueChange = { oldPrice = it }, label = "Old price (optional, UGX)", placeholder = "For showing a discount", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    Spacer(Modifier.height(10.dp))
                    InputField(value = stock, onValueChange = { stock = it }, label = "Stock quantity", placeholder = "e.g. 10", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                }

                2 -> {
                    Text("Photos", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = if (uploading) "Uploading…" else if (imageUrl.isBlank()) "Choose a photo" else "Change photo",
                        onClick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !uploading,
                    )
                    if (uploading) {
                        Spacer(Modifier.height(10.dp))
                        CircularProgressIndicator(color = ScottsTechXColors.BluePrimary)
                    }
                    if (uploadError.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(uploadError, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    InputField(value = imageUrl, onValueChange = { imageUrl = it }, label = "…or paste an image link", placeholder = "https://images.unsplash.com/photo-…")
                    Spacer(Modifier.height(10.dp))
                    if (imageUrl.isNotBlank()) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        ) {
                            coil.compose.AsyncImage(
                                model = V2Client.absoluteMediaUrl(imageUrl),
                                contentDescription = "Product preview",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Text(
                            "Tip: use the photo URL from Unsplash or your store's hosting.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                3 -> {
                    Text("Review & publish", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(title.ifBlank { "Untitled product" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Category: $category", fontSize = 13.sp)
                            Text("Price: UGX ${price.ifBlank { "0" }} · Stock: $stock", fontSize = 13.sp)
                            Text(description.ifBlank { "No description" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    error?.let {
                        Text(it, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (published) {
                        Text(
                            "✅ Sent for review. An admin approves listings before they go " +
                                "live — you'll get a notification either way. Track it under " +
                                "\"In review\" in your inventory.",
                            color = ScottsTechXColors.SuccessGreen,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step -= 1 },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Back")
                    }
                }
                PrimaryButton(
                    text = if (step == 3) "Submit for review" else "Next",
                    loading = publishing,
                    onClick = {
                        when (step) {
                            0 -> step = 1
                            1 -> step = 2
                            2 -> step = 3
                            3 -> {
                                publishing = true
                                error = null
                                scope.launch {
                                    val payload = NewProductPayload(
                                        title = title,
                                        description = description,
                                        category = category,
                                        priceMinor = price.toLongOrNull() ?: 0,
                                        oldPriceMinor = oldPrice.toLongOrNull(),
                                        stockQuantity = stock.toIntOrNull() ?: 1,
                                        imageUrl = imageUrl,
                                        mediaUrls = if (imageUrl.isNotBlank()) listOf(imageUrl) else emptyList(),
                                        location = com.scottsx.app.UserPrefs.aiCity,
                                    )
                                    val created = V2Client.createSellerProduct(payload)
                                    if (created != null) {
                                        published = true
                                    } else {
                                        error = "Could not submit — check your connection and try again."
                                    }
                                    publishing = false
                                }
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) ScottsTechXColors.BluePrimary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Client-side heuristic matching the backend generator (photo URL → title/category). */
private fun heuristicName(url: String): String = when {
    url.contains("iphone") || url.contains("samsung") || url.contains("phone") -> "Premium Smartphone — Like New"
    url.contains("laptop") || url.contains("macbook") -> "Laptop Computer — Ready to Work"
    url.contains("sneaker") || url.contains("shoe") || url.contains("nike") -> "Genuine Footwear — Quality Checked"
    url.contains("dress") || url.contains("ankara") || url.contains("kitenge") -> "Stylish Ankara / Kitenge Dress"
    url.contains("lipstick") || url.contains("makeup") -> "Beauty & Makeup Essentials"
    url.contains("rice") || url.contains("oil") || url.contains("food") -> "Groceries — Fresh & Affordable"
    url.contains("basket") -> "Handwoven Basket — Local Craft"
    url.contains("watch") -> "Wristwatch — Genuine"
    url.contains("tire") || url.contains("tyre") -> "Automotive Accessory"
    else -> "New Arrival — Quality Product"
}

private fun heuristicCategory(url: String): String = when {
    url.contains("iphone") || url.contains("samsung") || url.contains("phone") ||
        url.contains("laptop") || url.contains("headphone") || url.contains("tv") -> "Electronics"
    url.contains("sneaker") || url.contains("shoe") || url.contains("nike") -> "Sports"
    url.contains("dress") || url.contains("ankara") || url.contains("kitenge") ||
        url.contains("watch") || url.contains("bag") -> "Fashion"
    url.contains("lipstick") || url.contains("makeup") || url.contains("soap") -> "Beauty"
    url.contains("rice") || url.contains("oil") || url.contains("food") -> "Groceries"
    url.contains("basket") -> "Home & Living"
    url.contains("tire") || url.contains("tyre") || url.contains("car") -> "Automotive"
    else -> "Fashion"
}

// ── Image compression ─────────────────────────────────────────────────────────

private data class CompressedImage(val data: ByteArray)

/**
 * Downscale + JPEG-compress a picked photo for upload.
 *
 * Returns null when the bytes are not a decodable bitmap (e.g. an exotic
 * format) — the caller then uploads the original bytes subject to the size
 * cap. Never throws: a compression failure falls back to the raw bytes, so
 * an uploadable photo is never blocked by the optimiser.
 */
private fun compressForUpload(bytes: ByteArray): CompressedImage? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    // Power-of-two sampling keeps it to a single decode pass.
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2

    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return@runCatching null

    // Step quality down until the payload is comfortably small for a
    // mobile upload — or until further compression would look bad.
    var quality = 85
    var out = java.io.ByteArrayOutputStream()
    decoded.compress(Bitmap.CompressFormat.JPEG, quality, out)
    while (out.size() > 2 * 1024 * 1024 && quality > 40) {
        quality -= 10
        out = java.io.ByteArrayOutputStream()
        decoded.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    decoded.recycle()
    CompressedImage(out.toByteArray())
}.getOrNull()
