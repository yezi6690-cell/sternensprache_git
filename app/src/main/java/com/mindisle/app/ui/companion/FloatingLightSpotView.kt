package com.mindisle.app.ui.companion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class FloatingLightSpotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Spot(
        var x: Float,
        var y: Float,
        val radius: Float,
        val velocityX: Float,
        val velocityY: Float,
        val phase: Float,
        val pulseSpeed: Float,
        val shader: RadialGradient,
        val shaderMatrix: Matrix = Matrix()
    )

    private data class Particle(
        var baseX: Float,
        var baseY: Float,
        val radius: Float,
        val velocityX: Float,
        val velocityY: Float,
        val wobbleX: Float,
        val wobbleY: Float,
        val wobbleSpeed: Float,
        val phase: Float,
        val pulseSpeed: Float,
        val baseAlpha: Int,
        val shader: RadialGradient,
        val shaderMatrix: Matrix = Matrix()
    )

    private val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val spots = mutableListOf<Spot>()
    private val particles = mutableListOf<Particle>()
    private val random = Random(20260610)
    private var animationRunning = false
    private var lastFrameTimeNanos = 0L
    private var elapsedAnimationSeconds = 0f

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildSpots(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (spots.isEmpty() && particles.isEmpty()) return

        val nowNanos = System.nanoTime()
        val deltaSeconds = if (lastFrameTimeNanos == 0L) {
            0f
        } else {
            ((nowNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        }
        lastFrameTimeNanos = nowNanos
        elapsedAnimationSeconds += deltaSeconds

        drawLightSpots(canvas, deltaSeconds)
        drawAirParticles(canvas, deltaSeconds)

        if (animationRunning) {
            postInvalidateDelayed(FRAME_DELAY_MS)
        }
    }

    private fun drawLightSpots(canvas: Canvas, deltaSeconds: Float) {
        spots.forEach { spot ->
            spot.x += spot.velocityX * deltaSeconds
            spot.y += spot.velocityY * deltaSeconds
            wrapSpot(spot)

            val pulse =
                0.94f + sin(elapsedAnimationSeconds * spot.pulseSpeed + spot.phase) * 0.06f
            spot.shaderMatrix.reset()
            spot.shaderMatrix.setScale(pulse, pulse)
            spot.shaderMatrix.postTranslate(spot.x, spot.y)
            spot.shader.setLocalMatrix(spot.shaderMatrix)
            spotPaint.shader = spot.shader
            canvas.drawCircle(spot.x, spot.y, spot.radius * pulse, spotPaint)
        }
        spotPaint.shader = null
    }

    private fun drawAirParticles(canvas: Canvas, deltaSeconds: Float) {
        particles.forEach { particle ->
            particle.baseX += particle.velocityX * deltaSeconds
            particle.baseY += particle.velocityY * deltaSeconds

            val wobbleX =
                sin(elapsedAnimationSeconds * particle.wobbleSpeed + particle.phase) *
                    particle.wobbleX
            val wobbleY =
                sin(elapsedAnimationSeconds * particle.wobbleSpeed * 0.72f + particle.phase) *
                    particle.wobbleY
            val drawX = particle.baseX + wobbleX
            val drawY = particle.baseY + wobbleY
            val pulse =
                0.92f + sin(elapsedAnimationSeconds * particle.pulseSpeed + particle.phase) * 0.08f
            val alphaPulse =
                0.76f + sin(elapsedAnimationSeconds * particle.pulseSpeed + particle.phase) * 0.24f

            particle.shaderMatrix.reset()
            particle.shaderMatrix.setScale(pulse, pulse)
            particle.shaderMatrix.postTranslate(drawX, drawY)
            particle.shader.setLocalMatrix(particle.shaderMatrix)
            particlePaint.shader = particle.shader
            particlePaint.alpha = (particle.baseAlpha * alphaPulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(drawX, drawY, particle.radius * pulse, particlePaint)

            if (
                particle.baseY < -PARTICLE_MARGIN ||
                particle.baseX < -PARTICLE_MARGIN ||
                particle.baseX > width + PARTICLE_MARGIN
            ) {
                resetParticleAtBottom(particle)
            }
        }
        particlePaint.shader = null
        particlePaint.alpha = 255
    }

    fun startAnimation() {
        if (animationRunning) return
        animationRunning = true
        lastFrameTimeNanos = 0L
        postInvalidateOnAnimation()
    }

    fun stopAnimation() {
        animationRunning = false
        lastFrameTimeNanos = 0L
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    private fun rebuildSpots(width: Int, height: Int) {
        spots.clear()
        particles.clear()
        if (width <= 0 || height <= 0) return

        repeat(SPOT_COUNT) { index ->
            val radius = dp(random.nextInt(38, 92)).toFloat()
            val color = SPOT_COLORS[index % SPOT_COLORS.size]
            val alpha = random.nextInt(40, 72)
            val shader = RadialGradient(
                0f,
                0f,
                radius,
                intArrayOf(
                    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(alpha / 2, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            spots += Spot(
                x = random.nextFloat() * width,
                y = random.nextFloat() * height,
                radius = radius,
                velocityX = dpFloat(random.nextFloat() * 5f - 2.5f),
                velocityY = dpFloat(random.nextFloat() * 4.4f - 2.2f),
                phase = random.nextFloat() * TWO_PI,
                pulseSpeed = 0.35f + random.nextFloat() * 0.45f,
                shader = shader
            )
        }

        repeat(PARTICLE_COUNT) { index ->
            val radius = dpFloat(1.4f + random.nextFloat() * 3.2f)
            val color = PARTICLE_COLORS[index % PARTICLE_COLORS.size]
            val shader = RadialGradient(
                0f,
                0f,
                radius,
                intArrayOf(
                    Color.argb(230, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(125, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.28f, 1f),
                Shader.TileMode.CLAMP
            )
            particles += Particle(
                baseX = random.nextFloat() * width,
                baseY = random.nextFloat() * height,
                radius = radius,
                velocityX = dpFloat(random.nextFloat() * 5f - 2.5f),
                velocityY = -dpFloat(7f + random.nextFloat() * 11f),
                wobbleX = dpFloat(5f + random.nextFloat() * 11f),
                wobbleY = dpFloat(2f + random.nextFloat() * 6f),
                wobbleSpeed = 0.35f + random.nextFloat() * 0.65f,
                phase = random.nextFloat() * TWO_PI,
                pulseSpeed = 0.55f + random.nextFloat() * 0.8f,
                baseAlpha = random.nextInt(85, 150),
                shader = shader
            )
        }
        invalidate()
    }

    private fun wrapSpot(spot: Spot) {
        if (spot.x < -spot.radius) spot.x = width + spot.radius
        if (spot.x > width + spot.radius) spot.x = -spot.radius
        if (spot.y < -spot.radius) spot.y = height + spot.radius
        if (spot.y > height + spot.radius) spot.y = -spot.radius
    }

    private fun resetParticleAtBottom(particle: Particle) {
        particle.baseX = random.nextFloat() * width
        particle.baseY = height + random.nextFloat() * PARTICLE_MARGIN
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dpFloat(value: Float): Float =
        value * resources.displayMetrics.density

    companion object {
        private const val SPOT_COUNT = 8
        private const val PARTICLE_COUNT = 30
        private const val FRAME_DELAY_MS = 32L
        private const val PARTICLE_MARGIN = 48f
        private const val TWO_PI = (Math.PI * 2).toFloat()
        private val SPOT_COLORS = intArrayOf(
            Color.rgb(255, 255, 255),
            Color.rgb(190, 224, 255),
            Color.rgb(216, 198, 255)
        )
        private val PARTICLE_COLORS = intArrayOf(
            Color.rgb(255, 255, 255),
            Color.rgb(237, 248, 255),
            Color.rgb(244, 237, 255)
        )
    }
}
