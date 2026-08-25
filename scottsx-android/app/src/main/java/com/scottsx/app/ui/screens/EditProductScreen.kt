package com.scottsx.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scottsx.app.SessionCache
import com.scottsx.app.UserPrefs
import com.scottsx.app.data.domain.NewProductPayload
import com.scottsx.app.data.domain.Product
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edit listing — the missing half of the seller loop.
 *
 * The backend PATCH is a full-form update: every field is pre-filled from
 * the live product and sent back, so a partial save can never blank the
 * listing. Content changes (text, category, photos) send the listing back
 * to the admin review queue; price/stock-only saves keep it live. The UI
 * says which of the two just happened instead of guessing.
 */
@Composable
fun EditProductScreen(
    productId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUser by SessionCache.user.collectAsState()

    var product by remember { mutableStateOf<Product?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    // Pre-filled from the product once it loads.
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES[0]) }
    var price by remember { mutableStateOf("") }
    var oldPrice by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("1") }
    var imageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var pastedUrl by remember { mutableStateOf("") }

    var uploadProgress by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var savedStatus by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(productId, refresh) {
        val (loaded, failed) = V2Client.fetchProductByIdOutcome(productId)
        if (loaded != null) {
            product = loaded
            title = loaded.title
            description = loaded.description
            category = loaded.category.takeIf { c -> CATEGORIES.contains(c) } ?: CATEGORIES[0]
            price = loaded.priceMinor.toString()
            oldPrice = loaded.oldPriceMinor?.toString() ?: ""
            stock = loaded.stockQuantity.toString()
            imageUrls = loaded.gallery
        } else {
            loadError = failed
        }
    }

    // Same multi-select picker + compression pipeline as the add wizard.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            scope.launch {
                uploading = true
                uploadError = ""
                val batch = uris.take((10 - imageUrls.size).coerceAtLeast(0))
                if (batch.isEmpty()) {
                    uploadError = "A listing keeps at most 10 photos."
                    uploading = false
                    return@launch
                }
                for ((i, uri) in batch.withIndex()) {
                    uploadProgress = "Uploading photo ${i + 1} of ${batch.size}…"
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }.getOrNull()
                    }
                    if (bytes == null || bytes.isEmpty()) {
                        uploadError = "Could not read photo ${i + 1}. The photos already on the listing are kept."
                        break
                    }
                    val compressed = withContext(Dispatchers.IO) { compressForUpload(bytes) }
                    val payload = compressed?.data ?: bytes
                    if (payload.size > 3 * 1024 * 1024) {
                        uploadError = "Photo ${i + 1} is larger than 3 MB — pick a smaller one"
                        break
                    }
                    val mime = if (compressed != null) "image/jpeg"
                    else context.contentResolver.getType(uri) ?: "image/jpeg"
                    val url = V2Client.uploadImage(payload, "edit-${imageUrls.size + i + 1}.jpg", mime)
                    if (url == null) {
                        uploadError = "Uploading photo ${i + 1} failed — check your connection. The listing is unchanged."
                        break
                    }
                    imageUrls = imageUrls + url
                }
                uploadProgress = ""
                uploading = false
            }
        }
    }

    if (loadError) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("We couldn't load this listing", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Check your connection and try again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { refresh += 1 }) { Text("Retry") }
            }
        }
        return
    }

    val p = product
    if (p == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ScottsTechXColors.BluePrimary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Edit listing", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // What this save will do to the listing's visibility — the seller
            // must not discover it only after the admin re-reviews.
            when (p.status) {
                "rejected" -> Surface(
                    color = ScottsTechXColors.ErrorRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("This listing was rejected", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ScottsTechXColors.ErrorRed)
                        Text(
                            p.rejectionReason ?: "The admin didn't give a reason.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Saving your changes resubmits it for review.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                "pending" -> Surface(
                    color = ScottsTechXColors.WarningAmber.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Awaiting admin review — saving changes keeps it in the queue.",
                        fontSize = 12.5.sp,
                        color = ScottsTechXColors.WarningAmber,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                "suspended" -> Surface(
                    color = ScottsTechXColors.ErrorRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "This listing is suspended. Contact support for details.",
                        fontSize = 12.5.sp,
                        color = ScottsTechXColors.ErrorRed,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Details", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            InputField(value = title, onValueChange = { title = it }, label = "Product name", placeholder = "e.g. Ankara Maxi Dress")
            Spacer(Modifier.height(10.dp))
            InputField(value = description, onValueChange = { description = it }, label = "Description", placeholder = "Condition, size, colour, delivery info…")

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

            Spacer(Modifier.height(14.dp))
            Text("Pricing & stock", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            InputField(value = price, onValueChange = { price = it }, label = "Price (UGX)", placeholder = "e.g. 85000", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(10.dp))
            InputField(value = oldPrice, onValueChange = { oldPrice = it }, label = "Old price (optional, UGX)", placeholder = "For showing a discount", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(10.dp))
            InputField(value = stock, onValueChange = { stock = it }, label = "Stock quantity", placeholder = "e.g. 10", keyboardType = KeyboardType.Number)

            Spacer(Modifier.height(14.dp))
            Text("Photos", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "The first photo is the main one; the rest show in the gallery.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = if (uploading) uploadProgress.ifBlank { "Uploading…" }
                    else if (imageUrls.size >= 10) "Photo limit reached (10)"
                    else if (imageUrls.isEmpty()) "Choose photos"
                    else "Add more photos",
                onClick = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !uploading && imageUrls.size < 10,
            )
            if (uploading) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(color = ScottsTechXColors.BluePrimary)
            }
            if (uploadError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(uploadError, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
            }
            if (imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(imageUrls) { index, url ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                ) {
                                    AsyncImage(
                                        model = V2Client.absoluteMediaUrl(url),
                                        contentDescription = "Photo ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        imageUrls = imageUrls.filterIndexed { i, _ -> i != index }
                                    },
                                ) {
                                    Text("Remove", fontSize = 12.sp, color = ScottsTechXColors.ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            InputField(value = pastedUrl, onValueChange = { pastedUrl = it }, label = "…or paste an image link", placeholder = "https://images.unsplash.com/photo-…")

            Spacer(Modifier.height(18.dp))
            error?.let {
                Text(it, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
            }

            if (saved) {
                // The backend decides whether this save went back to review
                // (content change) or stayed live (price/stock only) — show
                // its answer, not a guess.
                Text(
                    if (savedStatus == "pending")
                        "✅ Saved — sent back for admin review. You'll get a notification when it's decided."
                    else "✅ Saved — your live listing is updated.",
                    color = ScottsTechXColors.SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(text = "Back to inventory", onClick = onSaved)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    PrimaryButton(
                        text = if (saving) "Saving…" else "Save changes",
                        loading = saving,
                        enabled = !uploading,
                        onClick = {
                            val allImages = imageUrls +
                                (pastedUrl.trim()
                                    .takeIf { it.isNotBlank() && it !in imageUrls }
                                    ?.let { listOf(it) } ?: emptyList())
                            val priceValue = price.trim().toLongOrNull() ?: 0
                            error = when {
                                title.trim().length < 3 -> "Give the product a name (at least 3 characters)."
                                priceValue <= 0 -> "Set a price above zero (UGX)."
                                allImages.isEmpty() -> "Keep at least one photo — the marketplace won't accept a listing without a picture."
                                else -> null
                            }
                            if (error != null) return@PrimaryButton
                            saving = true
                            scope.launch {
                                val payload = NewProductPayload(
                                    title = title.trim(),
                                    description = description,
                                    category = category,
                                    brand = p.brand,
                                    priceMinor = priceValue,
                                    oldPriceMinor = oldPrice.trim().toLongOrNull(),
                                    stockQuantity = stock.trim().toIntOrNull() ?: 1,
                                    imageUrl = allImages.firstOrNull() ?: "",
                                    mediaUrls = allImages,
                                    // Round-trip the flash-deal fields: this form
                                    // doesn't manage them, and a full-form
                                    // PATCH must not silently drop them.
                                    location = p.location.ifBlank { UserPrefs.aiCity },
                                    isFlashDeal = p.isFlashDeal,
                                    discountPercent = p.discountPercent,
                                )
                                val updated = V2Client.updateSellerProduct(productId, payload)
                                if (updated != null) {
                                    saved = true
                                    savedStatus = updated.status
                                    product = updated
                                } else {
                                    error = "Saving failed — check your connection and try again. Nothing was changed."
                                }
                                saving = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
