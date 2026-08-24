package com.scottsx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scottsx.app.ui.components.BrandLogo
import com.scottsx.app.ui.components.CinematicBackground
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay

/**
 * Splash / Launch screen — shown for ~1.4 seconds on cold-start so
 * the OS warm-up does not produce a blank black frame. The brand
 * monogram animates in (it auto-plays inside BrandLogo); after the
 * delay we hand control to Onboarding.
 */
@Composable
fun SplashScreen(
    onContinue: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(1500)
        onContinue()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        CinematicBackground()
        BrandLogo(
            monogramSize = 140.dp,
            showWordmark = true,
            showTagline = true,
            autoPlay = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    }
}