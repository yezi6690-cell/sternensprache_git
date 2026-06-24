package com.mindisle.app.relax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class TetrisGameView(context: Context) : ClassicGameView(context) {
    private data class Cell(val x: Int, val y: Int)

    private val columns = 10
    private val rows = 20
    private val board = IntArray(columns * rows)
    private val shapes = listOf(
        listOf(Cell(-1, 0), Cell(0, 0), Cell(1, 0), Cell(2, 0)),
        listOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1)),
        listOf(Cell(-1, 0), Cell(0, 0), Cell(1, 0), Cell(0, 1)),
        listOf(Cell(0, 0), Cell(1, 0), Cell(-1, 1), Cell(0, 1)),
        listOf(Cell(-1, 0), Cell(0, 0), Cell(0, 1), Cell(1, 1)),
        listOf(Cell(-1, 0), Cell(0, 0), Cell(1, 0), Cell(-1, 1)),
        listOf(Cell(-1, 0), Cell(0, 0), Cell(1, 0), Cell(1, 1))
    )
    private val colors = intArrayOf(
        Color.rgb(127, 175, 232),
        Color.rgb(185, 167, 255),
        Color.rgb(158, 230, 216),
        Color.rgb(247, 207, 227),
        Color.rgb(164, 201, 244),
        Color.rgb(208, 196, 250),
        Color.rgb(184, 229, 222)
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(28, 95, 135, 199)
        strokeWidth = dp(0.7f)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random(System.currentTimeMillis())
    private var type = 0
    private var rotation = 0
    private var pieceX = 4
    private var pieceY = 1
    private var downX = 0f
    private var downY = 0f

    private val tick = object : Runnable {
        override fun run() {
            if (isPaused || isGameOver) return
            if (!tryMove(0, 1)) lockPiece()
            handler.postDelayed(this, DROP_INTERVAL_MS)
        }
    }

    init {
        isClickable = true
        contentDescription = "俄罗斯方块游戏区域，可点击旋转或滑动移动"
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun restartGame() {
        handler.removeCallbacks(tick)
        board.fill(0)
        score = 0
        isGameOver = false
        isPaused = false
        spawnPiece()
        invalidate()
        handler.postDelayed(tick, DROP_INTERVAL_MS)
    }

    override fun pauseGame() {
        super.pauseGame()
        handler.removeCallbacks(tick)
        invalidate()
    }

    override fun resumeGame() {
        if (isGameOver || !isPaused) return
        super.resumeGame()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, DROP_INTERVAL_MS)
        invalidate()
    }

    override fun releaseGame() {
        handler.removeCallbacks(tick)
        super.releaseGame()
    }

    fun moveLeft() {
        if (!isPaused && !isGameOver) tryMove(-1, 0)
    }

    fun moveRight() {
        if (!isPaused && !isGameOver) tryMove(1, 0)
    }

    fun rotatePiece() {
        if (isPaused || isGameOver || type == 1) return
        val nextRotation = (rotation + 1) % 4
        if (!collides(pieceX, pieceY, nextRotation)) {
            rotation = nextRotation
            invalidate()
        }
    }

    fun softDrop() {
        if (isPaused || isGameOver) return
        if (tryMove(0, 1)) {
            score += 1
        } else {
            lockPiece()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = min(width / columns.toFloat(), height / rows.toFloat())
        val boardWidth = cell * columns
        val boardHeight = cell * rows
        val offsetX = (width - boardWidth) / 2f
        val offsetY = (height - boardHeight) / 2f

        paint.color = Color.argb(120, 245, 250, 255)
        canvas.drawRoundRect(
            offsetX,
            offsetY,
            offsetX + boardWidth,
            offsetY + boardHeight,
            dp(18f),
            dp(18f),
            paint
        )
        for (column in 0..columns) {
            val x = offsetX + column * cell
            canvas.drawLine(x, offsetY, x, offsetY + boardHeight, gridPaint)
        }
        for (row in 0..rows) {
            val y = offsetY + row * cell
            canvas.drawLine(offsetX, y, offsetX + boardWidth, y, gridPaint)
        }
        board.forEachIndexed { index, value ->
            if (value == 0) return@forEachIndexed
            drawBlock(canvas, offsetX, offsetY, cell, index % columns, index / columns, value - 1)
        }
        transformedCells(rotation).forEach { part ->
            drawBlock(canvas, offsetX, offsetY, cell, pieceX + part.x, pieceY + part.y, type)
        }
        if (isPaused && !isGameOver) {
            paint.color = Color.argb(115, 255, 255, 255)
            canvas.drawRoundRect(
                offsetX,
                offsetY,
                offsetX + boardWidth,
                offsetY + boardHeight,
                dp(18f),
                dp(18f),
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
                if (abs(dx) < dp(18f) && abs(dy) < dp(18f)) {
                    rotatePiece()
                } else if (abs(dx) > abs(dy)) {
                    if (dx > 0) moveRight() else moveLeft()
                } else if (dy > 0) {
                    softDrop()
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

    private fun tryMove(dx: Int, dy: Int): Boolean {
        if (collides(pieceX + dx, pieceY + dy, rotation)) return false
        pieceX += dx
        pieceY += dy
        invalidate()
        return true
    }

    private fun lockPiece() {
        transformedCells(rotation).forEach { part ->
            val x = pieceX + part.x
            val y = pieceY + part.y
            if (x in 0 until columns && y in 0 until rows) {
                board[y * columns + x] = type + 1
            }
        }
        clearLines()
        spawnPiece()
        if (collides(pieceX, pieceY, rotation)) {
            handler.removeCallbacks(tick)
            finishGame()
        }
        invalidate()
    }

    private fun clearLines() {
        var cleared = 0
        var targetRow = rows - 1
        for (sourceRow in rows - 1 downTo 0) {
            val full = (0 until columns).all { board[sourceRow * columns + it] != 0 }
            if (full) {
                cleared++
            } else {
                for (column in 0 until columns) {
                    board[targetRow * columns + column] = board[sourceRow * columns + column]
                }
                targetRow--
            }
        }
        while (targetRow >= 0) {
            for (column in 0 until columns) board[targetRow * columns + column] = 0
            targetRow--
        }
        if (cleared > 0) {
            score += when (cleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                else -> 800
            }
        }
    }

    private fun spawnPiece() {
        type = random.nextInt(shapes.size)
        rotation = 0
        pieceX = columns / 2
        pieceY = 1
    }

    private fun collides(x: Int, y: Int, targetRotation: Int): Boolean =
        transformedCells(targetRotation).any { part ->
            val boardX = x + part.x
            val boardY = y + part.y
            boardX !in 0 until columns ||
                boardY !in 0 until rows ||
                board[boardY * columns + boardX] != 0
        }

    private fun transformedCells(targetRotation: Int): List<Cell> {
        if (type == 1) return shapes[type]
        return shapes[type].map { source ->
            var x = source.x
            var y = source.y
            repeat(targetRotation) {
                val oldX = x
                x = -y
                y = oldX
            }
            Cell(x, y)
        }
    }

    private fun drawBlock(
        canvas: Canvas,
        offsetX: Float,
        offsetY: Float,
        cell: Float,
        x: Int,
        y: Int,
        colorIndex: Int
    ) {
        if (x !in 0 until columns || y !in 0 until rows) return
        val inset = cell * 0.09f
        paint.color = colors[colorIndex % colors.size]
        canvas.drawRoundRect(
            offsetX + x * cell + inset,
            offsetY + y * cell + inset,
            offsetX + (x + 1) * cell - inset,
            offsetY + (y + 1) * cell - inset,
            cell * 0.22f,
            cell * 0.22f,
            paint
        )
        paint.color = Color.argb(90, 255, 255, 255)
        canvas.drawRoundRect(
            offsetX + x * cell + inset * 1.5f,
            offsetY + y * cell + inset * 1.5f,
            offsetX + (x + 1) * cell - inset * 1.5f,
            offsetY + y * cell + cell * 0.28f,
            cell * 0.16f,
            cell * 0.16f,
            paint
        )
    }

    companion object {
        private const val DROP_INTERVAL_MS = 560L
    }
}
