package com.scottsx.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide connectivity state.
 *
 * Registered once from [ScottsTechXApp.onCreate]; screens read [isConnected]
 * to show the offline banner. A network failure must never masquerade as an
 * "empty" list, and a lost connection is exactly when the user needs to see
 * that a screen's data may be stale.
 *
 * [NET_CAPABILITY_VALIDATED] means the OS has confirmed actual internet
 * access (not just a connected-but-blackholed network), which is what the
 * "offline" state should mean for a buyer mid-checkout.
 */
object ConnectionWatcher {

    private val _isConnected = MutableStateFlow(true)

    /** True while the device has a validated internet connection. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    @Volatile
    private var started = false

    /** Idempotent — safe to call from Application.onCreate. */
    fun start(context: Context) {
        if (started) return
        started = true

        val appContext = context.applicationContext
        val connectivity =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        _isConnected.value = isOnline(connectivity)

        // Fires onAvailable/onLost for the default network as it comes and
        // goes (Wi-Fi <-> mobile <-> airplane mode, captive portals, etc.).
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isConnected.value = isOnline(connectivity)
                }

                override fun onLost(network: Network) {
                    _isConnected.value = isOnline(connectivity)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _isConnected.value = isOnline(connectivity)
                }
            },
        )
    }

    private fun isOnline(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
