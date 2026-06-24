package com.mindisle.app.floating

import android.content.Context

class FloatingInteractionStatusStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(eventReceived: Boolean, coordinateValid: Boolean, reaction: String) {
        prefs.edit()
            .putBoolean(KEY_EVENT_RECEIVED, eventReceived)
            .putBoolean(KEY_COORDINATE_VALID, coordinateValid)
            .putString(KEY_REACTION, reaction)
            .apply()
    }

    fun readSummary(): String {
        val eventText = if (prefs.getBoolean(KEY_EVENT_RECEIVED, false)) "已收到" else "未收到"
        val coordinateText = if (prefs.getBoolean(KEY_COORDINATE_VALID, false)) "有效" else "无效"
        val reactionText = prefs.getString(KEY_REACTION, "暂无") ?: "暂无"
        return "最近一次外部事件：$eventText\n最近一次坐标：$coordinateText\n最近一次反应：$reactionText"
    }

    companion object {
        private const val PREFS_NAME = "floating_interaction_status"
        private const val KEY_EVENT_RECEIVED = "event_received"
        private const val KEY_COORDINATE_VALID = "coordinate_valid"
        private const val KEY_REACTION = "reaction"
    }
}
