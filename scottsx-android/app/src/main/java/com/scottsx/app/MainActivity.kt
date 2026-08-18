package com.scottsx.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.scottsx.app.navigation.AppNavigation
import com.scottsx.app.ui.theme.ScottsTechXTheme

class MainActivity : ComponentActivity() {

    /**
     * Runtime permissions.
     *
     * The manifest declares LOCATION and POST_NOTIFICATIONS, but on Android 6+
     * (location) and 13+ (notifications) a manifest entry alone grants nothing.
     * Without this launcher the Nearby screen would never get a fix and push
     * notifications would be silently dropped — both would look like backend
     * bugs.
     *
     * We ask once on first launch; the result is not blocking, every dependent
     * feature degrades gracefully when denied.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Denied is fine: Nearby falls back to a city, push stays in-app. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStartupPermissions()

        setContent {
            ScottsTechXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun requestStartupPermissions() {
        val wanted = mutableListOf<String>()

        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            wanted += Manifest.permission.ACCESS_FINE_LOCATION
            wanted += Manifest.permission.ACCESS_COARSE_LOCATION
        }

        // POST_NOTIFICATIONS only exists on API 33+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotifications = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotifications) wanted += Manifest.permission.POST_NOTIFICATIONS
        }

        if (wanted.isNotEmpty()) {
            permissionLauncher.launch(wanted.toTypedArray())
        }
    }
}
