package com.mindisle.app.ai

data class AiRole(
    val roleId: String,
    val modelId: String,
    val displayName: String,
    val personaFileName: String,
    val live2dModelPath: String,
    val enabled: Boolean = true,
    val description: String = "",
    val personalityPrompt: String = "",
    val speakingStyle: String = "",
    val relationshipWithUser: String = "",
    val welcomeMessage: String = "",
    val live2d: Live2DRoleConfig = Live2DRoleConfig()
)

data class Live2DRoleConfig(
    val modelPath: String = "",
    val defaultExpression: String = "",
    val defaultMotion: String = "",
    val expressions: List<String> = emptyList(),
    val motions: List<String> = emptyList()
)
