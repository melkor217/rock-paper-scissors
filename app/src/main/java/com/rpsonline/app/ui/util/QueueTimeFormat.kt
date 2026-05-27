package com.rpsonline.app.ui.util

fun formatQueueTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}
