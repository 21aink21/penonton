package com.donghuaz.util

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View

object GradientBackground {

    // Diagonal from Top-Left to Bottom-Right (#121212 ➔ #1A1A1A)
    private val COLORS = intArrayOf(
        0xFF121212.toInt(),
        0xFF141414.toInt(),
        0xFF161616.toInt(),
        0xFF181818.toInt(),
        0xFF1A1A1A.toInt()
    )

    fun createDrawable(): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, COLORS).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }

    fun apply(activity: Activity) {
        val gradient = createDrawable()
        activity.window.decorView.background = gradient
        activity.window.statusBarColor = 0xFF121212.toInt()
        activity.window.navigationBarColor = 0xFF1A1A1A.toInt()
    }

    fun apply(view: View) {
        view.background = createDrawable()
    }
}
