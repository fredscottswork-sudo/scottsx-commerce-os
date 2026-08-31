package com.scottsx.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.scottsx.app.R
import com.scottsx.app.ui.components.CinematicBackground
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay

/**
 * Splash / Launch screen — the STX logo moment on cold-start.
 *
 * The brand-new ScottsTechX "STX" logo fades and scales up over the
 * animated cinematic background for ~1.5 seconds while the marketplace
 * warm-ups race it (the splash never waits on the network), then hands
 * control to the onboarding / role-select flow.
 *
 * Deliberately simple and bulletproof: one logo, one fade, one delay —
 * the exact launch mechanism that has always worked on real devices
 * (no platform splash screen, no theme tricks, no frame loops).
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    // Entrance animation: logo fades in and settles up to full size.
    var shown by remember { mutableStateOf(false) }
    val logoAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "stx-logo-alpha",
    )
    val logoScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.90f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "stx-logo-scale",
    )

    LaunchedEffect(Unit) {
        // The brand beat doubles as the marketplace's warm-up window:
        // while the logo plays we pull the live catalog + cart so the
        // home screen renders instantly. Fire-and-forget warm — the
        // splash NEVER waits on the network; a slow connection shows
        // the normal per-screen shimmers instead of a stuck launch.
        com.scottsx.app.data.LiveMarketplace.warm()
        com.scottsx.app.data.CartStore.warm()
        shown = true
        delay(1500)
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

        // The new STX logo (transparent PNG — carries the whole mark).
        Image(
            painter = painterResource(R.drawable.stx_logo),
            contentDescription = "ScottsTechX",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                },
        )
    }
}
