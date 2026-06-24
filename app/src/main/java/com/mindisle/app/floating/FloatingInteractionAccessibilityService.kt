package com.mindisle.app.floating

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.max

class FloatingInteractionAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val sourceBounds = Rect()
    private val statusStore by lazy { FloatingInteractionStatusStore(this) }
    private val settings by lazy { FloatingSettingsManager(this) }
    private var reactionCount = 0
    private var lastBubbleTarget: Pair<Int, Int>? = null
    private var lastControllerLogAt = 0L
    private val lastReactAtByType = mutableMapOf<Int, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val nextEvent = event ?: return
        val type = nextEvent.eventType
        if (!FEATURE_ENABLED) return
        if (!settings.enhancedInteractionEnabled) return
        if (!isSupportedEvent(type) || shouldThrottle(type)) return

        val controller = FloatingLive2DBridge.current() ?: return
        logControllerFound()

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            runFallbackReaction(controller)
            return
        }

        val target = findTargetCenter(nextEvent)
        if (target == null) {
            runFallbackReaction(controller)
            return
        }

        val floatingBounds = controller.getFloatingWindowBounds()
        val petCenterX = floatingBounds.centerX()
        val petCenterY = floatingBounds.centerY()
        val metrics = resources.displayMetrics
        val screenWidth = max(metrics.widthPixels, 1)
        val screenHeight = max(metrics.heightPixels, 1)
        val live2dX = ((target.first - petCenterX).toFloat() / (screenWidth * 0.5f))
            .coerceIn(-1f, 1f)
        val live2dY = (-(target.second - petCenterY).toFloat() / (screenHeight * 0.5f))
            .coerceIn(-1f, 1f)

        Log.d(TAG, "send lookAt x=$live2dX y=$live2dY")
        controller.lookAt(live2dX, live2dY)
        controller.playTapReaction(live2dX, live2dY)
        maybeShowBubble(controller, target = target)
        scheduleReset(controller)
        statusStore.save(eventReceived = true, coordinateValid = true, reaction = "方向反应")
    }

    override fun onInterrupt() = Unit

    private fun runFallbackReaction(controller: FloatingLive2DController) {
        Log.d(TAG, "fallback reaction")
        controller.playTapReaction(0f, 0f)
        maybeShowBubble(controller, fallback = true)
        scheduleReset(controller)
        statusStore.save(eventReceived = true, coordinateValid = false, reaction = "兜底反应")
    }

    private fun findTargetCenter(event: AccessibilityEvent): Pair<Int, Int>? {
        readBoundsCenter(event.source)?.let { return it }
        val root = rootInActiveWindow ?: return null
        return try {
            findBestNodeBounds(root)?.let { Pair(it.centerX(), it.centerY()) }
        } finally {
            root.recycle()
        }
    }

    private fun readBoundsCenter(source: AccessibilityNodeInfo?): Pair<Int, Int>? {
        source ?: return null
        return try {
            source.getBoundsInScreen(sourceBounds)
            if (isValidBounds(sourceBounds)) {
                Pair(sourceBounds.centerX(), sourceBounds.centerY())
            } else {
                null
            }
        } catch (error: RuntimeException) {
            null
        } finally {
            source.recycle()
        }
    }

    private fun findBestNodeBounds(root: AccessibilityNodeInfo): Rect? {
        var best: NodeCandidate? = null

        fun visit(node: AccessibilityNodeInfo?) {
            node ?: return
            val rect = Rect()
            runCatching { node.getBoundsInScreen(rect) }
            if (isValidBounds(rect)) {
                val priority = when {
                    node.isFocused || node.isAccessibilityFocused -> 4
                    node.isSelected -> 3
                    node.isClickable -> 2
                    else -> 1
                }
                if (best == null || priority > best!!.priority) {
                    best = NodeCandidate(Rect(rect), priority)
                }
            }

            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull()
                try {
                    visit(child)
                } finally {
                    child?.recycle()
                }
            }
        }

        visit(root)
        return best?.rect
    }

    private fun isValidBounds(rect: Rect): Boolean {
        return !rect.isEmpty && rect.width() > 1 && rect.height() > 1
    }

    private fun isSupportedEvent(type: Int): Boolean {
        return type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            type == AccessibilityEvent.TYPE_VIEW_SELECTED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }

    private fun shouldThrottle(type: Int): Boolean {
        val now = System.currentTimeMillis()
        val lastReactAt = lastReactAtByType[type] ?: 0L
        if (now - lastReactAt < throttleMs(type)) return true
        lastReactAtByType[type] = now
        return false
    }

    private fun throttleMs(type: Int): Long {
        return when (type) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> 300L
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> 500L
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> 1000L
            else -> 500L
        }
    }

    private fun maybeShowBubble(
        controller: FloatingLive2DController,
        fallback: Boolean = false,
        target: Pair<Int, Int>? = null
    ) {
        reactionCount += 1
        val isSameTarget = target != null && lastBubbleTarget?.let {
            abs(it.first - target.first) < SAME_TARGET_SLOP_PX &&
                abs(it.second - target.second) < SAME_TARGET_SLOP_PX
        } == true
        if (reactionCount % BUBBLE_INTERVAL == 0 && !isSameTarget) {
            controller.showBubble(if (fallback) fallbackBubble() else randomBubble())
            lastBubbleTarget = target
        }
    }

    private fun scheduleReset(controller: FloatingLive2DController) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ controller.resetLookAt() }, RESET_DELAY_MS)
    }

    private fun logControllerFound() {
        val now = System.currentTimeMillis()
        if (now - lastControllerLogAt > CONTROLLER_LOG_INTERVAL_MS) {
            Log.d(TAG, "controller found")
            lastControllerLogAt = now
        }
    }

    private fun randomBubble(): String = BUBBLES.random()

    private fun fallbackBubble(): String = FALLBACK_BUBBLES.random()

    private data class NodeCandidate(val rect: Rect, val priority: Int)

    companion object {
        private const val TAG = "XinyuA11y"
        private const val FEATURE_ENABLED = false
        private const val RESET_DELAY_MS = 1500L
        private const val BUBBLE_INTERVAL = 4
        private const val SAME_TARGET_SLOP_PX = 48
        private const val CONTROLLER_LOG_INTERVAL_MS = 3000L
        private val BUBBLES = arrayOf(
            "我看到了。",
            "我在旁边陪你。",
            "需要我帮你吗？",
            "慢慢来就好。"
        )
        private val FALLBACK_BUBBLES = arrayOf(
            "我在呢。",
            "我陪着你。",
            "慢慢来。"
        )
    }
}
