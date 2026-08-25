package com.scottsx.app.ui.screens

import com.scottsx.app.data.domain.Role

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.data.AuthRepository
import com.scottsx.app.data.AuthResult
import com.scottsx.app.data.SellerExtras
import com.scottsx.app.ui.components.BrandLogo
import com.scottsx.app.ui.components.CinematicBackground
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sign Up / Registration screen.
 *
 * Slides up from the bottom (same sheet behaviour as LoginScreen),
 * leaving a slim peek of the cinematic background above so the
 * ScottsTechX brand identity carries through.
 *
 * @param role When [Role.SELLER], the form is extended with the
 *             extra fields required to register a merchant account:
 *             business name, business type, store location, NIN
 *             (national ID), years in business, and a short bio.
 *             [Role.BUYER] keeps the form short — just name, email,
 *             phone, password.
 */
@Composable
fun SignUpScreen(
    role: Role,
    onBack: () -> Unit,
    onSubmit: (Role) -> Unit,
    onSignInInstead: () -> Unit,
    onRoleMismatch: (Role) -> Unit = {},
    onVerificationPending: (email: String) -> Unit = {},
    authRepository: AuthRepository = AuthRepository(),
) {
    // Common fields
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // No client-side verification state — Firebase sends a real email link on
    // sign-up. The flow navigates to [VerifyEmailPendingScreen] which owns the
    // reload/resend logic. See: scottsx-app-android skill → "Firebase real
    // email-link verification (not 6-digit codes)".

    // Seller-only fields
    var businessName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf(BusinessType.Retail) }
    var storeLocation by remember { mutableStateOf("") }
    var nin by remember { mutableStateOf("") }
    var yearsInBusiness by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val slideOffset = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        slideOffset.animateTo(0f, tween(420, easing = EaseOutCubic))
    }

    Box(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.BackgroundDark)) {
        CinematicBackground()

        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ScottsTechXColors.OnDark,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() }
                    .padding(8.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = ScottsTechXColors.PanelLight,
                        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                    )
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .verticalScroll(rememberScrollState())
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 10.dp, bottom = 28.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(40.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ScottsTechXColors.PanelBorderHint),
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BrandLogo(
                        monogramSize = 64.dp,
                        showWordmark = true,
                        showTagline = false,
                        autoPlay = false,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Create your ",
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                    Text(
                        text = role.displayName,
                        color = ScottsTechXColors.AccentLink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                    )
                    Text(
                        text = " account",
                        color = ScottsTechXColors.OnLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (role == Role.SELLER)
                        "Tell us a bit about your business — it takes less than a minute."
                    else
                        "It takes less than a minute to join ScottsTechX.",
                    color = ScottsTechXColors.OnLightSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ============================ Common fields
                InputField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = "Full Name",
                    placeholder = "Enter your full name",
                    keyboardType = KeyboardType.Text,
                )
                Spacer(modifier = Modifier.height(12.dp))
                InputField(
                    value = email,
                    onValueChange = { email = it; errorMsg = null },
                    label = "Email Address",
                    placeholder = "Enter your email",
                    keyboardType = KeyboardType.Email,
                    isError = !isValidEmail(email) && email.isNotEmpty(),
                    errorMessage = if (!isValidEmail(email) && email.isNotEmpty()) "Enter a valid email address" else null,
                )
                Spacer(modifier = Modifier.height(8.dp))
                InputField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = null },
                    label = "Phone Number",
                    placeholder = "Enter your phone number",
                    keyboardType = KeyboardType.Phone,
                )

                // ============================ Seller-only fields
                AnimatedVisibility(visible = role == Role.SELLER) {
                    Column {
                        Spacer(modifier = Modifier.height(20.dp))
                        SellerSectionHeader("Tell us about your business")

                        Spacer(modifier = Modifier.height(12.dp))
                        InputField(
                            value = businessName,
                            onValueChange = { businessName = it; errorMsg = null },
                            label = "Business / Store Name",
                            placeholder = "e.g. Kampala Electronics",
                            keyboardType = KeyboardType.Text,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        BusinessTypePicker(selected = businessType, onSelect = { businessType = it })

                        Spacer(modifier = Modifier.height(12.dp))
                        InputField(
                            value = storeLocation,
                            onValueChange = { storeLocation = it; errorMsg = null },
                            label = "Store Location",
                            placeholder = "City / District",
                            keyboardType = KeyboardType.Text,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        InputField(
                            value = nin,
                            onValueChange = { nin = it; errorMsg = null },
                            label = "NIN / National ID",
                            placeholder = "Enter your NIN",
                            keyboardType = KeyboardType.Text,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        InputField(
                            value = yearsInBusiness,
                            onValueChange = { yearsInBusiness = it; errorMsg = null },
                            label = "Years in Business",
                            placeholder = "e.g. 3",
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        InputField(
                            value = bio,
                            onValueChange = { bio = it; errorMsg = null },
                            label = "Short Bio",
                            placeholder = "What do you sell? (a sentence or two)",
                            keyboardType = KeyboardType.Text,
                        )
                    }
                }

                // ============================ Password fields
                Spacer(modifier = Modifier.height(12.dp))
                InputField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = "Password",
                    placeholder = "Create a strong password",
                    isPassword = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                InputField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMsg = null },
                    label = "Confirm Password",
                    placeholder = "Re-enter your password",
                    isPassword = true,
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = friendlyError(errorMsg!!),
                        color = Color(0xFFB91C1C),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                PrimaryButton(
                    text = when {
                        loading -> ""
                        role == Role.SELLER -> "Create my Seller account"
                        else -> "Create my Buyer account"
                    },
                    loading = loading,
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedEmail = email.trim()
                        val trimmedPhone = phone.trim()
                        if (trimmedName.isEmpty() || trimmedEmail.isEmpty() || trimmedPhone.isEmpty()) {
                            errorMsg = "Please fill in every required field."
                            return@PrimaryButton
                        }
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                            errorMsg = "Please enter a valid email address."
                            return@PrimaryButton
                        }
                        if (password.length < 6) {
                            errorMsg = "Password must be at least 6 characters."
                            return@PrimaryButton
                        }
                        if (password != confirmPassword) {
                            errorMsg = "Passwords do not match."
                            return@PrimaryButton
                        }
                        if (role == Role.SELLER) {
                            if (businessName.trim().isEmpty()) {
                                errorMsg = "Please enter your business name."
                                return@PrimaryButton
                            }
                            if (storeLocation.trim().isEmpty()) {
                                errorMsg = "Please enter your store location."
                                return@PrimaryButton
                            }
                            if (nin.trim().length < 6) {
                                errorMsg = "Please enter a valid NIN / National ID."
                                return@PrimaryButton
                            }
                        }
                        errorMsg = null
                        loading = true
                        scope.launch {
                            val sellerExtras = if (role == Role.SELLER) {
                                SellerExtras(
                                    businessName = businessName.trim(),
                                    businessType = businessType.displayName,
                                    storeLocation = storeLocation.trim(),
                                    nin = nin.trim(),
                                    yearsInBusiness = yearsInBusiness.trim(),
                                    bio = bio.trim(),
                                )
                            } else null
                            val result = runCatching {
                                authRepository.signUp(
                                    email = trimmedEmail,
                                    password = password,
                                    displayName = trimmedName,
                                    phone = trimmedPhone,
                                    role = role,
                                    sellerExtras = sellerExtras,
                                )
                            }.getOrElse { AuthResult.Failure(it.message ?: "Sign-up failed") }
                            loading = false
                            when (result) {
                                is AuthResult.Success -> {
                                    try {
                                        onSubmit(result.role)
                                    } catch (t: Throwable) {
                                        android.util.Log.e("SignUpScreen", "post-signup nav failed", t)
                                        errorMsg = "Account created, but the dashboard failed to load: ${t.message ?: t.javaClass.simpleName}"
                                    }
                                }
                                is AuthResult.Failure -> errorMsg = result.message
                                is AuthResult.VerificationPending -> {
                                    onVerificationPending(result.email)
                                }
                                is AuthResult.RoleMismatch -> {
                                    errorMsg = "This email is already registered as a ${result.actual.displayName}. Switching you there."
                                    onRoleMismatch(result.actual)
                                }
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Already on ScottsTechX? ",
                        color = ScottsTechXColors.OnLightSecondary,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "Sign in",
                        color = ScottsTechXColors.AccentLink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onSignInInstead() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun friendlyError(raw: String): String = when {
    raw.contains("email address is already", ignoreCase = true) -> "An account with that email already exists."
    raw.contains("password", ignoreCase = true) && raw.contains("invalid", ignoreCase = true) -> "Password is too weak. Use 6+ chars."
    raw.contains("network", ignoreCase = true) -> "Network error. Check your connection."
    raw.isBlank() -> "Sign-up failed. Please try again."
    else -> raw
}

/**
 * Industry / business type options for a seller account.
 */
enum class BusinessType(val displayName: String) {
    Retail("Retail / Shop"),
    Wholesale("Wholesale"),
    Agriculture("Agriculture & Farming"),
    Manufacturing("Manufacturing"),
    Services("Services"),
    Other("Other"),
}

@Composable
private fun SellerSectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(28.dp)
                .background(ScottsTechXColors.BluePrimary),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title.uppercase(),
            color = ScottsTechXColors.BluePrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BusinessTypePicker(selected: BusinessType, onSelect: (BusinessType) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Business Type",
            color = ScottsTechXColors.OnLight,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        // Wrap the chip row so long labels don't overflow.
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BusinessType.values().forEach { type ->
                val isSelected = type == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) ScottsTechXColors.BluePrimary
                            else ScottsTechXColors.PanelInputLight,
                        )
                        .clickable { onSelect(type) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = type.displayName,
                        color = if (isSelected) ScottsTechXColors.OnDark else ScottsTechXColors.OnLight,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

private fun isValidEmail(s: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
}
