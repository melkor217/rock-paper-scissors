package com.rpsonline.app.ui.util

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Hide status and navigation bars; swipe from edge to show them briefly. */
fun Activity.applyImmersiveFullscreen() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun Activity.enableImmersiveFullscreen() {
    applyImmersiveFullscreen()
}
