package com.mindisle.app.chat

data class CompanionLine(
    val sender: String,
    val text: String,
    val timeMillis: Long = System.currentTimeMillis()
)

class ChatRepository {
    private val lines = mutableListOf<CompanionLine>()

    fun add(sender: String, text: String) {
        lines.add(CompanionLine(sender, text))
    }

    fun latestReply(): String {
        return lines.lastOrNull { it.sender == "心屿" }?.text ?: "今天想让我陪你做点什么？"
    }

    fun all(): List<CompanionLine> = lines.toList()
}
