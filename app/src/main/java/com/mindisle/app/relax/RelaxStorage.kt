package com.mindisle.app.relax

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RelaxStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): List<RelaxRecord> {
        val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        RelaxRecord(
                            id = item.optLong("id"),
                            type = item.optString("type"),
                            title = item.optString("title"),
                            durationSeconds = item.optInt("durationSeconds"),
                            resultText = item.optString("resultText"),
                            createdAt = item.optLong("createdAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(record: RelaxRecord) {
        val records = (listOf(record) + load().filterNot { it.id == record.id }).take(MAX_RECORDS)
        val array = JSONArray()
        records.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", item.type)
                    .put("title", item.title)
                    .put("durationSeconds", item.durationSeconds)
                    .put("resultText", item.resultText)
                    .put("createdAt", item.createdAt)
            )
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    fun getGameHighScore(gameType: String): Int =
        preferences.getInt("$KEY_HIGH_SCORE_PREFIX$gameType", 0)

    fun updateGameHighScore(gameType: String, score: Int): Int {
        val highScore = maxOf(score, getGameHighScore(gameType))
        preferences.edit().putInt("$KEY_HIGH_SCORE_PREFIX$gameType", highScore).apply()
        return highScore
    }

    fun loadGameRecords(): List<RelaxGameRecord> {
        val raw = preferences.getString(KEY_GAME_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        RelaxGameRecord(
                            id = item.optLong("id"),
                            gameType = item.optString("gameType"),
                            gameName = item.optString("gameName"),
                            score = item.optInt("score"),
                            createdAt = item.optLong("createdAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveGameRecord(record: RelaxGameRecord) {
        val records = (listOf(record) + loadGameRecords().filterNot { it.id == record.id })
            .take(MAX_GAME_RECORDS)
        val array = JSONArray()
        records.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("gameType", item.gameType)
                    .put("gameName", item.gameName)
                    .put("score", item.score)
                    .put("createdAt", item.createdAt)
            )
        }
        preferences.edit().putString(KEY_GAME_RECORDS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "mindisle_relax_records"
        private const val KEY_RECORDS = "records"
        private const val KEY_GAME_RECORDS = "game_records"
        private const val KEY_HIGH_SCORE_PREFIX = "game_high_score_"
        private const val MAX_RECORDS = 10
        private const val MAX_GAME_RECORDS = 12
    }
}
