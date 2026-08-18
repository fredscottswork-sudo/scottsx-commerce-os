package com.scottsx.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Buyer position stream, used by the Nearby screen so stores continuously
 * re-sort as the user moves.
 *
 * Deliberately built on the platform [LocationManager] rather than
 * play-services-location: it needs no extra dependency, works on devices
 * without Google Play Services, and this app only needs coarse "which store is
 * closest" accuracy.
 *
 * Nothing here throws when permission is missing — callers get a null/empty
 * result and the UI falls back to a chosen city.
 */
object LocationProvider {

    /** Minimum time between updates (ms). */
    private const val MIN_INTERVAL_MS = 5_000L

    /** Minimum movement before a new fix is emitted (metres). */
    private const val MIN_DISTANCE_M = 25f

    data class Fix(val lat: Double, val lng: Double, val accuracyM: Float = 0f)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Best cached position, or null. Cheap: does not power up the GPS radio,
     * so it is safe to call on screen entry for an instant first render.
     */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Fix? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            providers
                .mapNotNull { provider ->
                    if (manager.isProviderEnabled(provider)) {
                        manager.getLastKnownLocation(provider)
                    } else {
                        null
                    }
                }
                // Prefer the most recent fix.
                .maxByOrNull { it.time }
                ?.let { Fix(it.latitude, it.longitude, it.accuracy) }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Continuous position updates while collected.
     *
     * Emits the last known fix immediately (so the list is never empty while
     * waiting for a satellite lock), then every subsequent update. The
     * registration is torn down automatically when collection stops.
     */
    @SuppressLint("MissingPermission")
    fun updates(context: Context): Flow<Fix> = callbackFlow {
        if (!hasPermission(context)) {
            close()
            return@callbackFlow
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            close()
            return@callbackFlow
        }

        lastKnown(context)?.let { trySend(it) }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(Fix(location.latitude, location.longitude, location.accuracy))
            }

            // Required on older API levels; no-ops on modern Android.
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        }

        val registered = mutableListOf<String>()
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(
                        provider,
                        MIN_INTERVAL_MS,
                        MIN_DISTANCE_M,
                        listener,
                        Looper.getMainLooper(),
                    )
                    registered.add(provider)
                }
            }
        } catch (e: SecurityException) {
            close(e)
        } catch (e: Exception) {
            close(e)
        }

        if (registered.isEmpty()) {
            // Location services are off entirely; the caller falls back to a city.
            close()
        }

        awaitClose {
            try {
                manager.removeUpdates(listener)
            } catch (e: Exception) {
                // Nothing useful to do while tearing down.
            }
        }
    }

    /**
     * Great-circle distance in kilometres (haversine).
     *
     * The server already returns `distanceKm`, but the app re-sorts locally
     * between network refreshes so the list reacts immediately as you walk.
     */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadiusKm * c
    }
}
