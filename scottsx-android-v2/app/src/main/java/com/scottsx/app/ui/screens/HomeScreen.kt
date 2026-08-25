package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.scottsx.app.data.domain.BuyerProfile
import com.scottsx.app.data.domain.Role
import com.scottsx.app.ui.components.BrandLogo
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Stage-1 placeholder home screen. Used by the email / Google
 * sign-in landings so the Stage-2 Buyer Dashboard can pick up
 * the user's profile via [onContinue].
 *
 * When [onContinue] is supplied the screen auto-redirects to
 * the buyer dashboard after a brief moment so the user lands in
 * the marketplace without an extra tap.
 */
@Composable
fun HomeScreen(
    role: Role,
    onContinue: (BuyerProfile) -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    val user = FirebaseAuth.getInstance().currentUser
    val profile = BuyerProfile(
        uid = user?.uid ?: "u-anon",
        displayName = user?.displayName ?: user?.email?.substringBefore("@") ?: "Buyer",
        email = user?.email ?: "",
    )

    LaunchedEffect(profile.uid) {
        kotlinx.coroutines.delay(900)
        onContinue(profile)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark)
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            BrandLogo(
                monogramSize = 80.dp,
                showWordmark = true,
                showTagline = false,
                autoPlay = false,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "WELCOME",
                color = ScottsTechXColors.BluePrimaryLight,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 4.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You're in.",
                color = ScottsTechXColors.OnDark,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Signed in as ${role.displayName}",
                color = ScottsTechXColors.OnDarkSecondary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "Loading your marketplace dashboard...",
                color = ScottsTechXColors.OnDarkMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign out",
                    color = ScottsTechXColors.BluePrimaryLight,
                )
            }
        }
    }
}
