package com.scottsx.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window-inset helpers.
 *
 * The app targets SDK 35. From Android 15 onwards an app that targets 35 is
 * laid out EDGE TO EDGE and cannot opt out (the `windowOptOutEdgeToEdgeEnforcement`
 * escape hatch is already deprecated for target 36). The system draws the
 * status bar, the gesture/3-button navigation bar and any display cutout
 * *over* our window, so anything positioned at the very top or very bottom of
 * a full-screen Composable is partly hidden unless we pad it ourselves.
 *
 * Before this file nothing in the app referenced WindowInsets at all, which is
 * why headers sat under the clock and bottom bars sat under the gesture pill.
 *
 * Use [ScreenScaffold] for a normal screen, or the modifiers below when a
 * screen needs to keep drawing its gradient behind the status bar while still
 * pushing its *content* clear of it.
 */

/** Height of the status bar / cutout at the top of the window. */
@Composable
fun topInset(): Dp =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()

/** Height of the navigation bar (gesture pill or 3-button bar) at the bottom. */
@Composable
fun bottomInset(): Dp =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

/**
 * Pads a coloured header so its *content* clears the status bar while the
 * background keeps bleeding to the top edge — the look every modern store app
 * uses. Apply to the inner Column, not to the Box that paints the gradient.
 *
 * Must be @Composable: `WindowInsets.safeDrawing` is a @Composable getter
 * (`@get:Composable`), so a plain extension function that reads it does not
 * compile — "@Composable invocations can only happen from the context of a
 * @Composable function". Calling this inside a modifier chain in a composable
 * body is fine, because that body IS a composable context.
 */
@Composable
fun Modifier.statusBarSpacer(): Modifier =
    this.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))

/**
 * Keeps a bottom bar above the gesture pill. The bar's own surface still
 * paints all the way down; only its rows are lifted.
 */
@Composable
fun Modifier.navBarSpacer(): Modifier =
    this.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))

/**
 * A screen container that keeps everything inside the safe area on all four
 * sides. `extraBottom` reserves room for an app bottom bar that is drawn as a
 * sibling overlay (the buyer and seller navigation bars are positioned this
 * way), so list content scrolls clear of it.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    extraBottom: Dp = 0.dp,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content(
            PaddingValues(
                top = topInset(),
                bottom = bottomInset() + extraBottom,
            ),
        )
    }
}
