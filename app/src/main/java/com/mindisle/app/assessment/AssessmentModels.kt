package com.mindisle.app.assessment

import androidx.annotation.DrawableRes

data class AssessmentQuestion(
    val text: String,
    val dimension: String,
    val reverse: Boolean = false
)

data class AssessmentDefinition(
    val id: String,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    @DrawableRes val iconRes: Int,
    val questions: List<AssessmentQuestion>
)

data class AssessmentRecord(
    val id: Long,
    val type: String,
    val title: String,
    val score: Int,
    val level: String,
    val summary: String,
    val createdAt: Long,
    val answers: List<Int>,
    val dimensionScores: Map<String, Int>,
    val suggestions: List<String> = emptyList()
)
