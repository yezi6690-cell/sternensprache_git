package com.mindisle.app.relax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class PinballGameView(context: Context) : ClassicGameView(context) {
    private data class Spark(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bricks = mutableListOf<RectF>()
    private val sparks = mutableListOf<Spark>()
    private val random = Random(System.currentTimeMillis())
    private var ballX = 0f
    private var ballY = 0f
    private var ballVx = 0f
    private var ballVy = 0f
    private var paddleX = 0f
    private var lastFrameNanos = 0L
    private var pendingReset = true

    init {
        isClickable = true
        contentDescription = "二维弹球游戏区域，左右拖动底部挡板"
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun restartGame() {
        score = 0
        isGameOver = false
        isPaused = false
        pendingReset = true
        lastFrameNanos = 0L
        if (width > 0 && height > 0) resetScene()
        invalidate()
    }

    override fun pauseGame() {
        super.pauseGame()
        lastFrameNanos = 0L
        invalidate()
    }

    override fun resumeGame() {
        if (isGameOver || !isPaused) return
        super.resumeGame()
        lastFrameNanos = 0L
        invalidate()
    }

    override fun releaseGame() {
        sparks.clear()
        super.releaseGame()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && pendingReset) resetScene()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pendingReset && width > 0 && height > 0) resetScene()
        if (!isPaused && !isGameOver) updatePhysics()

        paint.color = Color.argb(95, 244, 250, 255)
        canvas.drawRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            dp(22f),
            dp(22f),
            paint
        )

        bricks.forEachIndexed { index, brick ->
            paint.color = when (index % 4) {
                0 -> Color.rgb(186, 218, 249)
                1 -> Color.rgb(211, 202, 250)
                2 -> Color.rgb(190, 235, 226)
                else -> Color.rgb(241, 219, 235)
            }
            canvas.drawRoundRect(brick, dp(8f), dp(8f), paint)
            paint.color = Color.argb(85, 255, 255, 255)
            canvas.drawRoundRect(
                brick.left + dp(3f),
                brick.top + dp(3f),
                brick.right - dp(3f),
                brick.top + dp(8f),
                dp(4f),
                dp(4f),
                paint
            )
        }

        val paddleWidth = width * 0.25f
        val paddleTop = height - dp(34f)
        paint.shader = android.graphics.LinearGradient(
            paddleX - paddleWidth / 2f,
            paddleTop,
            paddleX + paddleWidth / 2f,
            paddleTop,
            Color.rgb(127, 175, 232),
            Color.rgb(185, 167, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            paddleX - paddleWidth / 2f,
            paddleTop,
            paddleX + paddleWidth / 2f,
            paddleTop + dp(12f),
            dp(7f),
            dp(7f),
            paint
        )
        paint.shader = null

        val radius = dp(8f)
        paint.shader = RadialGradient(
            ballX - radius * 0.3f,
            ballY - radius * 0.35f,
            radius * 1.3f,
            intArrayOf(Color.WHITE, Color.rgb(177, 221, 255), Color.rgb(111, 164, 230)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(ballX, ballY, radius, paint)
        paint.shader = null

        sparks.forEach {
            paint.color = Color.argb((it.life * 210).toInt(), 148, 196, 245)
            canvas.drawCircle(it.x, it.y, dp(2.2f), paint)
        }

        if (isPaused && !isGameOver) {
            paint.color = Color.argb(105, 255, 255, 255)
            canvas.drawRoundRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                dp(22f),
                dp(22f),
                paint
            )
        }
        if (!isPaused && !isGameOver) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPaused || isGameOver) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val half = width * 0.125f
                paddleX = event.x.coerceIn(half, width - half)
                invalidate()
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun resetScene() {
        pendingReset = false
        bricks.clear()
        sparks.clear()
        val columns = 5
        val rows = 4
        val gap = dp(7f)
        val side = dp(14f)
        val brickWidth = (width - side * 2 - gap * (columns - 1)) / columns
        val brickHeight = dp(24f)
        repeat(rows) { row ->
            repeat(columns) { column ->
                val left = side + column * (brickWidth + gap)
                val top = dp(28f) + row * (brickHeight + gap)
                bricks += RectF(left, top, left + brickWidth, top + brickHeight)
            }
        }
        paddleX = width / 2f
        ballX = width / 2f
        ballY = height * 0.62f
        ballVx = dp(145f)
        ballVy = -dp(205f)
        lastFrameNanos = 0L
    }

    private fun updatePhysics() {
        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) 0f else
            ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.034f)
        lastFrameNanos = now
        if (delta == 0f) return

        ballX += ballVx * delta
        ballY += ballVy * delta
        val radius = dp(8f)
        if (ballX - radius <= 0f && ballVx < 0f) ballVx = abs(ballVx)
        if (ballX + radius >= width && ballVx > 0f) ballVx = -abs(ballVx)
        if (ballY - radius <= 0f && ballVy < 0f) ballVy = abs(ballVy)

        val paddleWidth = width * 0.25f
        val paddleTop = height - dp(34f)
        if (
            ballVy > 0f &&
            ballY + radius >= paddleTop &&
            ballY - radius <= paddleTop + dp(12f) &&
            ballX in (paddleX - paddleWidth / 2f)..(paddleX + paddleWidth / 2f)
        ) {
            ballY = paddleTop - radius
            ballVy = -abs(ballVy)
            val offset = (ballX - paddleX) / (paddleWidth / 2f)
            ballVx += offset * dp(65f)
        }

        val iterator = bricks.iterator()
        while (iterator.hasNext()) {
            val brick = iterator.next()
            if (
                ballX + radius >= brick.left &&
                ballX - radius <= brick.right &&
                ballY + radius >= brick.top &&
                ballY - radius <= brick.bottom
            ) {
                iterator.remove()
                ballVy = if (ballY < brick.centerY()) -abs(ballVy) else abs(ballVy)
                score += 10
                createSparks(ballX, ballY)
                break
            }
        }
        updateSparks(delta)
        if (bricks.isEmpty()) rebuildBricks()
        if (ballY - radius > height) finishGame()
    }

    private fun rebuildBricks() {
        pendingReset = true
        val savedScore = score
        resetScene()
        score = savedScore
        ballVy = -abs(ballVy) * 1.04f
    }

    private fun createSparks(x: Float, y: Float) {
        repeat(7) {
            sparks += Spark(
                x,
                y,
                (random.nextFloat() - 0.5f) * dp(90f),
                (random.nextFloat() - 0.7f) * dp(90f),
                1f
            )
        }
    }

    private fun updateSparks(delta: Float) {
        val iterator = sparks.iterator()
        while (iterator.hasNext()) {
            val spark = iterator.next()
            spark.x += spark.vx * delta
            spark.y += spark.vy * delta
            spark.vy += dp(85f) * delta
            spark.life -= delta * 2.4f
            if (spark.life <= 0f) iterator.remove()
        }
    }
}
