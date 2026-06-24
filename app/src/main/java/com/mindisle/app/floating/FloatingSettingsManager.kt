package com.mindisle.app.floating

import android.content.Context
import kotlin.jvm.JvmName

class FloatingSettingsManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var x: Int
        get() = prefs.getInt(KEY_WINDOW_X, dp(DEFAULT_WINDOW_X_DP))
        set(value) = prefs.edit().putInt(KEY_WINDOW_X, value).apply()

    var y: Int
        get() = prefs.getInt(KEY_WINDOW_Y, dp(DEFAULT_WINDOW_Y_DP))
        set(value) = prefs.edit().putInt(KEY_WINDOW_Y, value).apply()

    var windowWidthDp: Int
        get() = prefs.getInt(KEY_WINDOW_WIDTH_DP, DEFAULT_WINDOW_WIDTH_DP)
        set(value) = prefs.edit().putInt(KEY_WINDOW_WIDTH_DP, value.coerceIn(120, 360)).apply()

    var windowHeightDp: Int
        get() = prefs.getInt(KEY_WINDOW_HEIGHT_DP, DEFAULT_WINDOW_HEIGHT_DP)
        set(value) = prefs.edit().putInt(KEY_WINDOW_HEIGHT_DP, value.coerceIn(160, 620)).apply()

    var floatingModelId: String?
        get() = prefs.getString(KEY_FLOATING_MODEL_ID, null)
        set(value) = prefs.edit().putString(KEY_FLOATING_MODEL_ID, value).apply()

    var floatingVariant: String?
        get() = prefs.getString(KEY_FLOATING_VARIANT, null)
        set(value) = prefs.edit().putString(KEY_FLOATING_VARIANT, value).apply()

    var modelScale: Float
        get() = prefs.getFloat(KEY_MODEL_SCALE, DEFAULT_MODEL_SCALE).coerceIn(0.8f, 1.2f)
        set(value) = prefs.edit().putFloat(KEY_MODEL_SCALE, value.coerceIn(0.8f, 1.2f)).apply()

    var windowPaddingDp: Int
        get() = prefs.getInt(KEY_WINDOW_PADDING_DP, DEFAULT_WINDOW_PADDING_DP)
        set(value) = prefs.edit().putInt(KEY_WINDOW_PADDING_DP, value.coerceIn(8, 16)).apply()

    var windowSizePreset: String
        get() = prefs.getString(KEY_WINDOW_SIZE_PRESET, DEFAULT_WINDOW_SIZE_PRESET) ?: DEFAULT_WINDOW_SIZE_PRESET
        set(value) = prefs.edit().putString(KEY_WINDOW_SIZE_PRESET, value).apply()

    var modelOffsetX: Float
        get() = prefs.getFloat(KEY_MODEL_OFFSET_X, 0f)
        set(value) = prefs.edit().putFloat(KEY_MODEL_OFFSET_X, value).apply()

    var modelOffsetY: Float
        get() = prefs.getFloat(KEY_MODEL_OFFSET_Y, 0f)
        set(value) = prefs.edit().putFloat(KEY_MODEL_OFFSET_Y, value).apply()

    var transparentBackground: Boolean
        get() = prefs.getBoolean(KEY_TRANSPARENT_BACKGROUND, true)
        set(value) = prefs.edit().putBoolean(KEY_TRANSPARENT_BACKGROUND, value).apply()

    var enhancedInteractionEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENHANCED_INTERACTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENHANCED_INTERACTION_ENABLED, value).apply()

    fun getWindowX(): Int = x

    fun getWindowY(): Int = y

    fun saveWindowPosition(nextX: Int, nextY: Int) {
        prefs.edit()
            .putInt(KEY_WINDOW_X, nextX)
            .putInt(KEY_WINDOW_Y, nextY)
            .apply()
    }

    @JvmName("readWindowWidthDp")
    fun getWindowWidthDp(): Int = windowWidthDp

    @JvmName("readWindowHeightDp")
    fun getWindowHeightDp(): Int = windowHeightDp

    @JvmName("readModelScale")
    fun getModelScale(): Float = modelScale

    fun saveModelScale(scale: Float) {
        modelScale = scale
    }

    fun saveFloatingSelection(modelId: String, variant: String?) {
        prefs.edit()
            .putString(KEY_FLOATING_MODEL_ID, modelId)
            .putString(KEY_FLOATING_VARIANT, variant)
            .apply()
    }

    fun resetPosition() {
        saveWindowPosition(dp(DEFAULT_WINDOW_X_DP), dp(DEFAULT_WINDOW_Y_DP))
    }

    fun reset() {
        prefs.edit()
            .putBoolean(KEY_ENABLED, false)
            .putInt(KEY_WINDOW_X, dp(DEFAULT_WINDOW_X_DP))
            .putInt(KEY_WINDOW_Y, dp(DEFAULT_WINDOW_Y_DP))
            .putInt(KEY_WINDOW_WIDTH_DP, DEFAULT_WINDOW_WIDTH_DP)
            .putInt(KEY_WINDOW_HEIGHT_DP, DEFAULT_WINDOW_HEIGHT_DP)
            .putInt(KEY_WINDOW_PADDING_DP, DEFAULT_WINDOW_PADDING_DP)
            .putString(KEY_WINDOW_SIZE_PRESET, DEFAULT_WINDOW_SIZE_PRESET)
            .putFloat(KEY_MODEL_SCALE, DEFAULT_MODEL_SCALE)
            .putFloat(KEY_MODEL_OFFSET_X, 0f)
            .putFloat(KEY_MODEL_OFFSET_Y, 0f)
            .putBoolean(KEY_TRANSPARENT_BACKGROUND, true)
            .putBoolean(KEY_ENHANCED_INTERACTION_ENABLED, false)
            .remove(KEY_FLOATING_MODEL_ID)
            .remove(KEY_FLOATING_VARIANT)
            .apply()
    }

    private fun dp(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density + 0.5f).toInt()
    }

    companion object {
        private const val PREFS_NAME = "floating_live2d_settings"
        private const val KEY_ENABLED = "floating_enabled"
        private const val KEY_WINDOW_X = "floating_window_x"
        private const val KEY_WINDOW_Y = "floating_window_y"
        private const val KEY_WINDOW_WIDTH_DP = "floating_window_width_dp"
        private const val KEY_WINDOW_HEIGHT_DP = "floating_window_height_dp"
        private const val KEY_WINDOW_PADDING_DP = "floating_window_padding_dp"
        private const val KEY_WINDOW_SIZE_PRESET = "floating_window_size_preset"
        private const val KEY_MODEL_SCALE = "floating_model_scale"
        private const val KEY_MODEL_OFFSET_X = "floating_model_offset_x"
        private const val KEY_MODEL_OFFSET_Y = "floating_model_offset_y"
        private const val KEY_TRANSPARENT_BACKGROUND = "floating_transparent_background"
        private const val KEY_ENHANCED_INTERACTION_ENABLED = "floating_enhanced_interaction_enabled"
        private const val KEY_FLOATING_MODEL_ID = "floating_model_id"
        private const val KEY_FLOATING_VARIANT = "floating_model_variant"

        private const val DEFAULT_WINDOW_X_DP = 80
        private const val DEFAULT_WINDOW_Y_DP = 260
        private const val DEFAULT_WINDOW_WIDTH_DP = 240
        private const val DEFAULT_WINDOW_HEIGHT_DP = 380
        private const val DEFAULT_WINDOW_PADDING_DP = 12
        private const val DEFAULT_WINDOW_SIZE_PRESET = "middle"
        private const val DEFAULT_MODEL_SCALE = 1.0f
    }
}
