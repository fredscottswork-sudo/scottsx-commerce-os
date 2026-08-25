package com.scottsx.app.data.preferences

import android.content.Context
import android.content.SharedPreferences

/** Language + currency + privacy prefs. */
class UserPrefs private constructor(private val sp: SharedPreferences) {
    fun getLanguage(): String = sp.getString("lang", "en") ?: "en"
    fun setLanguage(code: String) { sp.edit().putString("lang", code).apply() }
    fun getCurrency(): String = sp.getString("currency", "UGX") ?: "UGX"
    fun setCurrency(code: String) { sp.edit().putString("currency", code).apply() }
    fun getPrivacyShowReceipts(): Boolean = sp.getBoolean("priv_receipts", true)
    fun setPrivacyShowReceipts(v: Boolean) { sp.edit().putBoolean("priv_receipts", v).apply() }
    fun getPrivacyShowTransactions(): Boolean = sp.getBoolean("priv_trans", true)
    fun setPrivacyShowTransactions(v: Boolean) { sp.edit().putBoolean("priv_trans", v).apply() }

    // Theme mode: "system" | "light" | "dark"
fun themeMode(): String = sp.getString("theme_mode", "dark") ?: "dark"
        fun setThemeMode(mode: String) { sp.edit().putString("theme_mode", mode).apply() }

    companion object {
        @Volatile private var instance: UserPrefs? = null
        fun get(context: Context): UserPrefs = instance ?: synchronized(this) {
            instance ?: UserPrefs(
                context.applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
