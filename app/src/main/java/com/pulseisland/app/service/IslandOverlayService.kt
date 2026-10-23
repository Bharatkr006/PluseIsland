package com.pulseisland.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pulseisland.app.MainActivity
import com.pulseisland.app.R
import com.pulseisland.app.overlay.IslandOverlayManager
import com.pulseisland.app.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service managing the lifecycle of the floating Dynamic Island overlay.
 *
 * Runs as a lightweight foreground service to comply with Android 8+ background execution limits,
 * with minimal battery consumption (zero background loops or CPU wake locks).
 */
class IslandOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.pulseisland.app.action.START"
        const val ACTION_STOP = "com.pulseisland.app.action.STOP"

        private const val CHANNEL_ID = "pulse_island_overlay"
        private const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        @Volatile
        private var activeInstance: IslandOverlayService? = null

        fun startService(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val instance = activeInstance
            if (instance != null) {
                instance.stopOverlay()
            } else {
                try {
                    val intent = Intent(context, IslandOverlayService::class.java).apply {
                        action = ACTION_STOP
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private var overlayManager: IslandOverlayManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        // Always satisfy Android 8+ foreground service requirement immediately
        startForegroundWithNotification()

        if (action == ACTION_STOP) {
            stopOverlay()
            return START_NOT_STICKY
        }

        // Verify overlay permission before attaching window
        if (!PermissionUtils.hasOverlayPermission(this)) {
            stopOverlay()
            return START_NOT_STICKY
        }

        showOverlay()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayManager?.onConfigurationChanged()
    }

    private fun showOverlay() {
        if (overlayManager == null) {
            overlayManager = IslandOverlayManager(
                context = this,
                onCloseRequested = {
                    stopOverlay()
                }
            )
        }
        overlayManager?.show()
        _isRunning.value = true
    }

    private fun stopOverlay() {
        overlayManager?.hide()
        overlayManager = null
        _isRunning.value = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.island_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.island_service_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, IslandOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.island_notification_title))
            .setContentText(getString(R.string.island_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_close,
                getString(R.string.action_stop),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager?.hide()
        overlayManager = null
        _isRunning.value = false
        activeInstance = null
    }
}
