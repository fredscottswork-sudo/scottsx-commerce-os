package com.scottsx.app.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay

/**
 * Looping video background.
 *
 * Plays the supplied [videoUri] silently, scaled to fill the screen
 * via PlayerView's resizeMode = ZOOM. Loops indefinitely. The player
 * is released when the composable leaves the composition.
 *
 * Reliability guarantees (the welcome slides must never go dark):
 *  - playback starts the moment the slide appears (playWhenReady is
 *    set before prepare, and the surface shutter is transparent so
 *    the brand background shows while the first frame decodes);
 *  - a decoder/source failure used to leave a full-screen BLACK
 *    PlayerView behind the slide forever — "the welcome screens show
 *    nothing". Now a failure triggers ONE automatic retry, and if the
 *    clip still cannot play the PlayerView is dropped and a branded
 *    gradient stands in, so the slide always shows something;
 *  - dispose calls stop() before release(), which avoids the
 *    decoder-teardown crash some devices throw when the player is
 *    released mid-decode.
 *
 * @param videoUri A `content://`, `file://`, or `raw resource://` URI
 *                 pointing at the looping video clip.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoBackground(
    videoUri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f
        }
    }

    LaunchedEffect(videoUri) {
        failed = false
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w(
                    "VideoBackground",
                    "welcome video failed: ${error.errorCodeName}",
                )
                failed = true
            }
        }
        exoPlayer.addListener(listener)
        try {
            var attempt = 0
            while (attempt < 2) {
                attempt++
                val prepared = runCatching {
                    exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }.isSuccess
                if (!prepared) {
                    if (attempt < 2) {
                        delay(700)
                        continue
                    }
                    failed = true
                    return@LaunchedEffect
                }
                // Give the pipeline a short window to reach READY (or
                // report an error) before deciding.
                var waited = 0L
                while (
                    waited < 3500L &&
                    !failed &&
                    exoPlayer.playbackState != Player.STATE_READY &&
                    exoPlayer.playbackState != Player.STATE_ENDED
                ) {
                    delay(100)
                    waited += 100
                }
                if (failed && attempt < 2) {
                    // One retry on a transient decoder hiccup.
                    failed = false
                    delay(700)
                    continue
                }
                break
            }
            // Neither ready nor errored after both attempts — give up
            // gracefully rather than leave a black surface behind.
            if (!failed &&
                exoPlayer.playbackState != Player.STATE_READY &&
                exoPlayer.playbackState != Player.STATE_ENDED
            ) {
                failed = true
            }
        } finally {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // stop() before release() prevents the decoder-teardown
            // crash some devices throw when releasing mid-decode.
            runCatching { exoPlayer.stop() }
            runCatching { exoPlayer.release() }
        }
    }

    if (!failed) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    // Transparent shutter: while the first frame decodes
                    // the brand background shows through instead of black.
                    setShutterBackgroundColor(Color.TRANSPARENT)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
        )
    } else {
        // Branded stand-in — the slide keeps its look instead of a
        // black hole when the clip cannot be decoded.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A1B4D),
                            ScottsTechXColors.BackgroundDark,
                            Color(0xFF050711),
                        ),
                    ),
                ),
        )
    }
}
