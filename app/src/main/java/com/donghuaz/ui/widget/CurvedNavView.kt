package com.donghuaz.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator

class CurvedNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val strokePath = Path()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#731A1A1A")
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FC6F01")
        style = Paint.Style.FILL
        setShadowLayer(6f, 0f, 0f, Color.parseColor("#FC6F01"))
    }

    var activeCenterX = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val curveRadius = 42f
    private val curveDepth = 16f

    fun animateTo(targetX: Float, duration: Long = 350) {
        ValueAnimator.ofFloat(activeCenterX, targetX).apply {
            this.duration = duration
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener {
                activeCenterX = it.animatedValue as Float
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        path.reset()
        strokePath.reset()

        val cx = if (activeCenterX > 0f) activeCenterX else w / 8f

        val startX = (cx - curveRadius * 1.5f).coerceAtLeast(0f)
        val endX = (cx + curveRadius * 1.5f).coerceAtMost(w)

        path.moveTo(0f, 0f)
        path.lineTo(startX, 0f)
        path.cubicTo(
            cx - curveRadius, 0f,
            cx - curveRadius * 0.7f, curveDepth,
            cx, curveDepth
        )
        path.cubicTo(
            cx + curveRadius * 0.7f, curveDepth,
            cx + curveRadius, 0f,
            endX, 0f
        )
        path.lineTo(w, 0f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()

        canvas.drawPath(path, paint)

        strokePath.moveTo(0f, 0f)
        strokePath.lineTo(startX, 0f)
        strokePath.cubicTo(
            cx - curveRadius, 0f,
            cx - curveRadius * 0.7f, curveDepth,
            cx, curveDepth
        )
        strokePath.cubicTo(
            cx + curveRadius * 0.7f, curveDepth,
            cx + curveRadius, 0f,
            endX, 0f
        )
        strokePath.lineTo(w, 0f)

        canvas.drawPath(strokePath, strokePaint)

        // Draw glowing accent pip at notch center
        canvas.drawCircle(cx, curveDepth / 2f + 2f, 4f, pipPaint)
    }
}
