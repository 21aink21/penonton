package com.penonton.util

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.View

object GradientBackground {

    const val COLOR_BASE = 0xFF000000.toInt()

    fun createDrawable(): ColorDrawable {
        return ColorDrawable(COLOR_BASE)
    }

    fun apply(activity: Activity) {
        val bg = createDrawable()
        activity.window.decorView.background = bg
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    fun apply(view: View) {
        view.background = createDrawable()
    }
}
