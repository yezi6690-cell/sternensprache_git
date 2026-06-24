package com.mindisle.app.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class VinylRecordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private var coverBitmap: Bitmap? = null
    private var coverPath: String? = null

    fun setCover(path: String?) {
        if (coverPath == path) return
        coverBitmap?.recycle()
        coverBitmap = path?.let { BitmapFactory.decodeFile(it) }
        coverPath = path
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = width.coerceAtMost(height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = size * 0.48f

        paint.shader = RadialGradient(
            centerX,
            centerY,
            radius * 1.06f,
            intArrayOf(
                Color.argb(0, 127, 175, 232),
                Color.argb(54, 127, 175, 232),
                Color.argb(0, 127, 175, 232)
            ),
            floatArrayOf(0.78f, 0.94f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius * 1.04f, paint)
        paint.shader = null

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(
                Color.rgb(82, 107, 145),
                Color.rgb(33, 48, 72),
                Color.rgb(18, 30, 49),
                Color.rgb(103, 126, 165)
            ),
            floatArrayOf(0f, 0.48f, 0.76f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.shader = null

        ringPaint.color = Color.argb(72, 255, 255, 255)
        ringPaint.strokeWidth = dp(1f)
        listOf(0.9f, 0.78f, 0.67f, 0.57f).forEach {
            canvas.drawCircle(centerX, centerY, radius * it, ringPaint)
        }

        ringPaint.color = Color.argb(42, 190, 222, 255)
        ringPaint.strokeWidth = dp(2.2f)
        canvas.drawArc(
            centerX - radius * 0.82f,
            centerY - radius * 0.82f,
            centerX + radius * 0.82f,
            centerY + radius * 0.82f,
            205f,
            82f,
            false,
            ringPaint
        )

        val coverRadius = radius * 0.43f
        val cover = coverBitmap
        if (cover != null) {
            val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = (coverRadius * 2f / cover.width.coerceAtMost(cover.height))
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    centerX - cover.width * scale / 2f,
                    centerY - cover.height * scale / 2f
                )
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawCircle(centerX, centerY, coverRadius, paint)
            paint.shader = null
        } else {
            // Keep coverless tracks looking like a real record instead of a
            // generic placeholder image.
            paint.shader = RadialGradient(
                centerX,
                centerY,
                coverRadius,
                intArrayOf(
                    Color.rgb(111, 135, 171),
                    Color.rgb(54, 72, 101),
                    Color.rgb(31, 45, 68)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(centerX, centerY, coverRadius, paint)
            paint.shader = null
            ringPaint.color = Color.argb(95, 222, 236, 255)
            ringPaint.strokeWidth = dp(0.8f)
            canvas.drawCircle(centerX, centerY, coverRadius * 0.68f, ringPaint)
        }

        ringPaint.color = Color.argb(210, 255, 255, 255)
        ringPaint.strokeWidth = dp(2f)
        canvas.drawCircle(centerX, centerY, coverRadius, ringPaint)
        paint.color = Color.rgb(95, 135, 199)
        canvas.drawCircle(centerX, centerY, dp(5f), paint)
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, dp(1.7f), paint)
    }

    override fun onDetachedFromWindow() {
        coverBitmap?.recycle()
        coverBitmap = null
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
