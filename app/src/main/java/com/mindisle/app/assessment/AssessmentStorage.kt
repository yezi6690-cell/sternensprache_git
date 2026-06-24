package com.mindisle.app.assessment

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AssessmentStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): List<AssessmentRecord> {
        val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val dimensionsJson = item.optJSONObject("dimensionScores") ?: JSONObject()
                    val dimensions = linkedMapOf<String, Int>()
                    val keys = dimensionsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        dimensions[key] = dimensionsJson.optInt(key)
                    }
                    val suggestions = item.optJSONArray("suggestions")?.let { suggestionsJson ->
                        buildList {
                            for (suggestionIndex in 0 until suggestionsJson.length()) {
                                suggestionsJson.optString(suggestionIndex)
                                    .takeIf(String::isNotBlank)
                                    ?.let(::add)
                            }
                        }
                    }.orEmpty()
                    add(
                        AssessmentRecord(
                            id = item.optLong("id"),
                            type = item.optString("type"),
                            title = item.optString("title"),
                            score = item.optInt("score"),
                            level = item.optString("level"),
                            summary = item.optString("summary"),
                            createdAt = item.optLong("createdAt"),
                            answers = item.optJSONArray("answers")?.let { answersJson ->
                                buildList {
                                    for (answerIndex in 0 until answersJson.length()) {
                                        add(answersJson.optInt(answerIndex))
                                    }
                                }
                            }.orEmpty(),
                            dimensionScores = dimensions,
                            suggestions = suggestions
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(record: AssessmentRecord) {
        val records = (listOf(record) + load().filterNot { it.id == record.id }).take(MAX_RECORDS)
        val array = JSONArray()
        records.forEach { item ->
            val dimensions = JSONObject()
            val answers = JSONArray()
            val suggestions = JSONArray()
            item.dimensionScores.forEach { (name, score) -> dimensions.put(name, score) }
            item.answers.forEach(answers::put)
            item.suggestions.forEach(suggestions::put)
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", item.type)
                    .put("title", item.title)
                    .put("score", item.score)
                    .put("level", item.level)
                    .put("summary", item.summary)
                    .put("createdAt", item.createdAt)
                    .put("answers", answers)
                    .put("dimensionScores", dimensions)
                    .put("suggestions", suggestions)
            )
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "mindisle_assessment_records"
        private const val KEY_RECORDS = "records"
        private const val MAX_RECORDS = 10
    }
}
