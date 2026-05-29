package com.rpsonline.app.ui.util

fun formatQueueTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

/** Accessibility label for segmented queue timer (MM:SS, up to 99:59). */
fun formatQueueTimeMmSs(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val minutes = (total / 60).coerceAtMost(99)
    val secs = total % 60
    return "%02d:%02d".format(minutes, secs)
}
