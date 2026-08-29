package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.SessionCache
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.components.SettingsRow
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Account settings — profile editing (name/phone/city/photo) + password. */
@Composable
fun AccountSettingsScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sessionUser by SessionCache.user.collectAsState()
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ── Profile (web parity: Settings → Profile tab) ──────────────────
    var displayName by remember(sessionUser?.displayName) { mutableStateOf(sessionUser?.displayName ?: "") }
    var phone by remember(sessionUser?.phone) { mutableStateOf(sessionUser?.phone ?: "") }
    var city by remember(sessionUser?.city) { mutableStateOf(sessionUser?.city ?: "") }
    var photoUrl by remember(sessionUser?.profilePhotoUrl) { mutableStateOf(sessionUser?.profilePhotoUrl ?: "") }
    var profileBusy by remember { mutableStateOf(false) }
    var profileMessage by remember { mutableStateOf<String?>(null) }
    var profileError by remember { mutableStateOf(false) }
    var avatarUploading by remember { mutableStateOf(false) }

    // Live refresh from the backend: a profile edited on the WEB (name,
    // phone, city, photo — same row) must appear here the moment this
    // screen opens, not only from the sign-in-time snapshot.
    LaunchedEffect(Unit) {
        val fresh = runCatching { V2Client.fetchUserProfile() }.getOrNull() ?: return@LaunchedEffect
        val u = com.scottsx.app.SessionCache.user.value
        if (u != null) {
            com.scottsx.app.SessionCache.updateUser(
                u.copy(
                    displayName = fresh.optString("displayName").ifBlank { u.displayName },
                    phone = fresh.optString("phone").ifBlank { u.phone },
                    city = fresh.optString("city").ifBlank { u.city },
                    profilePhotoUrl = fresh.optString("profilePhotoUrl").takeIf {
                        it.isNotBlank() && it != "null"
                    } ?: u.profilePhotoUrl,
                    emailVerified = fresh.optBoolean("emailVerified", u.emailVerified),
                ),
            )
        }
        com.scottsx.app.data.Session.adoptSession(
            token = com.scottsx.app.data.Session.tokenOrNull() ?: return@LaunchedEffect,
            userId = fresh.optString("id").takeIf { it.isNotBlank() },
            role = if (fresh.optString("role").equals("seller", true))
                com.scottsx.app.data.domain.Role.SELLER else com.scottsx.app.data.domain.Role.BUYER,
            displayName = fresh.optString("displayName"),
            email = fresh.optString("email"),
            avatarUrl = fresh.optString("profilePhotoUrl").takeIf { it.isNotBlank() && it != "null" },
            storeLocation = fresh.optString("city"),
        )
    }

    val pickAvatar = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            avatarUploading = true
            scope.launch {
                val uploaded = runCatching {
                    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read image")
                    var mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                    var bytes = raw
                    if (bytes.size > 3 * 1024 * 1024 ||
                        mime !in setOf("image/jpeg", "image/png", "image/webp")
                    ) {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        if (bmp != null) {
                            val maxSide = 800
                            val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
                            val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                                bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
                                (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
                            val bos = java.io.ByteArrayOutputStream()
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
                            bytes = bos.toByteArray()
                            mime = "image/jpeg"
                        }
                    }
                    val ext = when { mime.endsWith("png") -> "png"; mime.endsWith("webp") -> "webp"; else -> "jpg" }
                    V2Client.uploadImage(bytes, mime, "avatar-${System.currentTimeMillis()}.$ext")
                        ?: error("Upload failed")
                }
                avatarUploading = false
                uploaded.onSuccess { url ->
                    photoUrl = url
                    val ok = V2Client.updateUserProfile(
                        org.json.JSONObject().put("profilePhotoUrl", url)
                    )
                    if (ok && sessionUser != null) {
                        SessionCache.updateUser(sessionUser!!.copy(profilePhotoUrl = url))
                        // Mirror into the role/AI session cache so the new
                        // avatar shows on the dashboard chrome immediately.
                        com.scottsx.app.data.domain.SessionCache.set(
                            role = com.scottsx.app.data.Session.roleOrNull() ?: com.scottsx.app.data.domain.Role.BUYER,
                            displayName = com.scottsx.app.data.Session.displayNameOrEmpty(),
                            email = com.scottsx.app.data.Session.emailOrEmpty(),
                            userId = com.scottsx.app.data.Session.userIdOrNull(),
                            avatarUrl = url,
                        )
                    }
                    profileMessage = if (ok) "Photo updated." else "Uploaded but save failed — press Save profile."
                    profileError = !ok
                }.onFailure {
                    profileMessage = "Photo upload failed — retry or paste an image URL."
                    profileError = true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Scrolling form: needs the status bar AND the gesture pill kept
            // clear, otherwise the back arrow hides under the clock and the
            // submit button under the navigation bar.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SettingsRow(title = "", icon = Icons.Filled.KeyboardArrowLeft, onClick = onBack)
        Text("Account settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Signed in as ${sessionUser?.email ?: "-"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        // ── Public profile — same fields the web's Settings → Profile tab
        //    edits (display name, phone, city, photo). ────────────────
        Text("Profile", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .then(
                        if (photoUrl.isBlank()) Modifier.background(ScottsTechXColors.BluePrimary.copy(alpha = 0.14f))
                        else Modifier
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (photoUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile photo",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
                    )
                } else {
                    Text(
                        (displayName.firstOrNull()?.uppercase() ?: "U").toString(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = ScottsTechXColors.BluePrimary,
                    )
                }
            }
            Spacer(Modifier.height(0.dp).padding(start = 0.dp))
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(start = 14.dp),
            ) {
                PrimaryButton(
                    text = if (avatarUploading) "Uploading…" else "Upload photo",
                    loading = avatarUploading,
                    enabled = !avatarUploading,
                    onClick = {
                        pickAvatar.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
                if (photoUrl.isNotBlank()) {
                    Text(
                        "Remove photo",
                        color = Color(0xFFB91C1C),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable {
                                photoUrl = ""
                                scope.launch {
                                    val ok = V2Client.updateUserProfile(
                                        org.json.JSONObject().put("profilePhotoUrl", "")
                                    )
                                    if (ok && sessionUser != null) {
                                        SessionCache.updateUser(sessionUser!!.copy(profilePhotoUrl = null))
                                    }
                                }
                            },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InputField(value = displayName, onValueChange = { displayName = it }, label = "Display name")
        Spacer(Modifier.height(10.dp))
        InputField(value = phone, onValueChange = { phone = it }, label = "Phone", placeholder = "+256 …")
        Spacer(Modifier.height(10.dp))
        InputField(value = city, onValueChange = { city = it }, label = "City / town", placeholder = "Helps sort nearby stores by distance")
        Spacer(Modifier.height(10.dp))
        InputField(value = photoUrl, onValueChange = { photoUrl = it }, label = "…or paste an image URL", placeholder = "https://…")
        Spacer(Modifier.height(8.dp))
        profileMessage?.let {
            Text(it, color = if (profileError) ScottsTechXColors.ErrorRed else ScottsTechXColors.SuccessGreen, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        PrimaryButton(
            text = "Save profile",
            loading = profileBusy,
            enabled = displayName.isNotBlank() && !profileBusy,
            onClick = {
                profileBusy = true
                profileMessage = null
                scope.launch {
                    val ok = V2Client.updateUserProfile(
                        org.json.JSONObject().apply {
                            put("displayName", displayName.trim())
                            put("phone", phone.trim())
                            put("city", city.trim())
                            put("profilePhotoUrl", photoUrl.trim())
                        },
                    )
                    if (ok && sessionUser != null) {
                        SessionCache.updateUser(
                            sessionUser!!.copy(
                                displayName = displayName.trim(),
                                phone = phone.trim(),
                                city = city.trim(),
                                profilePhotoUrl = photoUrl.trim().ifBlank { null },
                            ),
                        )
                    }
                    profileMessage = if (ok) "Profile saved." else "Could not save right now — try again."
                    profileError = !ok
                    profileBusy = false
                }
            },
        )
        Spacer(Modifier.height(22.dp))

        Text("Change password", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        InputField(value = oldPassword, onValueChange = { oldPassword = it }, label = "Current password", isPassword = true)
        Spacer(Modifier.height(12.dp))
        InputField(value = newPassword, onValueChange = { newPassword = it }, label = "New password", isPassword = true, placeholder = "min 6 characters")
        Spacer(Modifier.height(8.dp))
        message?.let {
            Text(it, color = if (isError) ScottsTechXColors.ErrorRed else ScottsTechXColors.SuccessGreen, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        PrimaryButton(
            text = "Update password",
            loading = busy,
            enabled = oldPassword.isNotBlank() && newPassword.length >= 6,
            onClick = {
                busy = true
                message = null
                scope.launch {
                    val ok = V2Client.changePassword(oldPassword, newPassword)
                    message = if (ok) "Password updated." else "Could not update — check your current password."
                    isError = !ok
                    if (ok) {
                        oldPassword = ""
                        newPassword = ""
                    }
                    busy = false
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Security: local accounts use bcrypt hashes; Firebase accounts are verified via email links.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
