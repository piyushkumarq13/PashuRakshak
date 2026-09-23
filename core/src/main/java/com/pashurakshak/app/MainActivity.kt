package com.pashurakshak.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pashurakshak.app.ui.navigation.AppNavHost
import com.pashurakshak.app.ui.theme.PashuRakshakTheme

class MainActivity : ComponentActivity() {

    /** Destination requested by a tapped notification ("alerts"), consumed once. */
    private val pendingDestination = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDestination.value = intent?.getStringExtra(EXTRA_DESTINATION)
        requestNotificationPermissionIfNeeded()
        setContent {
            PashuRakshakTheme {
                AppNavHost(
                    pendingDestination = pendingDestination.value,
                    onPendingDestinationShown = { pendingDestination.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingDestination.value = intent.getStringExtra(EXTRA_DESTINATION)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
    }
}
