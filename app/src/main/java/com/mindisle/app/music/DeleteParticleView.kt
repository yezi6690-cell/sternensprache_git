package com.mindisle.app.music

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Lightweight destructive burst: small circular particles scatter, then fall to the drawer
 * bottom before fading. Particle objects are allocated once per burst and Paint is reused.
 */
class DeleteParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private data class Particle(
        val startX: Float,
        val startY: Float,
        val velocityX: Float,
        val velocityY: Float,
        val gravity: Float,
        val damping: Float,
        val radius: Float,
        val baseAlpha: Float,
        val colorPhaseOffset: Float,
        val sparkle: Boolean,
        val bottomTarget: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = ArrayList<Particle>(PARTICLE_COUNT)
    private val random = Random.Default
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var drawLogged = false
    private var burstCenterX = 0f
    private var burstCenterY = 0f

    fun burst(bounds: RectF, onFinished: () -> Unit) {
        animator?.cancel()
        particles.clear()
        visibility = VISIBLE
        alpha = 1f
        bringToFront()
        requestLayout()
        invalidate()
        if (width == 0 || height == 0 || bounds.isEmpty) {
            Log.e(
                TAG,
                "burst aborted overlay=${width}x$height bounds=$bounds"
            )
            onFinished()
            return
        }

        drawLogged = false
        burstCenterX = bounds.centerX()
        burstCenterY = bounds.centerY()
        repeat(PARTICLE_COUNT) {
            val startY =
                bounds.top + bounds.height() * (0.12f + random.nextFloat() * 0.76f)
            val speedLayer = when {
                random.nextFloat() < 0.24f -> randomBetween(1.18f, 1.38f)
                random.nextFloat() < 0.58f -> randomBetween(0.92f, 1.10f)
                else -> randomBetween(0.68f, 0.88f)
            }
            val velocityY = if (random.nextFloat() < 0.28f) {
                randomBetween(-900f, -480f) * speedLayer
            } else {
                randomBetween(-480f, 180f) * speedLayer
            }
            val bottomTarget = height * randomBetween(0.78f, 1.02f)
            val requiredGravity =
                2f * (bottomTarget - startY - velocityY * DURATION_SECONDS) /
                    (DURATION_SECONDS * DURATION_SECONDS)
            particles += Particle(
                startX = bounds.left + bounds.width() * (0.08f + random.nextFloat() * 0.84f),
                startY = startY,
                velocityX = randomBetween(-650f, 650f) * speedLayer,
                velocityY = velocityY,
                gravity = max(randomBetween(1700f, 2600f), requiredGravity)
                    .coerceAtMost(3400f),
                damping = randomBetween(0.988f, 0.996f),
                radius = randomRadius(),
                baseAlpha = randomBetween(0.78f, 1f),
                colorPhaseOffset = randomBetween(-0.025f, 0.025f),
                sparkle = random.nextFloat() < 0.10f,
                bottomTarget = bottomTarget
            )
        }

        Log.d(
            TAG,
            "burst start overlay=${width}x$height bounds=$bounds " +
                "center=(${bounds.centerX()},${bounds.centerY()}) particles=${particles.size}"
        )
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BURST_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    Log.d(TAG, "animation end cancelled=$cancelled")
                    visibility = GONE
                    particles.clear()
                    if (!cancelled) onFinished()
                }
            })
            start()
        }
    }

    fun cancelBurst() {
        animator?.cancel()
        animator = null
        particles.clear()
        visibility = GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!drawLogged && particles.isNotEmpty()) {
            drawLogged = true
            Log.d(
                TAG,
                "onDraw called overlay=${width}x$height particles=${particles.size}"
            )
        }
        val elapsedSeconds = progress * BURST_DURATION_MS / 1000f
        val bottomFadeStart = height * 0.75f
        val bottomFadeEnd = height.toFloat()
        drawBurstFlash(canvas)

        particles.forEach { particle ->
            // Keep horizontal scatter lively while gravity dominates the later downward motion.
            val velocityDecay = particle.damping.pow(elapsedSeconds * 60f)
            val dampedTravel = elapsedSeconds * (1f + velocityDecay) * 0.5f
            val x = particle.startX + particle.velocityX * dampedTravel
            val rawY = particle.startY +
                particle.velocityY * elapsedSeconds +
                0.5f * particle.gravity * elapsedSeconds * elapsedSeconds
            val y = min(rawY, particle.bottomTarget)

            val timeAlpha = when {
                progress <= 0.60f -> 1f - progress * (0.20f / 0.60f)
                progress <= 0.85f -> 0.80f -
                    (progress - 0.60f) * (0.35f / 0.25f)
                else -> 0.45f * (1f - (progress - 0.85f) / 0.15f)
            }
            val bottomAlpha = when {
                y <= bottomFadeStart -> 1f
                y >= bottomFadeEnd -> 0f
                else -> 1f - (y - bottomFadeStart) /
                    (bottomFadeEnd - bottomFadeStart)
            }
            val alpha = (
                particle.baseAlpha *
                    min(timeAlpha.coerceIn(0f, 1f), bottomAlpha.coerceIn(0f, 1f)) *
                    255f
                ).toInt().coerceIn(0, 255)
            val colorProgress = (progress + particle.colorPhaseOffset).coerceIn(0f, 1f)
            val color = particleColor(colorProgress)
            val currentVelocityX = particle.velocityX * velocityDecay
            val currentVelocityY = particle.velocityY + particle.gravity * elapsedSeconds
            val speed = sqrt(
                currentVelocityX * currentVelocityX +
                    currentVelocityY * currentVelocityY
            ).coerceAtLeast(1f)
            val tailLength = min(dp(12f), speed * 0.012f)

            // A short low-alpha trail adds speed without changing the circular particle shape.
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = max(dp(0.75f), particle.radius * 0.72f)
            paint.color = color
            paint.alpha = (alpha * 0.30f).toInt().coerceIn(0, 255)
            canvas.drawLine(
                x - currentVelocityX / speed * tailLength,
                y - currentVelocityY / speed * tailLength,
                x,
                y,
                paint
            )

            paint.style = Paint.Style.FILL
            paint.color = color
            paint.alpha = if (particle.sparkle) {
                min(255, (alpha * 1.12f).toInt())
            } else {
                alpha
            }
            canvas.drawCircle(x, y, particle.radius, paint)
        }
    }

    override fun onDetachedFromWindow() {
        cancelBurst()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun randomRadius(): Float {
        val value = random.nextFloat()
        val diameterDp = when {
            value < 0.60f -> randomBetween(1.5f, 3.5f)
            value < 0.90f -> randomBetween(3.5f, 5.5f)
            else -> randomBetween(5.5f, 7f)
        }
        return dp(diameterDp) / 2f
    }

    private fun drawBurstFlash(canvas: Canvas) {
        paint.strokeCap = Paint.Cap.ROUND
        val flashProgress = (progress / FLASH_FRACTION).coerceIn(0f, 1f)
        if (flashProgress < 1f) {
            paint.style = Paint.Style.FILL
            paint.color = FLASH_BLUE
            paint.alpha = ((1f - flashProgress) * 92f).toInt()
            canvas.drawCircle(
                burstCenterX,
                burstCenterY,
                dp(9f + 45f * flashProgress),
                paint
            )
        }

        val waveProgress = (progress / SHOCKWAVE_FRACTION).coerceIn(0f, 1f)
        if (waveProgress < 1f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.5f + 1.5f * (1f - waveProgress))
            paint.color = lerpColor(SHOCKWAVE_BLUE, SHOCKWAVE_PURPLE, waveProgress)
            paint.alpha = ((1f - waveProgress) * 145f).toInt()
            canvas.drawCircle(
                burstCenterX,
                burstCenterY,
                dp(14f + 78f * waveProgress),
                paint
            )
        }
        paint.style = Paint.Style.FILL
    }

    private fun particleColor(value: Float): Int = when {
        value < 0.35f -> lerpColor(
            PARTICLE_BLUE,
            PARTICLE_PURPLE,
            value / 0.35f
        )
        value < 0.70f -> lerpColor(
            PARTICLE_PURPLE,
            PARTICLE_ORANGE,
            (value - 0.35f) / 0.35f
        )
        else -> lerpColor(
            PARTICLE_ORANGE_RED,
            PARTICLE_RED,
            (value - 0.70f) / 0.30f
        )
    }

    private fun lerpColor(start: Int, end: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(start) + (Color.red(end) - Color.red(start)) * t).toInt(),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * t).toInt(),
            (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t).toInt()
        )
    }

    private fun randomBetween(min: Float, max: Float): Float =
        min + random.nextFloat() * (max - min)

    private companion object {
        const val PARTICLE_COUNT = 300
        const val BURST_DURATION_MS = 1_700L
        const val DURATION_SECONDS = 1.7f
        const val FLASH_FRACTION = 120f / 1700f
        const val SHOCKWAVE_FRACTION = 250f / 1700f
        const val TAG = "MusicDeleteParticles"
        val PARTICLE_BLUE = Color.rgb(127, 175, 232)
        val PARTICLE_PURPLE = Color.rgb(185, 167, 255)
        val PARTICLE_ORANGE = Color.rgb(255, 154, 102)
        val PARTICLE_ORANGE_RED = Color.rgb(255, 107, 95)
        val PARTICLE_RED = Color.rgb(201, 74, 85)
        val FLASH_BLUE = Color.rgb(184, 215, 255)
        val SHOCKWAVE_BLUE = Color.rgb(184, 215, 255)
        val SHOCKWAVE_PURPLE = Color.rgb(201, 188, 255)
    }
}
