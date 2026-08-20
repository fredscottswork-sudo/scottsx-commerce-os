package com.scottsx.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.scottsx.app.R
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay

/**
 * The brand splash every launch opens with, the way other apps do it: the
 * ScottsTechX logo alone on the brand gradient, then it hands off.
 *
 * It is deliberately short and it never blocks. [onFinished] fires from a
 * LaunchedEffect keyed to Unit, so it runs exactly once per composition and
 * still fires if the animation is cut short by the system disabling
 * animations (accessibility, battery saver, or a developer-options override).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Drives the fade + settle. Starting false and flipping it inside a
    // LaunchedEffect guarantees the animation actually plays: if we started
    // at the target value there would be nothing to animate towards.
    var shown by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 620),
        label = "splashAlpha",
    )
    // A restrained settle - the mark eases down to its true size rather than
    // punching in. Anything more theatrical gets tiring on every cold start.
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 1.14f,
        animationSpec = tween(durationMillis = 760),
        label = "splashScale",
    )

    LaunchedEffect(Unit) {
        shown = true
        // Long enough to read the brand, short enough not to be a toll gate.
        delay(1500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScottsTechXColors.DarkBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // Gradient stays full-bleed; the content keeps clear of the
            // status bar and the gesture pill.
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.brand_lockup),
                contentDescription = "ScottsTechX",
                contentScale = ContentScale.Fit,
                // The lockup already carries the wordmark, so there is no
                // separate title underneath it to say the name twice.
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .scale(scale)
                    .alpha(alpha),
            )
        }
    }
}
