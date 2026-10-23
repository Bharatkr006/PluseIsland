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
import com.pulseisland.app.data.IslandPreferences
import com.pulseisland.app.music.MusicStateManager
import com.pulseisland.app.overlay.IslandOverlayManager
import com.pulseisland.app.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service managing the PulseIsland overlay and music session integration.
 *
 * Battery-efficient:
 * - MusicStateManager is fully event-driven (no polling)
 * - Only started when the user explicitly starts the island
 * - Cleans up all resources on stop
 */
class IslandOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.pulseisland.app.action.START"
        const val ACTION_STOP  = "com.pulseisland.app.action.STOP"

        private const val CHANNEL_ID       = "pulse_island_overlay"
        private const val NOTIFICATION_ID  = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        @Volatile private var activeInstance: IslandOverlayService? = null

        fun startService(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stopService(context: Context) {
            val instance = activeInstance
            if (instance != null) {
                instance.stopOverlay()
            } else {
                try {
                    context.startService(Intent(context, IslandOverlayService::class.java).apply { action = ACTION_STOP })
                } catch (_: Exception) {}
            }
        }
    }

    private var overlayManager: IslandOverlayManager? = null
    private var musicManager: MusicStateManager? = null
    private var configJob: Job? = null
    private var musicJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()

        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> { stopOverlay(); return START_NOT_STICKY }
        }

        if (!PermissionUtils.hasOverlayPermission(this)) {
            stopOverlay(); return START_NOT_STICKY
        }

        showOverlay()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayManager?.onConfigurationChanged()
    }

    // ─── Overlay lifecycle ───────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayManager == null) {
            val manager = IslandOverlayManager(context = this, onCloseRequested = { stopOverlay() })
            overlayManager = manager

            // Wire music control callbacks
            manager.setMusicControlCallbacks(object : IslandOverlayManager.MusicControlCallbacks {
                override fun onPlayPause() { musicManager?.togglePlayPause() }
                override fun onSkipNext() { musicManager?.skipToNext() }
                override fun onSkipPrevious() { musicManager?.skipToPrevious() }
            })
        }

        overlayManager?.show()
        _isRunning.value = true

        // Observe preference changes (size/position sliders)
        configJob?.cancel()
        configJob = CoroutineScope(Dispatchers.Main).launch {
            IslandPreferences.configFlow.collect { config ->
                overlayManager?.updateConfiguration(config)
            }
        }

        // Start music manager and observe its state
        startMusicManager()
    }

    private fun startMusicManager() {
        if (!PermissionUtils.isNotificationListenerDeclared(this)) {
            // In minimal build variant, notification listener is omitted to avoid Play Protect sensitive data block
            return
        }
        val musicMgr = MusicStateManager(this)
        musicManager = musicMgr
        musicMgr.start()

        musicJob?.cancel()
        musicJob = CoroutineScope(Dispatchers.Main).launch {
            musicMgr.musicState.collect { musicState ->
                overlayManager?.updateMusicState(musicState)
            }
        }
    }

    private fun stopOverlay() {
        configJob?.cancel(); configJob = null
        musicJob?.cancel();  musicJob = null

        musicManager?.release()
        musicManager = null

        overlayManager?.hide()
        overlayManager = null
        _isRunning.value = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    // ─── Notification ────────────────────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(com.pulseisland.app.R.string.island_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(com.pulseisland.app.R.string.island_service_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, IslandOverlayService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.pulseisland.app.R.string.island_notification_title))
            .setContentText(getString(com.pulseisland.app.R.string.island_notification_text))
            .setSmallIcon(com.pulseisland.app.R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .addAction(com.pulseisland.app.R.drawable.ic_close,
                getString(com.pulseisland.app.R.string.action_stop), stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        configJob?.cancel(); musicJob?.cancel()
        musicManager?.release(); musicManager = null
        overlayManager?.hide(); overlayManager = null
        _isRunning.value = false
        activeInstance = null
    }
}
