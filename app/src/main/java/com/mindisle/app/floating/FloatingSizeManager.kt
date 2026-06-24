package com.mindisle.app.floating

import android.content.Context
import kotlin.math.roundToInt

class FloatingSizeManager(
    private val context: Context,
    private val settings: FloatingSettingsManager
) {
    enum class Preset(val key: String, val widthDp: Int, val heightDp: Int) {
        SMALL("small", 200, 320),
        MIDDLE("middle", 240, 380),
        LARGE("large", 280, 440)
    }

    fun currentPreset(): Preset {
        return presetByKey(settings.windowSizePreset)
    }

    fun applyPreset(preset: Preset) {
        settings.windowSizePreset = preset.key
        settings.windowWidthDp = preset.widthDp
        settings.windowHeightDp = preset.heightDp
    }

    data class AutoSize(
        val widthDp: Int,
        val heightDp: Int
    )

    fun resetToDefault() {
        settings.modelScale = DEFAULT_SCALE_MULTIPLIER
        settings.modelOffsetX = 0f
        settings.modelOffsetY = 0f
        settings.windowWidthDp = DEFAULT_WINDOW_WIDTH_DP
        settings.windowHeightDp = DEFAULT_WINDOW_HEIGHT_DP
    }

    fun calculateAutoSize(boundsWidth: Float, boundsHeight: Float, multiplier: Float): AutoSize {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
        val safeWidth = boundsWidth.takeIf { it > 1f } ?: DEFAULT_WINDOW_WIDTH_DP.toFloat()
        val safeHeight = boundsHeight.takeIf { it > 1f } ?: DEFAULT_WINDOW_HEIGHT_DP.toFloat()
        val aspectRatio = (safeWidth / safeHeight).coerceIn(0.35f, 1.2f)
        val padding = DEFAULT_PADDING_DP
        val targetHeight = (screenHeightDp * 0.42f * multiplier).coerceIn(220f, screenHeightDp * 0.60f)
        val targetWidth = (targetHeight * aspectRatio).coerceIn(120f, screenWidthDp * 0.45f)
        return AutoSize(
            widthDp = (targetWidth + padding * 2).roundToInt().coerceIn(MIN_WINDOW_WIDTH_DP, MAX_WINDOW_WIDTH_DP),
            heightDp = (targetHeight + padding * 2).roundToInt().coerceIn(MIN_WINDOW_HEIGHT_DP, MAX_WINDOW_HEIGHT_DP)
        )
    }

    private fun presetByKey(key: String): Preset {
        return Preset.values().firstOrNull { it.key == key } ?: Preset.MIDDLE
    }

    companion object {
        const val MIN_SCALE_MULTIPLIER = 0.8f
        const val MAX_SCALE_MULTIPLIER = 1.2f
        const val DEFAULT_SCALE_MULTIPLIER = 1.0f
        const val DEFAULT_WINDOW_WIDTH_DP = 240
        const val DEFAULT_WINDOW_HEIGHT_DP = 380
        const val DEFAULT_PADDING_DP = 14
        const val MIN_WINDOW_WIDTH_DP = 120
        const val MAX_WINDOW_WIDTH_DP = 360
        const val MIN_WINDOW_HEIGHT_DP = 220
        const val MAX_WINDOW_HEIGHT_DP = 620
    }
}
