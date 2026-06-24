package com.mindisle.app.relax

data class RelaxRecord(
    val id: Long,
    val type: String,
    val title: String,
    val durationSeconds: Int,
    val resultText: String,
    val createdAt: Long
)

data class RelaxGameRecord(
    val id: Long,
    val gameType: String,
    val gameName: String,
    val score: Int,
    val createdAt: Long
)
