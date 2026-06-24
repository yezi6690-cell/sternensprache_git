package com.mindisle.app.relax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class SnakeGameView(context: Context) : ClassicGameView(context) {
    private data class Cell(val x: Int, val y: Int)
    private enum class Direction(val dx: Int, val dy: Int) {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0)
    }

    private val columns = 18
    private val rows = 24
    private val snake = mutableListOf<Cell>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(26, 95, 135, 199)
        strokeWidth = dp(0.6f)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random(System.currentTimeMillis())
    private var direction = Direction.RIGHT
    private var queuedDirection = direction
    private var food = Cell(4, 4)
    private var downX = 0f
    private var downY = 0f

    private val step = object : Runnable {
        override fun run() {
            if (isPaused || isGameOver) return
            moveSnake()
            handler.postDelayed(this, STEP_MS)
        }
    }

    init {
        isClickable = true
        contentDescription = "贪吃蛇游戏区域，上下左右滑动控制方向"
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun restartGame() {
        handler.removeCallbacks(step)
        snake.clear()
        val centerX = columns / 2
        val centerY = rows / 2
        snake += Cell(centerX, centerY)
        snake += Cell(centerX - 1, centerY)
        snake += Cell(centerX - 2, centerY)
        direction = Direction.RIGHT
        queuedDirection = direction
        score = 0
        isGameOver = false
        isPaused = false
        placeFood()
        invalidate()
        handler.postDelayed(step, STEP_MS)
    }

    override fun pauseGame() {
        super.pauseGame()
        handler.removeCallbacks(step)
        invalidate()
    }

    override fun resumeGame() {
        if (isGameOver || !isPaused) return
        super.resumeGame()
        handler.removeCallbacks(step)
        handler.postDelayed(step, STEP_MS)
        invalidate()
    }

    override fun releaseGame() {
        handler.removeCallbacks(step)
        super.releaseGame()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = min(width / columns.toFloat(), height / rows.toFloat())
        val boardWidth = cell * columns
        val boardHeight = cell * rows
        val offsetX = (width - boardWidth) / 2f
        val offsetY = (height - boardHeight) / 2f

        for (column in 0..columns) {
            val x = offsetX + column * cell
            canvas.drawLine(x, offsetY, x, offsetY + boardHeight, gridPaint)
        }
        for (row in 0..rows) {
            val y = offsetY + row * cell
            canvas.drawLine(offsetX, y, offsetX + boardWidth, y, gridPaint)
        }

        snake.forEachIndexed { index, part ->
            val fraction = index / snake.size.coerceAtLeast(1).toFloat()
            paint.color = blendColor(
                Color.rgb(127, 175, 232),
                Color.rgb(185, 167, 255),
                fraction
            )
            val inset = cell * 0.11f
            canvas.drawRoundRect(
                offsetX + part.x * cell + inset,
                offsetY + part.y * cell + inset,
                offsetX + (part.x + 1) * cell - inset,
                offsetY + (part.y + 1) * cell - inset,
                cell * 0.28f,
                cell * 0.28f,
                paint
            )
        }
        drawStar(
            canvas,
            offsetX + (food.x + 0.5f) * cell,
            offsetY + (food.y + 0.5f) * cell,
            cell * 0.39f
        )
        drawPausedOverlay(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPaused || isGameOver) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (kotlin.math.abs(dx) > dp(22f) || kotlin.math.abs(dy) > dp(22f)) {
                    val candidate = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        if (dx > 0) Direction.RIGHT else Direction.LEFT
                    } else {
                        if (dy > 0) Direction.DOWN else Direction.UP
                    }
                    if (!isOpposite(candidate, direction)) queuedDirection = candidate
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun moveSnake() {
        direction = queuedDirection
        val head = snake.first()
        val next = Cell(head.x + direction.dx, head.y + direction.dy)
        val hitWall = next.x !in 0 until columns || next.y !in 0 until rows
        val hitSelf = snake.dropLast(1).contains(next)
        if (hitWall || hitSelf) {
            handler.removeCallbacks(step)
            finishGame()
            invalidate()
            return
        }
        snake.add(0, next)
        if (next == food) {
            score += 10
            placeFood()
        } else {
            snake.removeLast()
        }
        invalidate()
    }

    private fun placeFood() {
        val free = buildList {
            for (x in 0 until columns) for (y in 0 until rows) {
                val cell = Cell(x, y)
                if (cell !in snake) add(cell)
            }
        }
        if (free.isEmpty()) {
            finishGame()
        } else {
            food = free[random.nextInt(free.size)]
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val path = Path()
        repeat(10) { index ->
            val angle = -Math.PI / 2 + index * Math.PI / 5
            val r = if (index % 2 == 0) radius else radius * 0.45f
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        paint.color = Color.rgb(158, 230, 216)
        canvas.drawPath(path, paint)
    }

    private fun drawPausedOverlay(canvas: Canvas) {
        if (!isPaused || isGameOver) return
        paint.color = Color.argb(100, 255, 255, 255)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(20f), dp(20f), paint)
    }

    private fun isOpposite(a: Direction, b: Direction): Boolean =
        a.dx + b.dx == 0 && a.dy + b.dy == 0

    private fun blendColor(from: Int, to: Int, fraction: Float): Int =
        Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).toInt()
        )

    companion object {
        private const val STEP_MS = 170L
    }
}
