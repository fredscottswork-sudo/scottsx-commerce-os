package com.scottsx.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-user notification toggles (channels: push/email/sms, topics:
 * orders/promos). Persisted in SharedPreferences, exposed as StateFlow
 * so the UI updates live.
 */
class NotificationPrefs private constructor(private val sp: SharedPreferences) {

    val pushFlow: StateFlow<Boolean> = MutableStateFlow(sp.getBoolean("notif_push", true))
    val emailFlow: StateFlow<Boolean> = MutableStateFlow(sp.getBoolean("notif_email", true))
    val smsFlow: StateFlow<Boolean> = MutableStateFlow(sp.getBoolean("notif_sms", false))
    val ordersFlow: StateFlow<Boolean> = MutableStateFlow(sp.getBoolean("notif_orders", true))
    val promosFlow: StateFlow<Boolean> = MutableStateFlow(sp.getBoolean("notif_promos", true))

    fun setPush(v: Boolean) = write { edit().putBoolean("notif_push", v).apply(); (pushFlow as MutableStateFlow).value = v }
    fun setEmail(v: Boolean) = write { edit().putBoolean("notif_email", v).apply(); (emailFlow as MutableStateFlow).value = v }
    fun setSms(v: Boolean) = write { edit().putBoolean("notif_sms", v).apply(); (smsFlow as MutableStateFlow).value = v }
    fun setOrders(v: Boolean) = write { edit().putBoolean("notif_orders", v).apply(); (ordersFlow as MutableStateFlow).value = v }
    fun setPromos(v: Boolean) = write { edit().putBoolean("notif_promos", v).apply(); (promosFlow as MutableStateFlow).value = v }

    private inline fun write(block: SharedPreferences.() -> Unit) = sp.block()

    companion object {
        @Volatile private var instance: NotificationPrefs? = null
        fun get(context: Context): NotificationPrefs =
            instance ?: synchronized(this) {
                instance ?: NotificationPrefs(
                    context.applicationContext.getSharedPreferences("notif_prefs", Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
