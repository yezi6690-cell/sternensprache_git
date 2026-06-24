package com.mindisle.app.floating

import android.graphics.Rect
import android.view.WindowManager

class FloatingLive2DController(
    private val view: FloatingLive2DView,
    private val params: WindowManager.LayoutParams
) {
    fun lookAt(x: Float, y: Float) {
        view.lookAt(x, y)
    }

    fun playTapReaction(x: Float, y: Float) {
        view.playTapReaction(x, y)
    }

    fun resetLookAt() {
        view.resetLookAt()
    }

    fun showBubble(text: String) {
        view.showBubble(text)
    }

    fun getFloatingWindowBounds(): Rect {
        val width = if (params.width > 0) params.width else view.width
        val height = if (params.height > 0) params.height else view.height
        return Rect(params.x, params.y, params.x + width, params.y + height)
    }
}
