package com.mindisle.app.relax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class WhiteNoiseRippleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val colors = intArrayOf(
        Color.rgb(127, 175, 232),
        Color.rgb(185, 167, 255),
        Color.rgb(158, 230, 216)
    )
    private var playing = false
    private var animationStartedAt = 0L

    private val frame = object : Runnable {
        override fun run() {
            if (!playing || !isAttachedToWindow) return
            invalidate()
            postOnAnimation(this)
        }
    }

    fun setPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        removeCallbacks(frame)
        if (value) {
            animationStartedAt = SystemClock.uptimeMillis()
            postOnAnimation(frame)
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val base = min(width, height) * 0.22f
        val maxExpansion = min(width, height) * 0.24f

        corePaint.color = Color.argb(34, 220, 238, 255)
        canvas.drawCircle(centerX, centerY, base * 1.18f, corePaint)

        val elapsed = if (playing) {
            (SystemClock.uptimeMillis() - animationStartedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        colors.forEachIndexed { index, color ->
            val phase = if (playing) {
                ((elapsed / CYCLE_MS.toFloat()) + index / 3f) % 1f
            } else {
                index * 0.14f
            }
            val eased = 1f - (1f - phase) * (1f - phase)
            ringPaint.color = color
            ringPaint.alpha = if (playing) {
                (112 * (1f - phase)).toInt().coerceIn(8, 112)
            } else {
                48 - index * 8
            }
            ringPaint.strokeWidth = dp(1.4f + index * 0.35f)
            canvas.drawCircle(
                centerX,
                centerY,
                base + maxExpansion * eased,
                ringPaint
            )
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val CYCLE_MS = 4_200L
    }
}
