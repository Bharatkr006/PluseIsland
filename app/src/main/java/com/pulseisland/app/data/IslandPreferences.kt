package com.pulseisland.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists and coordinates user customization for PulseIsland size and position.
 * Values remain fixed until explicitly changed by the user.
 */
object IslandPreferences {

    private const val PREFS_NAME = "pulse_island_settings"

    private const val KEY_X_OFFSET = "key_island_x_offset"
    private const val KEY_Y_OFFSET = "key_island_y_offset"
    private const val KEY_SCALE = "key_island_scale"

    // Default configuration values
    const val DEFAULT_X_OFFSET_DP = 0
    const val DEFAULT_Y_OFFSET_DP = 14
    const val DEFAULT_SCALE = 1.0f

    const val MIN_X_OFFSET_DP = -150
    const val MAX_X_OFFSET_DP = 150

    const val MIN_Y_OFFSET_DP = 0
    const val MAX_Y_OFFSET_DP = 90

    const val MIN_SCALE = 0.8f
    const val MAX_SCALE = 1.3f

    data class IslandConfig(
        val xOffsetDp: Int,
        val yOffsetDp: Int,
        val scale: Float
    )

    private val _configFlow = MutableStateFlow(
        IslandConfig(DEFAULT_X_OFFSET_DP, DEFAULT_Y_OFFSET_DP, DEFAULT_SCALE)
    )
    val configFlow: StateFlow<IslandConfig> = _configFlow.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes config from stored preferences.
     */
    fun init(context: Context) {
        val prefs = getPrefs(context)
        val x = prefs.getInt(KEY_X_OFFSET, DEFAULT_X_OFFSET_DP)
        val y = prefs.getInt(KEY_Y_OFFSET, DEFAULT_Y_OFFSET_DP)
        val scale = prefs.getFloat(KEY_SCALE, DEFAULT_SCALE)
        _configFlow.value = IslandConfig(x, y, scale)
    }

    fun getXOffsetDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_X_OFFSET, DEFAULT_X_OFFSET_DP)
    }

    fun setXOffsetDp(context: Context, xOffsetDp: Int) {
        val clamped = xOffsetDp.coerceIn(MIN_X_OFFSET_DP, MAX_X_OFFSET_DP)
        getPrefs(context).edit().putInt(KEY_X_OFFSET, clamped).apply()
        _configFlow.value = _configFlow.value.copy(xOffsetDp = clamped)
    }

    fun getYOffsetDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_Y_OFFSET, DEFAULT_Y_OFFSET_DP)
    }

    fun setYOffsetDp(context: Context, yOffsetDp: Int) {
        val clamped = yOffsetDp.coerceIn(MIN_Y_OFFSET_DP, MAX_Y_OFFSET_DP)
        getPrefs(context).edit().putInt(KEY_Y_OFFSET, clamped).apply()
        _configFlow.value = _configFlow.value.copy(yOffsetDp = clamped)
    }

    fun getScale(context: Context): Float {
        return getPrefs(context).getFloat(KEY_SCALE, DEFAULT_SCALE)
    }

    fun setScale(context: Context, scale: Float) {
        val clamped = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        getPrefs(context).edit().putFloat(KEY_SCALE, clamped).apply()
        _configFlow.value = _configFlow.value.copy(scale = clamped)
    }

    /**
     * Resets customization back to standard defaults.
     */
    fun resetToDefaults(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_X_OFFSET, DEFAULT_X_OFFSET_DP)
            .putInt(KEY_Y_OFFSET, DEFAULT_Y_OFFSET_DP)
            .putFloat(KEY_SCALE, DEFAULT_SCALE)
            .apply()
        _configFlow.value = IslandConfig(DEFAULT_X_OFFSET_DP, DEFAULT_Y_OFFSET_DP, DEFAULT_SCALE)
    }
}
