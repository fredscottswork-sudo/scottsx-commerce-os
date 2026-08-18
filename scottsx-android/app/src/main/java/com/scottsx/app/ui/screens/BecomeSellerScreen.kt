package com.scottsx.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.scottsx.app.data.firebase.FirebaseBridge
import com.scottsx.app.data.firebase.FirebaseAuthRepository
import com.scottsx.app.data.remote.V2Client
import com.scottsx.app.ui.components.InputField
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/** Buyer → Seller upgrade flow. Requires a verified email. */
@Composable
fun BecomeSellerScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var storeName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firebaseEmailVerified = remember { FirebaseBridge.isEmailVerified() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Become a seller", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Open your own store on ScottsTechX. List products, chat with buyers and manage your dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Text("✓ Verified email: ${if (firebaseEmailVerified) "yes" else "no — verify your email first"}", color = if (firebaseEmailVerified) ScottsTechXColors.SuccessGreen else ScottsTechXColors.WarningAmber, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        InputField(value = storeName, onValueChange = { storeName = it }, label = "Store name", placeholder = "e.g. Tech Hub Uganda")
        Spacer(Modifier.height(12.dp))
        InputField(value = description, onValueChange = { description = it }, label = "Store description", placeholder = "What do you sell?")
        Spacer(Modifier.height(10.dp))
        error?.let {
            Text(it, color = ScottsTechXColors.ErrorRed, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }

        PrimaryButton(
            text = "Upgrade to seller",
            loading = busy,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    // Firebase path first (requires a verified Firebase account)…
                    var upgraded = false
                    if (firebaseEmailVerified && FirebaseBridge.currentUser() != null) {
                        upgraded = FirebaseAuthRepository.upgradeToSeller()
                    }
                    // …otherwise the local path: POST /auth/upgrade-to-seller.
                    if (!upgraded) {
                        val local = V2Client.upgradeToSellerLocal()
                        if (local != null) {
                            val current = SessionCache.user.value
                            SessionCache.save(
                                local.token,
                                com.scottsx.app.CurrentUser(
                                    id = local.user.id,
                                    email = local.user.email,
                                    displayName = local.user.displayName,
                                    phone = local.user.phone,
                                    role = "seller",
                                    emailVerified = local.user.emailVerified,
                                    profilePhotoUrl = local.user.profilePhotoUrl,
                                    city = local.user.city,
                                ),
                            )
                            upgraded = true
                        }
                    }
                    if (upgraded) {
                        // Persist store profile details entered here.
                        val current = SessionCache.user.value ?: return@launch
                        V2Client.updateStoreSettings(
                            com.scottsx.app.data.domain.StoreSettings(
                                storeName = storeName.ifBlank { current.displayName },
                                storeDescription = description,
                            ),
                        )
                        onDone()
                    } else {
                        error = "Could not upgrade — please verify your email and try again."
                    }
                    busy = false
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Tip: verify your email to unlock the seller upgrade on Firebase accounts.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
