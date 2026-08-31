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
    onFinished: () -> Unit,
) {
    LaunchedEffect(Unit) {
        // The brand beat is now the marketplace's warm-up window: while
        // the lockup plays we pull the live catalog + cart + wishlist so
        // the home screen renders INSTANTLY instead of a second spinner.
        // Fire-and-forget warm: the fetches race the brand beat but the
        // splash NEVER waits on the network — a slow connection shows the
        // normal per-screen shimmers instead of a stuck launch screen.
        com.scottsx.app.data.LiveMarketplace.warm()
        com.scottsx.app.data.CartStore.warm()
        delay(1000)   // was 1500ms of pure waiting — now the warm window
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.BackgroundDark)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        CinematicBackground()
        // The transparent lockup (RGBA PNG) — it carries the full
        // wordmark once, so nothing else is printed over it.
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(
                com.scottsx.app.R.drawable.brand_lockup,
            ),
            contentDescription = "ScottsTechX",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
        )
    }
}