package com.rpsonline.app.platform

/** Whether the app process is in the foreground; used by background services. */
object AppForegroundTracker {
    @Volatile
    var isInForeground: Boolean = true
        private set

    fun setInForeground(inForeground: Boolean) {
        isInForeground = inForeground
    }
}
