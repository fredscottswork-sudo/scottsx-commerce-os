package com.scottsx.app.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A silent, looping background video.
 *
 * Deliberately built on the framework [VideoView] rather than Media3/ExoPlayer:
 * VideoView has existed since API 1, so this adds no dependency, no extra APK
 * weight, and nothing that could fail to resolve on an old device. The clips
 * are H.264 Constrained Baseline / yuv420p, which every Android hardware
 * decoder since 4.x can play.
 *
 * Robustness matters more than fidelity here — this is the very first screen a
 * new user sees, so a codec failure must never be able to show a black rectangle
 * or crash the app:
 *  - [onFailed] fires if the decoder rejects the file, letting the caller fall
 *    back to a still image.
 *  - Playback is paused/released with the composition, so leaving the screen
 *    cannot leak a decoder or keep the CPU busy.
 *
 * @param resId a raw resource id, e.g. `R.raw.welcome_intro`.
 * @param onFailed invoked on the main thread if the video cannot be played.
 */
@Composable
fun LoopingVideo(
    resId: Int,
    modifier: Modifier = Modifier,
    onFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val uri = remember(resId) {
        Uri.parse("android.resource://" + context.packageName + "/" + resId)
    }

    // Hold the view so the lifecycle observer and DisposableEffect can reach it.
    val holder = remember { arrayOfNulls<VideoView>(1) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pause while the app is backgrounded. A VideoView left playing keeps a
    // hardware decoder open and drains battery behind the lock screen; it also
    // resumes mid-clip on return, which looks like a glitch.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val v = holder[0]
            when (event) {
                Lifecycle.Event.ON_PAUSE -> runCatching { if (v?.isPlaying == true) v.pause() }
                Lifecycle.Event.ON_RESUME -> runCatching { if (v?.isPlaying == false) v.start() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    holder[0] = this
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        // Silent by design: an intro that blares audio the
                        // moment the app opens is hostile, especially as the
                        // clips have no meaningful soundtrack.
                        mp.setVolume(0f, 0f)
                        start()
                    }
                    setOnErrorListener { _, _, _ ->
                        // Returning true marks the error handled, which stops
                        // the framework popping its "Can't play this video"
                        // dialog over our onboarding.
                        onFailed()
                        true
                    }
                }
            },
        )
    }

    DisposableEffect(resId) {
        onDispose {
            holder[0]?.let { v ->
                runCatching {
                    v.stopPlayback()
                }
            }
            holder[0] = null
        }
    }
}
