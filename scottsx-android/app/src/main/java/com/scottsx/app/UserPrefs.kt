package com.scottsx.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Non-secret persisted preferences (theme, language, currency, AI personalisation).
 * Secrets (JWT) intentionally live only in SessionCache for this dev build.
 */
object UserPrefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences("scottsx_prefs", Context.MODE_PRIVATE)
        }
    }

    private fun ensure() {
        check(::prefs.isInitialized) { "UserPrefs.init(context) must be called first" }
    }

    var themeMode: String
        get() { ensure(); return prefs.getString("theme_mode", "system") ?: "system" }
        set(value) { ensure(); prefs.edit().putString("theme_mode", value).apply() }

    var language: String
        get() { ensure(); return prefs.getString("language", "en") ?: "en" }
        set(value) { ensure(); prefs.edit().putString("language", value).apply() }

    var currency: String
        get() { ensure(); return prefs.getString("currency", "UGX") ?: "UGX" }
        set(value) { ensure(); prefs.edit().putString("currency", value).apply() }

    // AI personalisation
    var aiUserName: String
        get() { ensure(); return prefs.getString("ai_user_name", "") ?: "" }
        set(value) { ensure(); prefs.edit().putString("ai_user_name", value).apply() }

    var aiCity: String
        get() { ensure(); return prefs.getString("ai_city", "Kampala") ?: "Kampala" }
        set(value) { ensure(); prefs.edit().putString("ai_city", value).apply() }

    var aiPersonalisationOn: Boolean
        get() { ensure(); return prefs.getBoolean("ai_personalisation_on", true) }
        set(value) { ensure(); prefs.edit().putBoolean("ai_personalisation_on", value).apply() }
}
