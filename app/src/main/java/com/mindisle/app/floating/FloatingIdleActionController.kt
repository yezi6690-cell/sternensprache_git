package com.mindisle.app.floating

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

class FloatingIdleActionController(
    private val view: FloatingLive2DView
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var idleActionCount = 0

    private val idleRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            playRandomIdleAction()
            scheduleNext()
        }
    }

    fun start() {
        running = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(idleRunnable, START_DELAY_MS)
    }

    fun pause() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    fun resumeLater() {
        running = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(idleRunnable, RESUME_DELAY_MS)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun playRandomIdleAction() {
        idleActionCount += 1
        val action = IDLE_ACTIONS.random()
        if (action == "randomBubble" || idleActionCount % Random.nextInt(3, 6) == 0) {
            view.showBubble(IDLE_BUBBLES.random())
            if (action == "randomBubble") return
        }
        view.playIdleAction(action)
    }

    private fun scheduleNext() {
        handler.postDelayed(idleRunnable, Random.nextLong(MIN_IDLE_INTERVAL_MS, MAX_IDLE_INTERVAL_MS))
    }

    companion object {
        private const val START_DELAY_MS = 3000L
        private const val RESUME_DELAY_MS = 3000L
        private const val MIN_IDLE_INTERVAL_MS = 5000L
        private const val MAX_IDLE_INTERVAL_MS = 12000L

        private val IDLE_ACTIONS = arrayOf(
            "blink",
            "lookLeft",
            "lookRight",
            "lookUp",
            "lookDown",
            "smile",
            "breathe",
            "reset",
            "randomBubble"
        )

        private val IDLE_BUBBLES = arrayOf(
            "我在这里陪你。",
            "要不要休息一下？",
            "今天也要慢慢来。",
            "我会安静地陪着你。",
            "需要我的时候点我就好。",
            "别忘了喝水。",
            "要不要开始一次专注？"
        )
    }
}
