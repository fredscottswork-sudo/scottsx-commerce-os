package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.components.BrandLogo
import com.scottsx.app.ui.components.CinematicBackground
import com.scottsx.app.ui.components.PrimaryButton
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Screen — Alternative Login. Reached when the user taps "Skip"
 * during onboarding, or from the Login screen back button.
 *
 * Motivational copy keeps the same voice as the onboarding flow.
 */
@Composable
fun AlternativeLoginScreen(
    onContinueWithEmail: () -> Unit,
    onContinueWithPhone: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(ScottsTechXColors.BackgroundDark)) {
        CinematicBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandLogo(
                    monogramSize = 40.dp,
                    showWordmark = true,
                    showTagline = false,
                    autoPlay = false,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "ALMOST THERE",
                    color = ScottsTechXColors.BluePrimaryLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 4.sp,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "BUY.\nSELL.\nCONNECT.",
                    color = ScottsTechXColors.OnDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 58.sp,
                    lineHeight = 62.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-1.0).sp,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Choose how you want to step into ScottsTechX — by email or by phone.",
                    color = ScottsTechXColors.OnDarkSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            PrimaryButton(
                text = "Continue with Email",
                onClick = onContinueWithEmail,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            Spacer(modifier = Modifier.height(14.dp))

            PrimaryButton(
                text = "Continue with Phone",
                onClick = onContinueWithPhone,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}