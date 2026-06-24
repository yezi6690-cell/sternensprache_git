package com.mindisle.app.ai

import android.util.Log

class AiActionExecutor(private val host: Host) {
    interface Host {
        fun currentRoleId(): String
        fun currentModelId(): String
        fun currentPage(): String
        fun confirm(action: AiAction, onConfirm: () -> Unit)
        fun openPage(page: String): AiActionResult
        fun switchRole(modelId: String): AiActionResult
        fun setExpression(expression: String): AiActionResult
        fun playMotion(motion: String): AiActionResult
        fun clearCurrentRoleHistory(): AiActionResult
        fun openMusicPanel(): AiActionResult
        fun playMusic(): AiActionResult
        fun pauseMusic(): AiActionResult
        fun nextMusic(): AiActionResult
        fun previousMusic(): AiActionResult
        fun togglePlayMusic(): AiActionResult
        fun openMusicList(): AiActionResult
        fun setMusicPlayMode(mode: String): AiActionResult
        fun toggleMusicPlayMode(): AiActionResult
        fun openFeature(actionType: String): AiActionResult
        fun showActionFailure(message: String)
    }

    fun execute(actions: List<AiAction>) {
        Log.d(TAG, "received actions count=${actions.size}")
        actions.forEach { action ->
            Log.d(TAG, "action type=${action.type} params=${action.params}")
            if (action.type.startsWith("music.")) {
                Log.d(TAG, "music action type=${action.type}")
                Log.d(TAG, "music params=${action.params}")
                Log.d(TAG, "current page=${host.currentPage()}")
                Log.d(TAG, "music action navigateToRelax=false")
                Log.d(TAG, "use global MusicPlayerManager")
                Log.d(TAG, "use RelaxFragment=false")
            }
            val validation = AiActionRegistry.validate(action)
            Log.d(TAG, "action allowed=${validation.isAllowed}")
            if (!validation.isAllowed) {
                Log.w(
                    TAG,
                    "ignored unknown action=${action.type} reason=${validation.reason}"
                )
                return@forEach
            }

            val needConfirm = AiActionRegistry.requiresConfirmation(action.type)
            Log.d(TAG, "action allowed type=${action.type}")
            Log.d(TAG, "action needConfirm=$needConfirm")
            if (needConfirm) {
                host.confirm(action) { executeAllowed(action) }
            } else {
                executeAllowed(action)
            }
        }
    }

    private fun executeAllowed(action: AiAction) {
        val result = runCatching {
            when (action.type) {
                "music.openPanel" -> host.openMusicPanel()
                "music.play" -> host.playMusic()
                "music.pause" -> host.pauseMusic()
                "music.togglePlay" -> host.togglePlayMusic()
                "music.next" -> host.nextMusic()
                "music.previous" -> host.previousMusic()
                "music.openList" -> host.openMusicList()
                "music.setPlayMode" ->
                    host.setMusicPlayMode(action.params.getValue("mode") as String)
                "music.togglePlayMode" -> host.toggleMusicPlayMode()
                else -> AiActionResult.failure(action.type, "unknown action")
            }
        }.getOrElse { error ->
            AiActionResult.failure(action.type, error.message ?: "action execution failed")
        }
        Log.d(
            TAG,
            "action result type=${result.actionType} success=${result.isSuccess} " +
                "message=${result.message} current roleId=${host.currentRoleId()} " +
                "current modelId=${host.currentModelId()} current page=${host.currentPage()}"
        )
        if (action.type.startsWith("music.")) {
            Log.d(
                TAG,
                "music action result success=${result.isSuccess} message=${result.message}"
            )
        }
        if (!result.isSuccess) {
            host.showActionFailure(result.message)
        }
    }

    private companion object {
        const val TAG = "MindIsleAi"
    }
}
