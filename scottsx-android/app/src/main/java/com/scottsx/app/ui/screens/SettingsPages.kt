package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.SettingsScaffold
import com.scottsx.app.ui.components.SettingsSectionHeader
import com.scottsx.app.ui.components.SettingsBlankHint
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Generic CMS viewer used for Terms / Privacy / About / Help / Contact /
 * Buyer Protection. Loads the body via GET /api/v1/cms/:slug.
 */
@Composable
fun CmsScreen(
    slug: String,
    title: String,
    onBack: () -> Unit,
) {
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(slug) {
        scope.launch {
            loading = true
            content = try {
                V2Client.fetchCms(slug)?.optString("body")
            } catch (e: Exception) { null }
            loading = false
        }
    }
    SettingsScaffold(title = title, onBack = onBack) {
        if (loading) {
            SettingsBlankHint("Loading...")
        } else if (content.isNullOrBlank()) {
            SettingsBlankHint("Content not available right now.")
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Text(
                    content!!,
                    color = ScottsTechXColors.OnLight,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

/**
 * Account / profile editor. Save button calls
 * PATCH /api/v1/user/profile and updates the displayed fields.
 */
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit = {},
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }
    var currency by remember { mutableStateOf("UGX") }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        V2Client.fetchUserProfile()?.let { it ->
            firstName = it.optString("displayName").split(" ").firstOrNull().orEmpty()
            lastName = it.optString("displayName").split(" ").drop(1).joinToString(" ")
            email = it.optString("email")
            phone = it.optString("phone")
            bio = it.optString("bio")
            gender = it.optString("gender")
            dob = it.optString("dateOfBirth")
            language = it.optString("language", "en")
            currency = it.optString("currency", "UGX")
            loaded = true
        }
    }

    SettingsScaffold(title = "Account", onBack = onBack) {
        SettingsSectionHeader("Personal info")
        Spacer(Modifier.height(6.dp))
        FieldRow("First name", firstName) { firstName = it }
        FieldRow("Last name", lastName) { lastName = it }
        FieldRow("Email", email) { email = it }
        FieldRow("Phone", phone) { phone = it }
        FieldRow("Bio", bio, lines = 3) { bio = it }
        FieldRow("Gender", gender, hint = "female/male/other/prefer_not_say") { gender = it }
        FieldRow("Date of birth", dob, hint = "YYYY-MM-DD") { dob = it }

        Spacer(Modifier.height(12.dp))
        SettingsSectionHeader("Preferences")
        Spacer(Modifier.height(6.dp))
        FieldRow("Language", language) { language = it }
        FieldRow("Currency", currency) { currency = it }

        Spacer(Modifier.height(20.dp))
        SaveButton(saving = saving, onSave = {
            saving = true
            scope.launch {
                val patch = JSONObject()
                    .put("displayName", "$firstName $lastName".trim())
                    .put("phone", phone)
                    .put("bio", bio)
                    .put("gender", gender)
                    .put("dateOfBirth", dob)
                    .put("language", language)
                    .put("currency", currency)
                val ok = V2Client.updateUserProfile(patch)
                saving = false
                toast = if (ok) "Saved!" else "Failed to save"
            }
        })

        toast?.let {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981))
                    .padding(12.dp),
            ) {
                Text(it, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Single-row text field for settings screens.
 */
@Composable
fun FieldRow(
    label: String,
    value: String,
    hint: String? = null,
    lines: Int = 1,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            color = ScottsTechXColors.OnLightSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    hint ?: "Enter $label",
                    color = ScottsTechXColors.OnLightSecondary.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                )
            } else {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = ScottsTechXColors.OnLight,
                        fontSize = 14.sp,
                    ),
                    singleLine = lines == 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun SaveButton(saving: Boolean, onSave: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ScottsTechXColors.BluePrimary)
            .clickable(enabled = !saving, onClick = onSave),
        contentAlignment = Alignment.Center,
    ) {
        if (saving) {
            Text(
                "Saving...",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        } else {
            Text(
                "Save",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

