package com.scottsx.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/**
 * Real-device location acquisition built on top of Play Services'
 * Fused Location Provider.
 *
 * Why a thin wrapper rather than calling FLP directly from the UI:
 *  - Permission gating in one place, so callers can't accidentally
 *    request a fix when the user hasn't granted location yet.
 *  - Returns `null` cleanly when permission is missing — callers
 *    (e.g. NearbyScreen) then fall back to the manual location chip
 *    picker so the UX degrades gracefully.
 *  - 30s timeout via [CancellationTokenSource] so a location never
 *    blocks the UI indefinitely.
 *
 * Stage-3.1 follow-up: cache the last-known fix in [SessionCache]
 * so subsequent screen opens don't trigger a fresh GPS read.
 */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Has the user granted fine location? Coarse is insufficient. */
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Current best fix using a single high-accuracy request.
     *
     * Returns null if permission is missing OR the device failed to
     * produce a fix within ~30 seconds (offline, no GPS, indoors).
     */
    @SuppressLint("MissingPermission") // checked inside hasLocationPermission()
    suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        return runCatching {
            val cts = CancellationTokenSource()
            val priority = Priority.PRIORITY_HIGH_ACCURACY
            val loc = client.getCurrentLocation(priority, cts.token).await()
            cts.cancel()
            loc
        }.getOrNull()
    }
}
