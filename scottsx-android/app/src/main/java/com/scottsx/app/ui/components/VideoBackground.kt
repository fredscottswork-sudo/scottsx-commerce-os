package com.scottsx.app.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Looping video background.
 *
 * Plays the supplied [videoUri] silently, scaled to fill the screen
 * via PlayerView's resizeMode = ZOOM. Loops indefinitely. The player
 * is released when the composable leaves the composition.
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
            playWhenReady = false
            volume = 0f
        }
    }

    LaunchedEffect(videoUri) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // A decoder/source failure used to leave a full-screen
                // BLACK PlayerView behind the slide forever — many users
                // reported "the welcome videos don't show". Now we drop
                // the player view; the slide keeps its branded overlay.
                android.util.Log.w(
                    "VideoBackground",
                    "welcome video failed: ${'$'}{error.errorCodeName}",
                )
                failed = true
            }
        })
        runCatching {
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }.onFailure {
            android.util.Log.w("VideoBackground", "prepare failed", it)
            failed = true
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
    }
}