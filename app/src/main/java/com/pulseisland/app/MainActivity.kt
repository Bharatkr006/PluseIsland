package com.pulseisland.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pulseisland.app.service.IslandOverlayService
import com.pulseisland.app.ui.MainScreen
import com.pulseisland.app.ui.theme.PulseIslandTheme
import com.pulseisland.app.util.PermissionUtils

/**
 * Main entry activity for PulseIsland.
 * Manages UI display, permission validation, and overlay service coordination.
 */
class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasOverlayPermission = PermissionUtils.hasOverlayPermission(this)

        setContent {
            PulseIslandTheme {
                val isIslandRunning by IslandOverlayService.isRunning.collectAsState()

                MainScreen(
                    hasOverlayPermission = hasOverlayPermission,
                    isIslandRunning = isIslandRunning,
                    onOpenPermissionSettings = {
                        PermissionUtils.openOverlaySettings(this)
                    },
                    onStartIsland = {
                        IslandOverlayService.startService(this)
                    },
                    onStopIsland = {
                        IslandOverlayService.stopService(this)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission status when returning from Android System Settings
        hasOverlayPermission = PermissionUtils.hasOverlayPermission(this)

        // If permission was revoked while the island was active, dismiss the overlay cleanly
        if (!hasOverlayPermission && IslandOverlayService.isRunning.value) {
            IslandOverlayService.stopService(this)
        }
    }
}
