package com.pulseisland.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * A lightweight custom View that renders an animated waveform (3 bouncing bars).
 * Animation only runs when [startAnimation] is called and completely stops on [stopAnimation].
 * Zero CPU overhead when not animating.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F0FF")
        strokeCap = Paint.Cap.ROUND
    }

    // 3 bars with independent heights (0.0 = min, 1.0 = max)
    private val barHeights = floatArrayOf(0.4f, 0.7f, 0.4f)
    private val animators = mutableListOf<ValueAnimator>()

    // Static heights for paused state
    private val pausedHeights = floatArrayOf(0.25f, 0.25f, 0.25f)
    private var isAnimating = false

    fun startAnimation() {
        if (isAnimating) return
        isAnimating = true
        animators.clear()

        // Each bar gets a slightly different speed to look organic
        val speeds = longArrayOf(420L, 300L, 510L)
        for (i in 0..2) {
            val animator = ValueAnimator.ofFloat(0.25f, 1f).apply {
                duration = speeds[i]
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator()
                startDelay = (i * 80L)
                addUpdateListener {
                    barHeights[i] = it.animatedValue as Float
                    invalidate()
                }
            }
            animators.add(animator)
            animator.start()
        }
    }

    fun stopAnimation() {
        isAnimating = false
        animators.forEach { it.cancel() }
        animators.clear()
        // Set to static paused state
        for (i in 0..2) barHeights[i] = pausedHeights[i]
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val barCount = 3
        val barWidth = w * 0.18f
        val gap = (w - barCount * barWidth) / (barCount + 1)
        paint.strokeWidth = barWidth

        for (i in 0 until barCount) {
            val cx = gap + i * (barWidth + gap) + barWidth / 2f
            val barH = barHeights[i] * h * 0.85f
            val top = (h - barH) / 2f
            val bottom = top + barH
            canvas.drawLine(cx, top, cx, bottom, paint)
        }
    }
}
