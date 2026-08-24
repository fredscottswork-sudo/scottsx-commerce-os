package com.scottsx.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.scottsx.app.data.domain.SessionCache
import com.scottsx.app.data.domain.Role
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The three theme modes the app supports.
 *
 *  - LIGHT  : force the light color scheme.
 *  - DARK   : force the dark color scheme.
 *  - SYSTEM : follow the device's dark/light setting (default).
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Persists the user's theme preference. Backed by SharedPreferences
 * (no DataStore dependency) so the whole app can read it via a
 * simple [StateFlow] and the value survives process death.
 *
 * Reads / writes are cheap; the live state is exposed as
 * [themeFlow] which the root composable collects via [themeState].
 */
class ThemePreference private constructor(private val prefs: SharedPreferences) {

    private val _themeFlow = MutableStateFlow(read())
    val themeFlow: StateFlow<ThemeMode> = _themeFlow.asStateFlow()

    /** Read the persisted mode, falling back to DARK on first run. */
        private fun read(): ThemeMode {
            val raw = prefs.getString(KEY, null) ?: return ThemeMode.DARK
            return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
        }

/** Write the mode to disk and notify subscribers. Also swaps the legacy light tokens so direct PanelLight refs re-skin. */
    fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        _themeFlow.value = mode
        val isLight = mode == ThemeMode.LIGHT || mode == ThemeMode.SYSTEM
        com.scottsx.app.ui.theme.ScottsTechXColors.applyThemePalette(isLight)
    }

    /**
     * Load the user's theme preference from the backend
     * (PUT /api/v1/settings/v2) and apply it locally. This is the
     * cross-device sync hook — when the user signs in on a new phone
     * the theme they chose on the old one is restored.
     *
     * Returns true if the remote value was applied, false if no
     * remote settings were available (offline / unauthenticated).
     */
    suspend fun loadFromServer(): Boolean {
        val s = com.scottsx.app.data.remote.V2Client.loadSettings() ?: return false
        val mode = runCatching { ThemeMode.valueOf(s.theme.uppercase()) }
            .getOrDefault(ThemeMode.SYSTEM)
        set(mode)
        return true
    }

    companion object {
        private const val PREFS = "scottsx_prefs"
        private const val KEY = "theme_mode"

        @Volatile private var instance: ThemePreference? = null

        /**
         * Process-wide singleton. Lazy-built the first time
         * anything reads it (Application onCreate would also work).
         */
        fun get(context: Context): ThemePreference {
            val cached = instance
            if (cached != null) return cached
            return synchronized(this) {
                instance ?: ThemePreference(
                    context.applicationContext
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
        }
    }
}

/**
 * Composition-local override for [ThemePreference]. Tests can swap a
 * fake by wrapping the tree in `CompositionLocalProvider`. In production
 * the [ScottsTechXApp] Application supplies the real instance via
 * the local. If no override is provided we fall back to a system-default
 * theme (LIGHT when isSystemInDarkTheme is false, DARK otherwise).
 */
val LocalThemePreference = staticCompositionLocalOf<ThemePreference> {
    // The real app provides this via CompositionLocalProvider in
    // MainActivity. If a preview or deep-linked composable hits this
    // default, we throw with a clear message so the fix is easy.
    error("ThemePreference not provided. Wrap your tree in CompositionLocalProvider (MainActivity does this).")
}

/**
 * Compose-friendly accessor: collects [ThemePreference.themeFlow] as
 * Compose [State] so recomposition happens when the user toggles the
 * theme.
 */
@Composable
fun ThemePreference.themeState(): State<ThemeMode> = themeFlow.collectAsState()

/**
 * Pick a background + text palette for the buyer sidebar given the
 * current theme + mode. The colors are deliberately the same blue
 * gradient in light and dark mode (sidebar stays on-brand) but the
 * surface behind the gradient flips between light and dark.
 */
data class SidebarPalette(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
)

@Composable
fun sidebarPaletteFor(mode: ThemeMode): SidebarPalette {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    return if (dark) SidebarPalette(
        background = ScottsTechXColors.BackgroundDark,
        surface = ScottsTechXColors.SurfaceElevatedDark,
        onSurface = ScottsTechXColors.OnDark,
        onSurfaceMuted = ScottsTechXColors.OnDarkSecondary,
    ) else SidebarPalette(
        background = Color.White,
        surface = ScottsTechXColors.PanelInputLight,
        onSurface = ScottsTechXColors.OnLight,
        onSurfaceMuted = ScottsTechXColors.OnLightSecondary,
    )
}

/** Convenience: should the seller center entry be shown for the current session? */
fun SessionCache.isSeller(): Boolean = role == Role.SELLER