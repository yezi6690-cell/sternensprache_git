package com.mindisle.app.live2d

import kotlin.random.Random

class Live2DTouchController {
    private val bubbles = listOf(
        "我在呢。",
        "今天也要慢慢来。",
        "要不要开始一次专注？",
        "别忘了休息一下。"
    )

    fun randomBubble(): String = bubbles[Random.nextInt(bubbles.size)]
}
