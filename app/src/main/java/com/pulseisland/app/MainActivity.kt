package com.pulseisland.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pulseisland.app.data.IslandPreferences
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
        IslandPreferences.init(this)
        hasOverlayPermission = PermissionUtils.hasOverlayPermission(this)

        setContent {
            PulseIslandTheme {
                val isIslandRunning by IslandOverlayService.isRunning.collectAsState()
                val islandConfig by IslandPreferences.configFlow.collectAsState()

                MainScreen(
                    hasOverlayPermission = hasOverlayPermission,
                    isIslandRunning = isIslandRunning,
                    islandConfig = islandConfig,
                    onOpenPermissionSettings = {
                        PermissionUtils.openOverlaySettings(this@MainActivity)
                    },
                    onStartIsland = {
                        IslandOverlayService.startService(this@MainActivity)
                    },
                    onStopIsland = {
                        IslandOverlayService.stopService(this@MainActivity)
                    },
                    onUpdateXOffset = { newX ->
                        IslandPreferences.setXOffsetDp(this@MainActivity, newX)
                    },
                    onUpdateYOffset = { newY ->
                        IslandPreferences.setYOffsetDp(this@MainActivity, newY)
                    },
                    onUpdateScale = { newScale ->
                        IslandPreferences.setScale(this@MainActivity, newScale)
                    },
                    onResetDefaults = {
                        IslandPreferences.resetToDefaults(this@MainActivity)
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
