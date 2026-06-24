package com.mindisle.app.floating

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class FloatingControlGestureController(
    private val button: View,
    private val currentPosition: () -> Pair<Int, Int>,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onDragStart()
        fun onDrag(x: Int, y: Int)
        fun onDragEnd(x: Int, y: Int)
        fun onDoubleTap()
        fun onTripleTap()
        fun onInteractionEnd()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val dragSlop = dp(8)
    private var startX = 0
    private var startY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false
    private var tapCount = 0
    private var lastTapTime = 0L

    private val finishTapSequence = Runnable {
        if (tapCount == 2) {
            callbacks.onDoubleTap()
        }
        tapCount = 0
        lastTapTime = 0L
    }

    fun attach() {
        button.setOnTouchListener { _, event -> handleTouch(event) }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        button.setOnTouchListener(null)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val position = currentPosition()
                startX = position.first
                startY = position.second
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                callbacks.onDragStart()
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && hypot(dx, dy) > dragSlop) {
                    dragging = true
                    handler.removeCallbacks(finishTapSequence)
                    tapCount = 0
                }
                if (dragging) {
                    callbacks.onDrag(startX + dx.toInt(), startY + dy.toInt())
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    callbacks.onDragEnd(startX + dx.toInt(), startY + dy.toInt())
                } else {
                    handleTap()
                }
                callbacks.onInteractionEnd()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                callbacks.onInteractionEnd()
                true
            }

            else -> false
        }
    }

    private fun handleTap() {
        val now = System.currentTimeMillis()
        tapCount = if (now - lastTapTime <= MULTI_TAP_WINDOW_MS) tapCount + 1 else 1
        lastTapTime = now
        handler.removeCallbacks(finishTapSequence)

        if (tapCount >= 3) {
            tapCount = 0
            lastTapTime = 0L
            callbacks.onTripleTap()
        } else {
            handler.postDelayed(finishTapSequence, TAP_DECISION_DELAY_MS)
        }
    }

    private fun dp(value: Int): Float {
        return value * button.resources.displayMetrics.density
    }

    companion object {
        private const val MULTI_TAP_WINDOW_MS = 500L
        private const val TAP_DECISION_DELAY_MS = 280L
    }
}
