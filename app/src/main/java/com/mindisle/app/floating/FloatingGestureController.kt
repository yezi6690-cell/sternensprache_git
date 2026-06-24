package com.mindisle.app.floating

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot

class FloatingGestureController(
    private val rootView: View,
    private val touchTargets: List<View>,
    private val params: WindowManager.LayoutParams,
    private val windowManager: WindowManager,
    private val settings: FloatingSettingsManager,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onInteractionStart()
        fun onTouchLookAt(x: Float, y: Float)
        fun onClick()
        fun onDoubleClick()
        fun onTripleClick()
        fun onPositionChanged(x: Int, y: Int)
        fun onInteractionEnd(wasDragging: Boolean)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val dragSlop = (10f * rootView.resources.displayMetrics.density + 0.5f).toInt()
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var lastTapTime = 0L
    private var tapCount = 0
    private val finishMultiClick = Runnable {
        if (tapCount == 2) {
            callbacks.onDoubleClick()
        }
        tapCount = 0
        lastTapTime = 0L
    }

    fun attach() {
        touchTargets.forEach { target ->
            target.setOnTouchListener { view, event -> handleTouch(view, event) }
        }
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                callbacks.onInteractionStart()
                downRawX = event.rawX
                downRawY = event.rawY
                downTime = event.eventTime
                startX = params.x
                startY = params.y
                moved = false
                callbacks.onTouchLookAt(toLive2DX(view, event), toLive2DY(view, event))
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (hypot(dx, dy) > dragSlop) {
                    moved = true
                }
                if (moved) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    windowManager.updateViewLayout(rootView, params)
                    callbacks.onPositionChanged(params.x, params.y)
                } else {
                    callbacks.onTouchLookAt(toLive2DX(view, event), toLive2DY(view, event))
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (moved) {
                    settings.saveWindowPosition(params.x, params.y)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val now = System.currentTimeMillis()
                    tapCount = if (now - lastTapTime < MULTI_CLICK_TIMEOUT_MS) tapCount + 1 else 1
                    lastTapTime = now
                    handler.removeCallbacks(finishMultiClick)
                    when {
                        tapCount >= 3 -> {
                            tapCount = 0
                            lastTapTime = 0L
                            callbacks.onTripleClick()
                        }
                        tapCount == 2 -> {
                            handler.postDelayed(finishMultiClick, MULTI_CLICK_TIMEOUT_MS)
                        }
                        event.eventTime - downTime < CLICK_TIMEOUT_MS -> {
                            callbacks.onClick()
                            handler.postDelayed(finishMultiClick, MULTI_CLICK_TIMEOUT_MS)
                        }
                    }
                }
                callbacks.onInteractionEnd(moved)
                true
            }
            else -> false
        }
    }

    private fun toLive2DX(view: View, event: MotionEvent): Float {
        val width = view.width.takeIf { it > 0 } ?: return 0f
        return (event.x / width * 2f - 1f).coerceIn(-1f, 1f)
    }

    private fun toLive2DY(view: View, event: MotionEvent): Float {
        val height = view.height.takeIf { it > 0 } ?: return 0f
        return (1f - event.y / height * 2f).coerceIn(-1f, 1f)
    }

    companion object {
        private const val CLICK_TIMEOUT_MS = 250L
        private const val MULTI_CLICK_TIMEOUT_MS = 320L
    }
}
