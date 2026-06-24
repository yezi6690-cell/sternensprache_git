package com.mindisle.app.relax

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class Game2048View(context: Context) : ClassicGameView(context) {
    private enum class Direction { LEFT, RIGHT, UP, DOWN }

    private val board = IntArray(16)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())
    private var downX = 0f
    private var downY = 0f
    private var pulse = 0f
    private var pulseAnimator: ValueAnimator? = null

    init {
        isClickable = true
        contentDescription = "2048 游戏区域，上下左右滑动合并数字"
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun restartGame() {
        pulseAnimator?.cancel()
        board.fill(0)
        score = 0
        isGameOver = false
        isPaused = false
        addTile()
        addTile()
        invalidate()
    }

    override fun pauseGame() {
        super.pauseGame()
        invalidate()
    }

    override fun resumeGame() {
        super.resumeGame()
        invalidate()
    }

    override fun releaseGame() {
        pulseAnimator?.cancel()
        super.releaseGame()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val boardSize = size * 0.94f
        val left = (width - boardSize) / 2f
        val top = (height - boardSize) / 2f
        val gap = dp(7f)
        val cell = (boardSize - gap * 5) / 4f

        paint.color = Color.argb(185, 225, 239, 252)
        canvas.drawRoundRect(
            left,
            top,
            left + boardSize,
            top + boardSize,
            dp(22f),
            dp(22f),
            paint
        )

        board.forEachIndexed { index, value ->
            val row = index / 4
            val column = index % 4
            val cellLeft = left + gap + column * (cell + gap)
            val cellTop = top + gap + row * (cell + gap)
            val scale = if (value != 0) 1f + pulse * 0.025f else 1f
            val inset = cell * (1f - scale) / 2f
            paint.color = tileColor(value)
            canvas.drawRoundRect(
                cellLeft + inset,
                cellTop + inset,
                cellLeft + cell - inset,
                cellTop + cell - inset,
                dp(14f),
                dp(14f),
                paint
            )
            if (value != 0) {
                paint.color = Color.rgb(47, 64, 88)
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textSize = when {
                    value < 100 -> cell * 0.34f
                    value < 1000 -> cell * 0.29f
                    else -> cell * 0.24f
                }
                val baseline = cellTop + cell / 2f - (paint.ascent() + paint.descent()) / 2f
                canvas.drawText(value.toString(), cellLeft + cell / 2f, baseline, paint)
            }
        }

        if (isPaused && !isGameOver) {
            paint.color = Color.argb(115, 255, 255, 255)
            canvas.drawRoundRect(
                left,
                top,
                left + boardSize,
                top + boardSize,
                dp(22f),
                dp(22f),
                paint
            )
        }
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
                if (abs(dx) > dp(24f) || abs(dy) > dp(24f)) {
                    val direction = if (abs(dx) > abs(dy)) {
                        if (dx > 0) Direction.RIGHT else Direction.LEFT
                    } else {
                        if (dy > 0) Direction.DOWN else Direction.UP
                    }
                    move(direction)
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

    private fun move(direction: Direction) {
        val before = board.copyOf()
        var scoreGain = 0
        repeat(4) { line ->
            val indices = lineIndices(direction, line)
            val values = indices.map { board[it] }.filter { it != 0 }.toMutableList()
            val merged = mutableListOf<Int>()
            var cursor = 0
            while (cursor < values.size) {
                if (cursor + 1 < values.size && values[cursor] == values[cursor + 1]) {
                    val value = values[cursor] * 2
                    merged += value
                    scoreGain += value
                    cursor += 2
                } else {
                    merged += values[cursor]
                    cursor++
                }
            }
            while (merged.size < 4) merged += 0
            indices.forEachIndexed { index, boardIndex -> board[boardIndex] = merged[index] }
        }
        if (!board.contentEquals(before)) {
            score += scoreGain
            addTile()
            animateMerge()
            if (!hasMoves()) finishGame()
            invalidate()
        }
    }

    private fun lineIndices(direction: Direction, line: Int): List<Int> = when (direction) {
        Direction.LEFT -> (0..3).map { line * 4 + it }
        Direction.RIGHT -> (3 downTo 0).map { line * 4 + it }
        Direction.UP -> (0..3).map { it * 4 + line }
        Direction.DOWN -> (3 downTo 0).map { it * 4 + line }
    }

    private fun addTile() {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return
        board[empty[random.nextInt(empty.size)]] = if (random.nextFloat() < 0.9f) 2 else 4
    }

    private fun hasMoves(): Boolean {
        if (board.any { it == 0 }) return true
        for (row in 0 until 4) for (column in 0 until 4) {
            val value = board[row * 4 + column]
            if (column < 3 && board[row * 4 + column + 1] == value) return true
            if (row < 3 && board[(row + 1) * 4 + column] == value) return true
        }
        return false
    }

    private fun animateMerge() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun tileColor(value: Int): Int = when (value) {
        0 -> Color.argb(125, 255, 255, 255)
        2 -> Color.rgb(235, 245, 255)
        4 -> Color.rgb(218, 237, 255)
        8 -> Color.rgb(205, 225, 251)
        16 -> Color.rgb(224, 218, 255)
        32 -> Color.rgb(199, 184, 255)
        64 -> Color.rgb(204, 242, 235)
        128 -> Color.rgb(158, 230, 216)
        256 -> Color.rgb(241, 219, 235)
        512 -> Color.rgb(247, 207, 227)
        1024 -> Color.rgb(164, 201, 244)
        else -> Color.rgb(127, 175, 232)
    }
}
