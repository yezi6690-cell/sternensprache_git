package com.mindisle.app.ai

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ChatStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun historyKey(modelId: String): String = KEY_PREFIX + normalizeModelId(modelId)

    fun load(modelId: String): MutableList<ChatHistoryItem> {
        val key = historyKey(modelId)
        migrateLegacyHistoryIfNeeded(key)
        val raw = preferences.getString(key, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val role = item.optString("role")
                    val content = item.optString("content")
                    if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                        add(ChatHistoryItem(role, content))
                    }
                }
            }.takeLast(MAX_MESSAGES).toMutableList()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to load saved chat messages", error)
            mutableListOf()
        }
    }

    fun save(modelId: String, messages: List<ChatHistoryItem>) {
        val array = JSONArray()
        messages.takeLast(MAX_MESSAGES).forEach { message ->
            array.put(
                JSONObject()
                    .put("role", message.role)
                    .put("content", message.content)
            )
        }
        preferences.edit().putString(historyKey(modelId), array.toString()).apply()
    }

    fun clear(modelId: String) {
        preferences.edit().remove(historyKey(modelId)).apply()
    }

    private fun migrateLegacyHistoryIfNeeded(targetKey: String) {
        if (preferences.contains(targetKey) || !preferences.contains(LEGACY_KEY_MESSAGES)) return
        val legacyMessages = preferences.getString(LEGACY_KEY_MESSAGES, null)
        preferences.edit()
            .apply {
                if (!legacyMessages.isNullOrBlank()) {
                    putString(targetKey, legacyMessages)
                }
                remove(LEGACY_KEY_MESSAGES)
            }
            .apply()
    }

    private fun normalizeModelId(modelId: String): String {
        val normalized = modelId.trim().lowercase()
        return normalized.takeIf { it.matches(Regex("[a-z0-9_]+")) } ?: "default"
    }

    companion object {
        private const val TAG = "MindIsleChatStorage"
        private const val PREFS_NAME = "mindisle_chat_storage"
        private const val KEY_PREFIX = "chat_history_"
        private const val LEGACY_KEY_MESSAGES = "recent_messages"
        private const val MAX_MESSAGES = 100
    }
}
