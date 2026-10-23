package com.pulseisland.app.music

import android.graphics.Bitmap

/**
 * Immutable snapshot of the current media playback state.
 * All fields are nullable to gracefully handle unavailable metadata.
 */
data class MusicState(
    /** True when a media session is active (doesn't mean playing) */
    val hasActiveSession: Boolean = false,
    /** True when audio is actively playing */
    val isPlaying: Boolean = false,
    /** Track title from MediaMetadata, null if unavailable */
    val title: String? = null,
    /** Artist name from MediaMetadata, null if unavailable */
    val artist: String? = null,
    /** Album artwork bitmap, null if unavailable or not loaded yet */
    val albumArt: Bitmap? = null,
    /** Track duration in milliseconds, -1 if unknown */
    val durationMs: Long = -1L,
    /** Current playback position in milliseconds, 0 if unknown */
    val positionMs: Long = 0L,
    /** Whether skip-to-next is supported by this session */
    val canSkipNext: Boolean = false,
    /** Whether skip-to-previous is supported by this session */
    val canSkipPrevious: Boolean = false,
    /** Timestamp when positionMs was captured (System.currentTimeMillis) */
    val positionTimestampMs: Long = 0L
) {
    /** Estimated current position accounting for elapsed time since last update */
    fun estimatedPositionMs(): Long {
        if (!isPlaying || positionMs < 0) return positionMs
        val elapsed = System.currentTimeMillis() - positionTimestampMs
        return (positionMs + elapsed).coerceAtMost(if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }

    /** Progress fraction 0.0 to 1.0, or 0 if unknown */
    fun progressFraction(): Float {
        if (durationMs <= 0) return 0f
        return (estimatedPositionMs().toFloat() / durationMs).coerceIn(0f, 1f)
    }

    companion object {
        val EMPTY = MusicState()
    }
}
