package com.mindisle.app.music

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Lightweight tonearm layer. Its single progress value keeps playback motion
 * independent from layout and preserves the current visual state on pause.
 */
class TonearmView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(74, 94, 123)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 229, 240, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()
    private var animator: ValueAnimator? = null
    private var armProgress = 0f

    fun setPlaying(playing: Boolean, animate: Boolean = true) {
        val target = if (playing) 1f else 0f
        animator?.cancel()
        if (!animate) {
            armProgress = target
            invalidate()
            return
        }
        if (armProgress == target) return
        animator = ValueAnimator.ofFloat(armProgress, target).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                armProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val baseX = width * 0.84f
        val baseY = height * 0.19f
        val idleX = width * 0.91f
        val idleY = height * 0.66f
        val playX = width * 0.59f
        val playY = height * 0.43f
        val needleX = lerp(idleX, playX, armProgress)
        val needleY = lerp(idleY, playY, armProgress)
        val elbowX = lerp(width * 0.91f, width * 0.73f, armProgress)
        val elbowY = lerp(height * 0.38f, height * 0.30f, armProgress)

        basePaint.color = Color.rgb(221, 236, 252)
        canvas.drawCircle(baseX, baseY, dp(10f), basePaint)
        basePaint.color = Color.rgb(101, 126, 163)
        canvas.drawCircle(baseX, baseY, dp(6f), basePaint)
        basePaint.color = Color.rgb(239, 247, 255)
        canvas.drawCircle(baseX, baseY, dp(2.3f), basePaint)

        armPaint.strokeWidth = dp(4.2f)
        path.reset()
        path.moveTo(baseX, baseY)
        path.lineTo(elbowX, elbowY)
        path.lineTo(needleX, needleY)
        canvas.drawPath(path, armPaint)

        highlightPaint.strokeWidth = dp(1.2f)
        path.reset()
        path.moveTo(baseX - dp(1f), baseY - dp(1f))
        path.lineTo(elbowX - dp(1f), elbowY - dp(1f))
        path.lineTo(needleX - dp(1f), needleY - dp(1f))
        canvas.drawPath(path, highlightPaint)

        basePaint.color = Color.rgb(47, 64, 88)
        canvas.save()
        canvas.rotate(-18f + armProgress * 10f, needleX, needleY)
        canvas.drawRoundRect(
            needleX - dp(3f),
            needleY - dp(2f),
            needleX + dp(9f),
            needleY + dp(4f),
            dp(2f),
            dp(2f),
            basePaint
        )
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
