package com.mindisle.app.relax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class RelaxPlayView(context: Context) : View(context) {
    enum class Mode { BUBBLES, STARS }

    private data class Item(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedX: Float,
        var speedY: Float,
        var alpha: Int
    )

    private data class Burst(
        val x: Float,
        val y: Float,
        val startedAt: Long,
        val mode: Mode
    )

    var mode: Mode = Mode.BUBBLES
        private set
    var onItemCollected: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val items = mutableListOf<Item>()
    private val bursts = mutableListOf<Burst>()
    private val random = Random(System.currentTimeMillis())
    private var running = false
    private var lastFrameNanos = 0L

    init {
        isClickable = true
        contentDescription = "放松互动区域"
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun start(newMode: Mode) {
        mode = newMode
        running = true
        lastFrameNanos = 0L
        rebuildItems()
        invalidate()
    }

    fun stop() {
        running = false
        items.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (running) rebuildItems()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running || width == 0 || height == 0) return
        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) 1f else
            ((now - lastFrameNanos) / 16_666_667f).coerceIn(0.4f, 2.2f)
        lastFrameNanos = now

        items.forEach {
            it.x += it.speedX * delta
            it.y += it.speedY * delta
            wrap(it)
            if (mode == Mode.BUBBLES) drawBubble(canvas, it) else drawStar(canvas, it)
        }
        drawBursts(canvas)
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || !running) return true
        val hit = items.indices.reversed().firstOrNull { index ->
            val item = items[index]
            val dx = event.x - item.x
            val dy = event.y - item.y
            dx * dx + dy * dy <= item.radius * item.radius * 1.35f
        } ?: return true

        val item = items[hit]
        animateCollection(item.x, item.y)
        items[hit] = createItem()
        onItemCollected?.invoke()
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun rebuildItems() {
        if (width == 0 || height == 0) return
        items.clear()
        repeat(if (mode == Mode.BUBBLES) 11 else 7) { items += createItem() }
    }

    private fun createItem(): Item {
        val minRadius = if (mode == Mode.BUBBLES) dp(20f) else dp(14f)
        val radiusRange = if (mode == Mode.BUBBLES) dp(28f) else dp(10f)
        return Item(
            x = random.nextFloat() * width.coerceAtLeast(1),
            y = random.nextFloat() * height.coerceAtLeast(1),
            radius = minRadius + random.nextFloat() * radiusRange,
            speedX = (random.nextFloat() - 0.5f) * dp(0.28f),
            speedY = if (mode == Mode.BUBBLES) {
                -(dp(0.18f) + random.nextFloat() * dp(0.32f))
            } else {
                (random.nextFloat() - 0.5f) * dp(0.22f)
            },
            alpha = 120 + random.nextInt(75)
        )
    }

    private fun wrap(item: Item) {
        if (item.x < -item.radius) item.x = width + item.radius
        if (item.x > width + item.radius) item.x = -item.radius
        if (item.y < -item.radius) item.y = height + item.radius
        if (item.y > height + item.radius) item.y = -item.radius
    }

    private fun drawBubble(canvas: Canvas, item: Item) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            item.x - item.radius * 0.3f,
            item.y - item.radius * 0.35f,
            item.radius * 1.25f,
            intArrayOf(
                Color.argb(item.alpha, 255, 255, 255),
                Color.argb(item.alpha / 2, 181, 222, 255),
                Color.argb(20, 185, 167, 255)
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(item.x, item.y, item.radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = Color.argb(130, 255, 255, 255)
        canvas.drawCircle(item.x, item.y, item.radius - dp(1f), paint)
    }

    private fun drawStar(canvas: Canvas, item: Item) {
        val path = Path()
        repeat(10) { index ->
            val angle = -Math.PI / 2 + index * Math.PI / 5
            val radius = if (index % 2 == 0) item.radius else item.radius * 0.44f
            val x = item.x + cos(angle).toFloat() * radius
            val y = item.y + sin(angle).toFloat() * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(item.alpha, 151, 172, 243)
        canvas.drawPath(path, paint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = dp(1f)
        highlightPaint.color = Color.argb(150, 255, 255, 255)
        canvas.drawPath(path, highlightPaint)
    }

    private fun animateCollection(x: Float, y: Float) {
        bursts += Burst(x, y, SystemClock.uptimeMillis(), mode)
    }

    private fun drawBursts(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val iterator = bursts.iterator()
        while (iterator.hasNext()) {
            val burst = iterator.next()
            val progress = ((now - burst.startedAt) / 420f).coerceIn(0f, 1f)
            if (progress >= 1f) {
                iterator.remove()
                continue
            }
            val alpha = ((1f - progress) * 190).toInt()
            if (burst.mode == Mode.BUBBLES) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.5f)
                paint.color = Color.argb(alpha, 160, 202, 255)
                canvas.drawCircle(burst.x, burst.y, dp(10f) + dp(26f) * progress, paint)
                paint.style = Paint.Style.FILL
                repeat(6) { index ->
                    val angle = index * Math.PI / 3
                    val distance = dp(8f) + dp(24f) * progress
                    canvas.drawCircle(
                        burst.x + cos(angle).toFloat() * distance,
                        burst.y + sin(angle).toFloat() * distance,
                        dp(2.2f) * (1f - progress * 0.6f),
                        paint
                    )
                }
            } else {
                val targetX = width - dp(28f)
                val targetY = dp(28f)
                val x = burst.x + (targetX - burst.x) * progress
                val y = burst.y + (targetY - burst.y) * progress
                val item = Item(x, y, dp(12f) * (1f - progress * 0.4f), 0f, 0f, alpha)
                drawStar(canvas, item)
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
