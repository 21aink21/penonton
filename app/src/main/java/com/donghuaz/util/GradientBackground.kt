package com.donghuaz.util

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View

object GradientBackground {

    // Diagonal from Top-Left Obsidian to Bottom-Right Crimson
    // Colors: 0xFF09060F, 0xFF1E0514, 0xFF380317, 0xFF5A021E, 0xFF7A0225, 0xFFC50337
    private val COLORS = intArrayOf(
        0xFF09060F.toInt(),
        0xFF1E0514.toInt(),
        0xFF380317.toInt(),
        0xFF5A021E.toInt(),
        0xFF7A0225.toInt(),
        0xFFC50337.toInt()
    )

    fun createDrawable(): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, COLORS).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }

    fun apply(activity: Activity) {
        val gradient = createDrawable()
        activity.window.decorView.background = gradient
        activity.window.statusBarColor = 0xFF09060F.toInt()
        activity.window.navigationBarColor = 0xFF1E0514.toInt()
    }

    fun apply(view: View) {
        view.background = createDrawable()
    }
}
