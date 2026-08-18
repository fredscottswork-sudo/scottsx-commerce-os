package com.scottsx.app.ai

import com.scottsx.app.UserPrefs

/**
 * Persisted AI personalisation — remembers the user's name/city and whether
 * personalisation is on, so prompts can be prefixed with context.
 */
object AiPersonalizationStore {

    var name: String
        get() = UserPrefs.aiUserName
        set(value) { UserPrefs.aiUserName = value }

    var city: String
        get() = UserPrefs.aiCity
        set(value) { UserPrefs.aiCity = value }

    var enabled: Boolean
        get() = UserPrefs.aiPersonalisationOn
        set(value) { UserPrefs.aiPersonalisationOn = value }

    fun summary(): String =
        "Name: ${name.ifBlank { "not set" }} • City: ${city.ifBlank { "not set" }} • " +
            (if (enabled) "Personalisation ON" else "Personalisation OFF")
}
