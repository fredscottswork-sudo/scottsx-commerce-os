package com.scottsx.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.domain.ProductCategory
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.data.domain.SessionCache
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import java.util.UUID
import com.scottsx.app.ui.components.statusBarSpacer

/**
 * Add a new product. Three steps:
 *  1. Basics  - name, description, category
 *  2. Pricing - price, discount, stock
 *  3. Review  - summary card + Save button
 *
 * State is held in local [remember] fields; on Save, prints to log
 * (Stage 3.5) and pops back. The shape is identical to the existing
 * data flow so wiring a real `SellerDataSource.addProduct()` is a
 * one-liner.
 */
@Composable
fun AddProductScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var brandText by remember { mutableStateOf("") }
    // Stage 5.x AI-generated fields (when seller adds a photo URL)
    var aiSuggestedName by remember { mutableStateOf<String?>(null) }
    var aiSuggestedDesc by remember { mutableStateOf<String?>(null) }
    var aiSuggestedCategory by remember { mutableStateOf<ProductCategory?>(null) }
    var aiGenerating by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(ProductCategory.All) }
    var priceText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    var stockText by remember { mutableStateOf("10") }
    var imageUrls by remember { mutableStateOf(listOf<String>()) }
    var newImageUrl by remember { mutableStateOf("") }
    var locationLabel by remember { mutableStateOf("Kampala") }
    var publishStatus by remember { mutableStateOf<String?>(null) }
    var publishResult by remember { mutableStateOf<com.scottsx.app.data.ProductUploadSafety.Result?>(null) }
    val scope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var pickError by remember { mutableStateOf<String?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }

    // Android 13+ Photo Picker — no runtime permission required.
    // Returns a content:// URI we then upload to Firebase Storage and
    // replace with the public download URL.
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) {
            pickError = null
            return@rememberLauncherForActivityResult
        }
        pickError = null
        uploadingImage = true
        scope.launch {
            val uploaded = runCatching {
                // Real backend route: multipart POST /api/v1/uploads/images
                // (the old signed-URL flow called a route that does not
                // exist, so gallery photos could never reach the server).
                // Compress first — the backend accepts up to 8 MB and the
                // route notes explicitly that the app compresses.
                val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read selected image")
                var mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                var bytes = raw
                if (bytes.size > 3 * 1024 * 1024 || mime !in setOf("image/jpeg", "image/png", "image/webp")) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    if (bmp != null) {
                        val maxSide = 1600
                        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
                        val scaled = if (scale < 1f)
                            android.graphics.Bitmap.createScaledBitmap(
                                bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
                                (bmp.height * scale).toInt().coerceAtLeast(1), true)
                        else bmp
                        val bos = java.io.ByteArrayOutputStream()
                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
                        bytes = bos.toByteArray()
                        mime = "image/jpeg"
                    }
                }
                val ext = when { mime.endsWith("png") -> "png"; mime.endsWith("webp") -> "webp"; else -> "jpg" }
                val filename = "${UUID.randomUUID()}.$ext"
                V2Client.uploadImage(bytes, mime, filename)
                    ?: throw IllegalStateException(
                        "Image upload failed — sign in and retry (the server accepts JPEG/PNG/WEBP up to 8 MB)."
                    )
            }
            uploadingImage = false
            uploaded.fold(
                onSuccess = { url ->
                    imageUrls = imageUrls + url
                    publishStatus = "Image uploaded."
                },
                onFailure = { t ->
                    pickError = "Upload failed: ${t.message ?: t.javaClass.simpleName}"
                    android.util.Log.w("AddProductScreen", "image upload failed", t)
                },
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.PanelLight).statusBarSpacer()) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text("Add Product", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Step ${step + 1} of 3", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
        // Stepper indicator
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (0..2).forEach { i ->
                val active = i <= step
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) ScottsTechXColors.BluePrimary else ScottsTechXColors.PanelInputLight),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when (step) {
                0 -> {
                    Text("Basics", color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    InputField(value = name, onValueChange = { name = it }, label = "Product name")
                    Spacer(Modifier.height(10.dp))
                    InputField(value = description, onValueChange = { description = it }, label = "Description")
                    Spacer(Modifier.height(10.dp))
                    InputField(value = brandText, onValueChange = { brandText = it }, label = "Brand (optional)")
                    Spacer(Modifier.height(10.dp))
                    // Stage 5.x: AI helper. Seller enters a short hint (or
                    // uses the first photo URL as context), and we fill
                    // title, description, and category automatically.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                if (imageUrls.isEmpty()) return@OutlinedButton
                                aiGenerating = true
                                // Heuristic AI suggestion based on the image
                                // filename / URL. The real backend would
                                // POST /api/v1/ai/v2/generate-product with the
                                // image bytes + hint and return structured fields.
                                val first = imageUrls.first().lowercase()
                                aiSuggestedName = when {
                                    "phone" in first || "iphone" in first || "samsung" in first -> "Premium Smartphone — Like New"
                                    "laptop" in first || "macbook" in first -> "High-Performance Laptop"
                                    "shoe" in first || "sneaker" in first || "nike" in first -> "Stylish Sneakers — Premium Quality"
                                    "dress" in first || "ankara" in first || "kitenge" in first -> "Traditional African Outfit"
                                    "rice" in first || "food" in first -> "Quality Food Item"
                                    "headphone" in first -> "Wireless Headphones — Premium Audio"
                                    "lipstick" in first || "makeup" in first -> "Premium Beauty Product"
                                    "watch" in first -> "Elegant Timepiece"
                                    else -> "Quality Product from a Verified Seller"
                                }
                                aiSuggestedDesc = "Carefully sourced and inspected before listing. " +
                                    "Buy with confidence from a trusted ScottsTechX seller. " +
                                    "Free local delivery in Kampala, fast shipping nationwide."
                                aiSuggestedCategory = when {
                                    "phone" in first || "iphone" in first || "samsung" in first ||
                                    "laptop" in first || "macbook" in first ||
                                    "headphone" in first || "watch" in first -> ProductCategory.Electronics
                                    "shoe" in first || "sneaker" in first || "nike" in first -> ProductCategory.Sports
                                    "dress" in first || "ankara" in first || "kitenge" in first -> ProductCategory.Fashion
                                    "lipstick" in first || "makeup" in first -> ProductCategory.Beauty
                                    "rice" in first -> ProductCategory.Groceries
                                    else -> ProductCategory.Electronics
                                }
                                aiGenerating = false
                            },
                            modifier = Modifier.weight(1f),
                            enabled = imageUrls.isNotEmpty() && !aiGenerating,
                        ) {
                            if (aiGenerating) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Generating...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("AI suggest from photo", fontSize = 12.sp)
                            }
                        }
                    }
                    if (aiSuggestedName != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✨ AI suggestion ready", color = ScottsTechXColors.BluePrimary, fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            androidx.compose.material3.TextButton(onClick = {
                                aiSuggestedName = null
                                aiSuggestedDesc = null
                                aiSuggestedCategory = null
                            }) {
                                Text("Dismiss", fontSize = 11.sp)
                            }
                            androidx.compose.material3.TextButton(onClick = {
                                aiSuggestedName?.let { name = it }
                                aiSuggestedDesc?.let { description = it }
                                aiSuggestedCategory?.let { category = it }
                                aiSuggestedName = null
                                aiSuggestedDesc = null
                                aiSuggestedCategory = null
                            }) {
                                Text("Apply", fontSize = 11.sp)
                            }
                        }
                        // Show preview
                        Text(
                            text = "→ ${aiSuggestedName.orEmpty()}",
                            color = ScottsTechXColors.OnPanel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = aiSuggestedDesc.orEmpty().take(140) + if ((aiSuggestedDesc?.length ?: 0) > 140) "..." else "",
                            color = ScottsTechXColors.OnPanelSecondary,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = "→ ${aiSuggestedCategory?.displayName ?: ""}",
                            color = ScottsTechXColors.BluePrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Category", color = ScottsTechXColors.OnPanelSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ProductCategory.values().toList()) { c ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (c == category) ScottsTechXColors.BluePrimary else ScottsTechXColors.PanelInputLight,
                                    )
                                    .clickable { category = c }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = c.displayName,
                                    color = if (c == category) Color.White else ScottsTechXColors.OnPanel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Location (where the item ships from)", color = ScottsTechXColors.OnPanelSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Kampala", "Entebbe", "Jinja", "Gulu", "Mbarara", "Arua")) { loc ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (locationLabel == loc) ScottsTechXColors.BluePrimary else ScottsTechXColors.PanelInputLight,
                                    )
                                    .clickable { locationLabel = loc }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = loc,
                                    color = if (locationLabel == loc) Color.White else ScottsTechXColors.OnPanel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Text("Pricing & Stock", color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    InputField(value = priceText, onValueChange = { priceText = it.filter { c -> c.isDigit() } }, label = "Price (UGX)", keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(10.dp))
                    InputField(value = discountText, onValueChange = { discountText = it.filter { c -> c.isDigit() } }, label = "Discount %", keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(10.dp))
                    InputField(value = stockText, onValueChange = { stockText = it.filter { c -> c.isDigit() } }, label = "Stock quantity", keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(16.dp))
                    Text("Product images", color = ScottsTechXColors.OnPanelSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (imageUrls.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(imageUrls) { url ->
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ScottsTechXColors.PanelInputLight),
                                ) {
                                    coil.compose.AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.scottsx.app.ui.components.InputField(
                            value = newImageUrl,
                            onValueChange = { newImageUrl = it },
                            label = "Image URL",
                            placeholder = "Paste image URL (Firebase Storage)",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        com.scottsx.app.ui.components.PrimaryButton(
                            text = "Add",
                            enabled = newImageUrl.isNotBlank(),
                            onClick = {
                                if (newImageUrl.isNotBlank()) {
                                    imageUrls = imageUrls + newImageUrl.trim()
                                    newImageUrl = ""
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    com.scottsx.app.ui.components.PrimaryButton(
                        text = if (uploadingImage) "Uploading..." else "Pick photo from gallery",
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        loading = uploadingImage,
                        enabled = !uploadingImage,
                        onClick = {
                            pickImageLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts
                                        .PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (pickError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = pickError ?: "",
                            color = Color(0xFFD32F2F),
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = imageUrls.size.toString() + " image(s) — uploaded to Firebase Storage",
                        color = ScottsTechXColors.OnPanelSecondary,
                        fontSize = 11.sp,
                    )
                }
                2 -> {
                    Text("Review", color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(14.dp),
                    ) {
                        Column {
                            ReviewRow("Name", name.ifBlank { "(no name)" })
                            ReviewRow("Description", description.ifBlank { "(no description)" })
                            ReviewRow("Category", category.displayName)
                            ReviewRow("Price", "UGX ${priceText.ifBlank { "0" }}")
                            ReviewRow("Discount", "$discountText%")
                            ReviewRow("Stock", stockText)
                            ReviewRow("Images", "${imageUrls.size}")
                            ReviewRow("Location", locationLabel)
                        }
                    }
                }
                3 -> {
                    Text("Safety Check", color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    val safety = remember(name, description, category.displayName, priceText, stockText, imageUrls.size, locationLabel) {
                        com.scottsx.app.data.ProductUploadSafety.check(
                            com.scottsx.app.data.ProductUploadSafety.Draft(
                                name = name,
                                description = description,
                                categoryName = category.displayName,
                                priceUgx = priceText.toLongOrNull() ?: 0L,
                                stock = stockText.toIntOrNull() ?: 0,
                                imageCount = imageUrls.size,
                                locationLabel = locationLabel,
                            ),
                        )
                    }
                    androidx.compose.runtime.LaunchedEffect(safety) {
                        publishResult = safety
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = when {
                                safety.errors.isNotEmpty() -> Color(0xFFFFEBEE)
                                safety.requiresAdminReview -> Color(0xFFFFF8E1)
                                else -> Color(0xFFE8F5E9)
                            },
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = when {
                                    safety.errors.isNotEmpty() -> "X Fix ${safety.errors.size} issue(s) before publishing"
                                    safety.requiresAdminReview -> "Pending admin review after publish"
                                    else -> "Ready to publish"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = when {
                                    safety.errors.isNotEmpty() -> Color(0xFFB71C1C)
                                    safety.requiresAdminReview -> Color(0xFFE65100)
                                    else -> Color(0xFF1B5E20)
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Draft -> Checking -> ${if (safety.requiresAdminReview) "Admin Review" else "Approved"} -> Live",
                                fontSize = 11.sp,
                                color = ScottsTechXColors.OnLightSecondary,
                            )
                        }
                    }
                    if (safety.issues.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                safety.issues.forEach { issue ->
                                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            text = when (issue.severity) {
                                                com.scottsx.app.data.ProductUploadSafety.Severity.BLOCK -> "X"
                                                com.scottsx.app.data.ProductUploadSafety.Severity.WARN -> "!"
                                                else -> "i"
                                            },
                                            color = when (issue.severity) {
                                                com.scottsx.app.data.ProductUploadSafety.Severity.BLOCK -> Color(0xFFD32F2F)
                                                com.scottsx.app.data.ProductUploadSafety.Severity.WARN -> Color(0xFFE65100)
                                                else -> Color(0xFF1976D2)
                                            },
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(28.dp),
                                        )
                                        Text(issue.message, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Bottom nav
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(ScottsTechXColors.PanelInputLight)
                        .clickable { step-- }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Back", color = ScottsTechXColors.OnPanel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
            }
            PrimaryButton(
                text = if (step == 3) "Save Product" else "Next",
                enabled = !isUploading,
                loading = isUploading,
                onClick = {
                    if (step == 3) {
                        // Run safety check before allowing publish
                        val safety = com.scottsx.app.data.ProductUploadSafety.check(
                            com.scottsx.app.data.ProductUploadSafety.Draft(
                                name = name,
                                description = description,
                                categoryName = category.displayName,
                                priceUgx = priceText.toLongOrNull() ?: 0L,
                                stock = stockText.toIntOrNull() ?: 0,
                                imageCount = imageUrls.size,
                                locationLabel = locationLabel,
                            ),
                        )
                        publishResult = safety
                        if (!safety.isPublishable) {
                            android.widget.Toast.makeText(
                                ctx, "Fix ${safety.errors.size} blocking issue(s) before publishing", android.widget.Toast.LENGTH_LONG,
                            ).show()
                            return@PrimaryButton
                        }
                        // Validation: name and price are required
                        if (name.isBlank() || priceText.isBlank()) {
                            android.widget.Toast.makeText(
                                ctx, "Name and price are required", android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@PrimaryButton
                        }
                        val priceUgx = priceText.toLongOrNull() ?: 0L
                        val stock = stockText.toIntOrNull() ?: 0
                        val discount = discountText.toIntOrNull() ?: 0
                        val firstImage = imageUrls.firstOrNull()
                        val productName = name.trim()
                        val productDesc = description.trim()
                        scope.launch {
                            isUploading = true
                            try {
                                // Real backend: POST /api/v1/seller/products
                                // (the old /api/v1/products/v2/create route
                                // never existed — every publish was a 404).
                                val newId = V2Client.createProduct(
                                    title = productName,
                                    priceMinor = priceUgx,
                                    description = productDesc,
                                    currency = "UGX",
                                    stock = stock,
                                    category = category.name,
                                    brand = brandText.trim(),
                                    imageUrl = firstImage,
                                    mediaUrls = imageUrls,
                                    location = locationLabel,
                                    discountPercent = discount,
                                )
                                if (newId != null) {
                                    android.util.Log.i("AddProduct", "created id=$newId")
                                    V2Client.recordSignal("category", category.name)
                                    android.widget.Toast.makeText(
                                        ctx, "Product saved", android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                    onSaved()
                                } else {
                                    android.widget.Toast.makeText(
                                        ctx, "Save failed — check connection", android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            } catch (t: Throwable) {
                                android.util.Log.w("AddProduct", "save failed", t)
                                android.widget.Toast.makeText(
                                    ctx, "Save error: ${t.message}", android.widget.Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                isUploading = false
                            }
                        }
                    } else {
                        if (step == 0 && name.isBlank()) {
                            android.widget.Toast.makeText(
                                ctx, "Enter a product name", android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@PrimaryButton
                        }
                        step++
                    }
                },
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

@Composable
private fun StepBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(ScottsTechXColors.PanelInputLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ScottsTechXColors.BluePrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = ScottsTechXColors.OnLightSecondary, fontSize = 12.sp, modifier = Modifier.width(110.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, color = ScottsTechXColors.OnLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}