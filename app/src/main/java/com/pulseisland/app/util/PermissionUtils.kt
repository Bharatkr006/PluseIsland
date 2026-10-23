package com.pulseisland.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Utility functions for checking and requesting system overlay and notification permissions.
 */
object PermissionUtils {

    /**
     * Checks whether the application has permission to draw on top of other applications.
     * Required for WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Opens Android's official system settings screen for "Display over other apps".
     * Guides the user to explicitly grant permission for this application.
     */
    fun openOverlaySettings(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }

    /**
     * Checks if notification permission is granted on Android 13+ (API 33+).
     * On earlier versions, notification permission is granted at install time.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
