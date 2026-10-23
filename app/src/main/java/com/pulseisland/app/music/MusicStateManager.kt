package com.pulseisland.app.music

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages discovery of active media sessions and provides a reactive [MusicState] flow.
 *
 * Architecture notes:
 * - Fully event-driven: no polling loops or timers
 * - Uses MediaSessionManager.OnActiveSessionsChangedListener for session discovery
 * - Each active MediaController gets a callback registered for state/metadata updates
 * - All callbacks are cleaned up on [release]
 * - Requires BIND_NOTIFICATION_LISTENER_SERVICE permission via NotificationListenerService
 */
class MusicStateManager(private val context: Context) {

    private val _musicState = MutableStateFlow(MusicState.EMPTY)
    val musicState: StateFlow<MusicState> = _musicState.asStateFlow()

    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var controllerCallback: MediaController.Callback? = null
    private var sessionChangeListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    private val notificationListenerComponent = ComponentName(
        context, PulseIslandNotificationListener::class.java
    )

    /**
     * Returns true if the user has granted notification listener access.
     * This is required on all API levels to use MediaSessionManager.
     */
    fun hasNotificationListenerPermission(): Boolean {
        return com.pulseisland.app.util.PermissionUtils.hasNotificationListenerPermission(context)
    }

    /**
     * Starts monitoring for active media sessions.
     * Call only after verifying [hasNotificationListenerPermission].
     */
    fun start() {
        if (!hasNotificationListenerPermission()) return
        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            sessionManager = manager

            // Listen for session list changes (new apps start/stop playing)
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
                onActiveSessionsChanged(sessions)
            }
            sessionChangeListener = listener

            manager.addOnActiveSessionsChangedListener(listener, notificationListenerComponent)

            // Immediately check current active sessions
            val currentSessions = manager.getActiveSessions(notificationListenerComponent)
            onActiveSessionsChanged(currentSessions)
        } catch (e: SecurityException) {
            // Permission revoked between check and call
            _musicState.value = MusicState.EMPTY
        } catch (e: Exception) {
            _musicState.value = MusicState.EMPTY
        }
    }

    /**
     * Stops all monitoring and releases all resources.
     * Safe to call multiple times.
     */
    fun release() {
        try {
            sessionChangeListener?.let {
                sessionManager?.removeOnActiveSessionsChangedListener(it)
            }
        } catch (_: Exception) {}

        unregisterCurrentController()
        sessionManager = null
        sessionChangeListener = null
        _musicState.value = MusicState.EMPTY
    }

    /**
     * Sends a play/pause toggle to the active media session.
     */
    fun togglePlayPause() {
        val controller = activeController ?: return
        val state = controller.playbackState?.state
        try {
            if (state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } catch (_: Exception) {}
    }

    /**
     * Sends skip-to-next command if the session supports it.
     */
    fun skipToNext() {
        val controller = activeController ?: return
        val actions = controller.playbackState?.actions ?: return
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) {
            try { controller.transportControls.skipToNext() } catch (_: Exception) {}
        }
    }

    /**
     * Sends skip-to-previous command if the session supports it.
     */
    fun skipToPrevious() {
        val controller = activeController ?: return
        val actions = controller.playbackState?.actions ?: return
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) {
            try { controller.transportControls.skipToPrevious() } catch (_: Exception) {}
        }
    }

    // ─── Private ────────────────────────────────────────────────────────────────

    private fun onActiveSessionsChanged(sessions: List<MediaController>?) {
        unregisterCurrentController()

        // Pick the first active playing session, fall back to first session if none playing
        val preferred = sessions?.firstOrNull { ctrl ->
            ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions?.firstOrNull()

        if (preferred == null) {
            _musicState.value = MusicState.EMPTY
            return
        }

        registerController(preferred)
    }

    private fun registerController(controller: MediaController) {
        activeController = controller

        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                updateState()
            }
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateState()
            }
            override fun onSessionDestroyed() {
                unregisterCurrentController()
                // Re-query for any remaining sessions
                try {
                    val sessions = sessionManager?.getActiveSessions(notificationListenerComponent)
                    onActiveSessionsChanged(sessions)
                } catch (_: Exception) {
                    _musicState.value = MusicState.EMPTY
                }
            }
        }
        controllerCallback = callback

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                controller.registerCallback(callback)
            } else {
                controller.registerCallback(callback)
            }
        } catch (_: Exception) {}

        // Immediately emit current state
        updateState()
    }

    private fun unregisterCurrentController() {
        try {
            val cb = controllerCallback
            val ctrl = activeController
            if (cb != null && ctrl != null) {
                ctrl.unregisterCallback(cb)
            }
        } catch (_: Exception) {}
        controllerCallback = null
        activeController = null
    }

    private fun updateState() {
        val controller = activeController
        if (controller == null) {
            _musicState.value = MusicState.EMPTY
            return
        }

        val pbState = controller.playbackState
        val metadata = controller.metadata
        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val positionMs = pbState?.position ?: 0L

        val albumArt: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val actions = pbState?.actions ?: 0L
        val canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
        val canSkipPrev = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L

        _musicState.value = MusicState(
            hasActiveSession = true,
            isPlaying = isPlaying,
            title = title?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() },
            albumArt = albumArt,
            durationMs = durationMs,
            positionMs = positionMs,
            canSkipNext = canSkipNext,
            canSkipPrevious = canSkipPrev,
            positionTimestampMs = System.currentTimeMillis()
        )
    }
}
