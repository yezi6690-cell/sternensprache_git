package com.mindisle.app.relax

import android.content.Context
import android.view.View

abstract class ClassicGameView(context: Context) : View(context) {
    var onScoreChanged: (Int) -> Unit = {}
    var onGameOver: (Int) -> Unit = {}

    var score: Int = 0
        protected set(value) {
            field = value
            onScoreChanged(value)
        }

    var isPaused: Boolean = true
        protected set

    var isGameOver: Boolean = false
        protected set

    abstract fun restartGame()

    open fun pauseGame() {
        isPaused = true
    }

    open fun resumeGame() {
        if (!isGameOver) isPaused = false
    }

    open fun releaseGame() {
        isPaused = true
    }

    protected fun finishGame() {
        if (isGameOver) return
        isGameOver = true
        isPaused = true
        onGameOver(score)
    }

    protected fun dp(value: Float): Float = value * resources.displayMetrics.density
}
