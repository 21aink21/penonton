package com.lk21official.ui.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class FadingImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.onDraw(canvas)
            return
        }

        val count = canvas.saveLayer(0f, 0f, w, h, null)
        super.onDraw(canvas)

        val shader = LinearGradient(
            0f, h * 0.40f,
            0f, h,
            intArrayOf(Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        maskPaint.shader = shader
        canvas.drawRect(0f, 0f, w, h, maskPaint)
        canvas.restoreToCount(count)
    }
}
