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
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback for OEMs that do not support package URI on overlay action
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                // Final fallback to Application Details screen
                val appDetailsIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(appDetailsIntent)
            }
        }
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

    /**
     * Returns true if PulseIslandNotificationListener is registered in this build's manifest.
     * Returns false in the 'minimal' build variant where notification listener is omitted.
     */
    fun isNotificationListenerDeclared(context: Context): Boolean {
        return try {
            val component = android.content.ComponentName(
                context,
                "com.pulseisland.app.music.PulseIslandNotificationListener"
            )
            context.packageManager.getServiceInfo(component, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks whether the user has enabled PulseIsland as a Notification Listener.
     * This is required to access MediaSessionManager for music controls.
     * Returns false immediately if not declared in the current build variant.
     */
    fun hasNotificationListenerPermission(context: Context): Boolean {
        if (!isNotificationListenerDeclared(context)) return false
        return try {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            enabledListeners.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens Android System Settings for Notification Listener / Device & app notifications.
     */
    fun openNotificationListenerSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                val appDetailsIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(appDetailsIntent)
            } catch (_: Exception) {}
        }
    }
}

